package com.dd3boh.outertune.playback

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.dd3boh.outertune.constants.DownloadExtraPathKey
import com.dd3boh.outertune.constants.DownloadPathKey
import com.dd3boh.outertune.constants.DownloadQualityKey
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.PlaylistSong
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.downloadManager.DownloadDirectoryManagerOt
import com.dd3boh.outertune.playback.downloadManager.DownloadEvent
import com.dd3boh.outertune.playback.downloadManager.DownloadManagerOt
import com.dd3boh.outertune.source.lx.LxSourceRuntime
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.dlCoroutine
import com.dd3boh.outertune.utils.get
import com.dd3boh.outertune.utils.reportException
import com.dd3boh.outertune.utils.scanners.InvalidAudioFileException
import com.dd3boh.outertune.utils.scanners.fileFromUri
import com.dd3boh.outertune.utils.scanners.uriListFromString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/** 直接将洛雪解析后的音频写入用户选择的下载目录，不使用 Media3 下载缓存。 */
@Singleton
class DownloadUtil @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
) {
    private val tag = DownloadUtil::class.simpleName.orEmpty()
    val downloads = MutableStateFlow<Map<String, LocalDateTime>>(emptyMap())
    var isProcessingDownloads = MutableStateFlow(false)

    var localMgr = DownloadDirectoryManagerOt(
        context,
        context.dataStore.get(DownloadPathKey, "").toUri(),
        uriListFromString(context.dataStore.get(DownloadExtraPathKey, "")),
    )
    val downloadMgr = DownloadManagerOt(localMgr)

    fun getDownload(songId: String): Flow<LocalDateTime?> = downloads.map { it[songId] }

    fun download(songs: List<MediaMetadata>) {
        songs.forEach(::download)
    }

    fun download(song: MediaMetadata) {
        enqueueDownload(song)
    }

    fun download(song: SongEntity) {
        CoroutineScope(dlCoroutine).launch {
            database.song(song.id).first()?.toMediaMetadata()?.let(::enqueueDownload)
        }
    }

    /** 直接下载无需恢复 Media3 任务；未完成任务由应用本次进程管理。 */
    fun resumeDownloadsOnStart() = Unit

    fun delete(song: PlaylistSong) = deleteSong(song.song.id)

    fun delete(song: Song) = deleteSong(song.song.id)

    fun delete(song: SongEntity) = deleteSong(song.id)

    fun delete(song: MediaMetadata) = deleteSong(song.id)

    fun delete(songId: String) = deleteSong(songId)

    fun clearDownloads() {
        downloadMgr.cancelAll()
        localMgr.getAvailableFiles(false).keys.forEach(localMgr::deleteFile)
        downloads.value = emptyMap()
        database.transaction {
            removeAllDownloadedSongs()
        }
    }

    fun cd() {
        localMgr.doInit(
            context,
            context.dataStore.get(DownloadPathKey, "").toUri(),
            uriListFromString(context.dataStore.get(DownloadExtraPathKey, "")),
        )
    }

    /** 根据所选目录重新发现离线文件。 */
    suspend fun scanDownloads() {
        if (isProcessingDownloads.value) return
        isProcessingDownloads.value = true
        try {
            database.removeAllDownloadedSongs()
            val now = LocalDateTime.now()
            val discovered = mutableMapOf<String, LocalDateTime>()
            localMgr.getAvailableFiles(false).forEach { (mediaId, uri) ->
                try {
                    val file = fileFromUri(context, uri)
                        ?: throw InvalidAudioFileException("无法访问下载文件")
                    database.registerDownloadSong(mediaId, now, file.absolutePath)
                    discovered[mediaId] = now
                } catch (error: InvalidAudioFileException) {
                    reportException(error)
                }
            }
            downloads.value = discovered
        } finally {
            isProcessingDownloads.value = false
        }
    }

    suspend fun rescanDownloads() {
        if (isProcessingDownloads.value) return
        isProcessingDownloads.value = true
        try {
            val databaseDownloads = database.downloadedOrQueuedSongs().first()
            val missing = localMgr.getMissingFiles(
                databaseDownloads.filter { it.song.dateDownload != null }
            )
            database.transaction {
                missing.forEach { removeDownloadSong(it.song.id) }
            }
            val foundIds = localMgr.getAvailableFiles(false).keys
            downloads.value = databaseDownloads
                .filter { it.song.dateDownload != null && it.song.id in foundIds }
                .associate { it.song.id to it.song.dateDownload!! }
        } finally {
            isProcessingDownloads.value = false
        }
    }

    private fun enqueueDownload(song: MediaMetadata) {
        if (downloads.value.containsKey(song.id)) return
        if (song.source == null) {
            Log.w(tag, "跳过未配置洛雪来源的下载：${song.id}")
            return
        }

        val quality = context.dataStore.get(DownloadQualityKey, DEFAULT_DOWNLOAD_QUALITY)
        downloads.update { it + (song.id to STATE_DOWNLOADING) }
        CoroutineScope(dlCoroutine).launch {
            try {
                database.transaction { insert(song) }
                val streamUrl = LxSourceRuntime.resolve(song, qualityOverride = quality)
                downloadMgr.enqueue(
                    mediaId = song.id,
                    url = streamUrl,
                    displayName = song.title,
                    fileExtension = downloadFileExtension(quality),
                )
            } catch (error: Throwable) {
                reportException(error)
                markDownloadFailed(song.id)
            }
        }
    }

    private fun deleteSong(id: String): Boolean {
        val wasPending = downloads.value[id] == STATE_DOWNLOADING
        downloadMgr.cancel(id)
        val deleted = localMgr.deleteFile(id)
        downloads.update { it - id }
        database.transaction { removeDownloadSong(id) }
        return deleted || wasPending
    }

    private fun handleDownloadSuccess(mediaId: String, uri: android.net.Uri) {
        val downloadedAt = LocalDateTime.now()
        val file = fileFromUri(context, uri)
        database.transaction {
            if (file != null) {
                registerDownloadSong(mediaId, downloadedAt, file.absolutePath)
            } else {
                updateDownloadStatus(mediaId, downloadedAt)
            }
        }
        downloads.update { it + (mediaId to downloadedAt) }
    }

    private fun markDownloadFailed(mediaId: String) {
        downloads.update { it - mediaId }
        database.transaction { updateDownloadStatus(mediaId, null) }
    }

    init {
        CoroutineScope(dlCoroutine).launch {
            downloadMgr.events.collect { event ->
                when (event) {
                    is DownloadEvent.Progress -> {
                        downloads.update { current ->
                            if (current[event.mediaId] == null) current + (event.mediaId to STATE_DOWNLOADING) else current
                        }
                    }
                    is DownloadEvent.Success -> handleDownloadSuccess(event.mediaId, event.file)
                    is DownloadEvent.Failure -> markDownloadFailed(event.mediaId)
                }
            }
        }
        CoroutineScope(dlCoroutine).launch { rescanDownloads() }
    }

    companion object {
        val STATE_DOWNLOADING: LocalDateTime = Instant.ofEpochMilli(1).atZone(ZoneOffset.UTC).toLocalDateTime()
    }
}

private const val DEFAULT_DOWNLOAD_QUALITY = "320k"

private fun downloadFileExtension(quality: String): String =
    if (quality == "flac" || quality == "flac24bit") ".flac" else ".mp3"
