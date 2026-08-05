package com.dd3boh.outertune.lyrics

import android.content.Context
import android.os.ParcelFileDescriptor
import com.kyant.taglib.TagLib
import org.akanework.gramophone.logic.utils.LrcUtils
import org.akanework.gramophone.logic.utils.LrcUtils.loadAndParseLyricsFile
import org.akanework.gramophone.logic.utils.SemanticLyrics
import java.io.File


object LocalLyricsProvider : LyricsProvider {
    override val name = "Local LRC"
    override fun isEnabled(context: Context) = true

    /**
     * This function is "hot-wired" to adapted to the
     * interface design. As a result, title is actually the file path.
     * The lrc file is assumed to be in the same directory as the song.
     * All the other fields serve no purpose.
     *
     * @param title file path of the song, NOT the song title
     */
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> {
        throw NotImplementedError()
    }

    fun getLyricsNew(
        path: String,
        parserOptions: LrcUtils.LrcParserOptions
    ): SemanticLyrics? {
        // TODO: audiomimetype
        return loadAndParseLyricsFile(File(path), null, parserOptions)
    }

    fun getEmbeddedLyrics(
        path: String,
        parserOptions: LrcUtils.LrcParserOptions
    ): SemanticLyrics? = runCatching {
        val file = File(path)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val lyrics = TagLib.getMetadata(fd.dup().detachFd(), readPictures = false)
                ?.propertyMap
                ?.asSequence()
                ?.filter { (key, _) -> key == "LYRICS" || key == "UNSYNCEDLYRICS" || key.startsWith("LYRICS:") }
                ?.flatMap { (_, values) -> values.asSequence() }
                ?.firstOrNull { it.isNotBlank() }
                ?: return@use null
            LrcUtils.parseLyrics(lyrics, null, parserOptions, null)
        }
    }.getOrNull()

}
