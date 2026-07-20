package com.caipan.music.player

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LyricLine(val timeMs: Long, val text: String)

object LyricsManager {

    /**
     * Load lyrics for a song. Looks for a .lrc file next to the audio file.
     * Returns a list of timed lyric lines, or empty if none found.
     */
    suspend fun loadLyrics(context: Context, songId: Long): List<LyricLine> = withContext(Dispatchers.IO) {
        val audioPath = queryAudioPath(context, songId) ?: return@withContext emptyList()
        val audioFile = File(audioPath)
        if (!audioFile.exists()) return@withContext emptyList()

        // Look for same-name .lrc beside the audio file
        val base = audioFile.absolutePath.substringBeforeLast('.')
        val candidates = listOf(File(base + ".lrc"), File(base + ".LRC"))
        val lrcFile = candidates.firstOrNull { it.exists() } ?: return@withContext emptyList()

        try {
            parseLrc(lrcFile.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun queryAudioPath(context: Context, songId: Long): String? {
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val selection = MediaStore.Audio.Media._ID + " = ?"
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, arrayOf(songId.toString()), null
        )?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return null
    }

    /**
     * Parse standard LRC format:
     * [mm:ss.xx] line text
     * Supports multiple timestamps per line.
     */
    fun parseLrc(content: String): List<LyricLine> {
        val timeRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
        val result = mutableListOf<LyricLine>()
        content.lineSequence().forEach { rawLine ->
            val matches = timeRegex.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach
            val text = rawLine.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) return@forEach
            matches.forEach { m ->
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = m.groupValues[3]
                val frac = when (fracStr.length) {
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10
                    3 -> fracStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val timeMs = min * 60_000 + sec * 1_000 + frac
                result.add(LyricLine(timeMs, text))
            }
        }
        return result.sortedBy { it.timeMs }
    }

    /** Index of the currently active lyric line for a given playback position. */
    fun currentLineIndex(lyrics: List<LyricLine>, positionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        var idx = -1
        for (i in lyrics.indices) {
            if (lyrics[i].timeMs <= positionMs) idx = i else break
        }
        return idx
    }
}
