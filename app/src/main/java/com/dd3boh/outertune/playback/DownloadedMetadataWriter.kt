package com.dd3boh.outertune.playback

import android.os.ParcelFileDescriptor
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.LyricsEntity
import com.dd3boh.outertune.lyrics.LyricsHelper
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.source.lx.LxMusicInfo
import com.dd3boh.outertune.source.lx.LxSearchClient
import com.dd3boh.outertune.source.lx.lxJson
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 将下载歌曲的封面和歌词写入音频文件，失败时不影响已下载文件。 */
@Singleton
class DownloadedMetadataWriter @Inject constructor(
    private val database: MusicDatabase,
    private val lyricsHelper: LyricsHelper,
) {
    private val httpClient = OkHttpClient()

    suspend fun write(file: File, song: MediaMetadata) = withContext(Dispatchers.IO) {
        val cover = fetchCover(song)
        val lyrics = fetchLyrics(song)

        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
                val propertyMap = TagLib.getMetadata(fd.dup().detachFd(), readPictures = false)?.propertyMap
                    ?: HashMap()
                propertyMap["TITLE"] = arrayOf(song.title)
                song.artists.joinToString(" / ")
                    .takeIf(String::isNotBlank)
                    ?.let { propertyMap["ARTIST"] = arrayOf(it) }
                song.album?.title
                    ?.takeIf(String::isNotBlank)
                    ?.let { propertyMap["ALBUM"] = arrayOf(it) }
                lyrics?.let { propertyMap["LYRICS"] = arrayOf(it) }
                runCatching { TagLib.savePropertyMap(fd.dup().detachFd(), propertyMap) }
                    .onFailure { android.util.Log.w(TAG, "保存下载歌曲文本元数据失败", it) }
                cover?.let {
                    runCatching { TagLib.savePictures(fd.dup().detachFd(), arrayOf(it)) }
                        .onFailure { error -> android.util.Log.w(TAG, "保存下载歌曲封面失败", error) }
                }
            }
        }.onFailure { error ->
            android.util.Log.w(TAG, "写入下载歌曲元数据失败: ${file.absolutePath}", error)
        }
    }

    private suspend fun fetchCover(song: MediaMetadata): Picture? {
        val musicInfo = song.sourceData
            ?.let { runCatching { lxJson.decodeFromString<LxMusicInfo>(it) }.getOrNull() }
        val coverUrls = buildList {
            song.thumbnailUrl?.let(::add)
            musicInfo?.let { runCatching { LxSearchClient.loadCover(it) }.getOrNull() }?.let(::add)
        }.distinct()
        for (coverUrl in coverUrls) {
            fetchCover(coverUrl)?.let { return it }
        }
        return null
    }

    private fun fetchCover(coverUrl: String): Picture? {
        val request = Request.Builder()
            .url(coverUrl)
            .header("User-Agent", USER_AGENT)
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body.bytes()
                if (bytes.isEmpty()) return@use null
                Picture(
                    data = bytes,
                    description = "Front Cover",
                    pictureType = "Front Cover",
                    mimeType = response.header("Content-Type")
                        ?.substringBefore(';')
                        ?.takeIf { it.startsWith("image/") }
                        ?: guessImageMimeType(bytes),
                )
            }
        }.getOrNull()
    }

    private suspend fun fetchLyrics(song: MediaMetadata): String? {
        fun String?.validLyrics() = this?.takeUnless {
            it.isBlank() || it == LyricsEntity.LYRICS_NOT_FOUND
        }

        database.lyrics(song.id).first()?.lyrics.validLyrics()?.let { return it }
        val fetched = runCatching { lyricsHelper.getLyrics(song) }.getOrNull()
        if (fetched == null) {
            return database.lyrics(song.id).first()?.lyrics.validLyrics()
        }

        // LyricsHelper 异步写入远程结果，等待 Room 通知，避免首次下载漏写歌词。
        return withTimeoutOrNull(2_000) {
            database.lyrics(song.id)
                .map { it?.lyrics.validLyrics() }
                .filterNotNull()
                .first()
        }
    }

    private companion object {
        const val TAG = "DownloadedMetadata"
        const val USER_AGENT = "OuterTune/1.0 (Android)"

        fun guessImageMimeType(bytes: ByteArray): String = when {
            bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
            bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() -> "image/jpeg"
            else -> "image/jpeg"
        }
    }
}
