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
import com.dd3boh.outertune.playback.downloadManager.DownloadHttpException
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** 直接将洛雪解析后的音频写入用户选择的下载目录，不使用 Media3 下载缓存。 */
@Singleton
class DownloadUtil @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
    private val metadataWriter: DownloadedMetadataWriter,
) {
    private val tag = DownloadUtil::class.simpleName.orEmpty()
    val downloads = MutableStateFlow<Map<String, LocalDateTime>>(emptyMap())
    var isProcessingDownloads = MutableStateFlow(false)
    private val pendingDownloadRequests = ConcurrentHashMap<String, PendingDownloadRequest>()

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
            val now = LocalDateTime.now()
            val discovered = mutableMapOf<String, LocalDateTime>()
            val availableFiles = localMgr.getAvailableFiles(false)
            val existingBeforeScan = database.downloadedOrQueuedSongs().first()

            // 目录权限暂时失效或目录尚未初始化时不要清空数据库，否则下载列表会瞬间消失。
            if (localMgr.allDirs.isEmpty()) return
            // 扫描结果为空通常意味着 SAF 临时权限/IO 异常；保留已有记录等待下次扫描。
            if (availableFiles.isEmpty() && existingBeforeScan.isNotEmpty()) {
                val activeDownloads = downloads.value.filterValues { it == STATE_DOWNLOADING }
                downloads.value = activeDownloads + existingBeforeScan
                    .filter { it.song.dateDownload != null }
                    .associate { it.song.id to it.song.dateDownload!! }
                return
            }

            availableFiles.forEach { (mediaId, uri) ->
                try {
                    val file = fileFromUri(context, uri)
                        ?: throw InvalidAudioFileException("无法访问下载文件")
                    database.registerDownloadSong(mediaId, now, file.absolutePath)
                    discovered[mediaId] = now
                } catch (error: InvalidAudioFileException) {
                    reportException(error)
                }
            }

            val activeDownloads = downloads.value
                .filterValues { it == STATE_DOWNLOADING }
            val existing = database.downloadedOrQueuedSongs().first()
            database.transaction {
                existing
                    .filter { it.song.dateDownload != null && it.song.id !in discovered && it.song.id !in activeDownloads }
                    .forEach { removeDownloadSong(it.song.id) }
            }
            downloads.value = activeDownloads + discovered
        } finally {
            isProcessingDownloads.value = false
        }
    }

    suspend fun rescanDownloads() {
        if (isProcessingDownloads.value) return
        isProcessingDownloads.value = true
        try {
            val activeDownloads = downloads.value.filterValues { it == STATE_DOWNLOADING }
            val databaseDownloads = database.downloadedOrQueuedSongs().first()
            val foundIds = if (localMgr.allDirs.isEmpty()) {
                emptySet()
            } else {
                localMgr.getAvailableFiles(false).keys
            }
            if (localMgr.allDirs.isNotEmpty()) {
                val missing = databaseDownloads.filter {
                    it.song.dateDownload != null && it.song.id !in foundIds && downloads.value[it.song.id] != STATE_DOWNLOADING
                }
                database.transaction { missing.forEach { removeDownloadSong(it.song.id) } }
            }
            downloads.value = activeDownloads + databaseDownloads
                .filter { it.song.dateDownload != null && (localMgr.allDirs.isEmpty() || it.song.id in foundIds) }
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
        pendingDownloadRequests[song.id] = PendingDownloadRequest(song, downloadQualityFallbacks(quality))
        downloads.update { it + (song.id to STATE_DOWNLOADING) }
        startDownload(song.id)
    }

    private fun startDownload(mediaId: String) {
        val pending = pendingDownloadRequests[mediaId] ?: return
        CoroutineScope(dlCoroutine).launch {
            try {
                database.transaction { insert(pending.song) }
                val quality = pending.currentQuality
                val streamUrl = LxSourceRuntime.resolve(pending.song, qualityOverride = quality)
                downloadMgr.enqueue(
                    mediaId = mediaId,
                    url = streamUrl,
                    displayName = pending.song.title,
                    fileExtension = downloadFileExtension(quality),
                )
            } catch (error: Throwable) {
                reportException(error)
                if (!retryDownload(mediaId, error)) markDownloadFailed(mediaId)
            }
        }
    }

    private fun deleteSong(id: String): Boolean {
        val wasPending = downloads.value[id] == STATE_DOWNLOADING
        pendingDownloadRequests.remove(id)
        downloadMgr.cancel(id)
        val deleted = localMgr.deleteFile(id)
        downloads.update { it - id }
        database.transaction { removeDownloadSong(id) }
        return deleted || wasPending
    }

    private suspend fun handleDownloadSuccess(mediaId: String, uri: android.net.Uri) {
        val downloadedAt = LocalDateTime.now()
        val file = fileFromUri(context, uri)
        val song = pendingDownloadRequests[mediaId]?.song
            ?: database.song(mediaId).first()?.toMediaMetadata()
        if (file != null && song != null) {
            runCatching { metadataWriter.write(file, song) }
                .onFailure { reportException(it) }
        }
        pendingDownloadRequests.remove(mediaId)
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
        pendingDownloadRequests.remove(mediaId)
        downloads.update { it - mediaId }
        database.transaction { updateDownloadStatus(mediaId, null) }
    }

    private fun retryDownload(mediaId: String, error: Throwable): Boolean {
        if (error !is DownloadHttpException || error.responseCode != HTTP_NOT_FOUND) return false
        val pending = pendingDownloadRequests[mediaId] ?: return false
        if (!pending.moveToNextQuality()) return false

        Log.w(tag, "下载地址返回 404，改用 ${pending.currentQuality} 重试：$mediaId")
        startDownload(mediaId)
        return true
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
                    is DownloadEvent.Failure -> {
                        if (!retryDownload(event.mediaId, event.error)) markDownloadFailed(event.mediaId)
                    }
                }
            }
        }
        CoroutineScope(dlCoroutine).launch { rescanDownloads() }
    }

    companion object {
        val STATE_DOWNLOADING: LocalDateTime = Instant.ofEpochMilli(1).atZone(ZoneOffset.UTC).toLocalDateTime()
        private const val HTTP_NOT_FOUND = 404
    }
}

private const val DEFAULT_DOWNLOAD_QUALITY = "320k"

private class PendingDownloadRequest(
    val song: MediaMetadata,
    private val qualities: List<String>,
) {
    private var qualityIndex = 0

    val currentQuality: String
        get() = qualities[qualityIndex]

    @Synchronized
    fun moveToNextQuality(): Boolean {
        if (qualityIndex >= qualities.lastIndex) return false
        qualityIndex++
        return true
    }
}

private fun downloadQualityFallbacks(quality: String): List<String> = when (quality) {
    "flac24bit" -> listOf("flac24bit", "flac", "320k", "128k")
    "flac" -> listOf("flac", "320k", "128k")
    "320k" -> listOf("320k", "128k")
    else -> listOf("128k")
}

private fun downloadFileExtension(quality: String): String =
    if (quality == "flac" || quality == "flac24bit") ".flac" else ".mp3"
