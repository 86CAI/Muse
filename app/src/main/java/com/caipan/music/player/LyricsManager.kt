package com.caipan.music.player

import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LyricWord(val timeMs: Long, val text: String)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val romanization: String? = null
)

object LyricsManager {

    suspend fun loadLyrics(context: Context, songId: Long): List<LyricLine> = withContext(Dispatchers.IO) {
        val audioPath = queryAudioPath(context, songId) ?: return@withContext emptyList()
        val audioFile = File(audioPath)
        if (!audioFile.exists()) return@withContext emptyList()

        val base = audioFile.absolutePath.substringBeforeLast('.')
        val candidates = listOf(File(base + ".lrc"), File(base + ".LRC"))
        val lrcFile = candidates.firstOrNull { it.exists() }

        val lrcContent = lrcFile?.runCatching { readText() }?.getOrNull()
        if (!lrcContent.isNullOrBlank()) {
            val parsed = parseLrc(lrcContent)
            if (parsed.isNotEmpty()) return@withContext parsed
        }

        val embedded = loadEmbeddedLyrics(audioPath)
        if (!embedded.isNullOrBlank()) return@withContext parseLrc(embedded)

        emptyList()
    }

    private fun loadEmbeddedLyrics(audioPath: String): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(audioPath)
            val lyrics = extractEmbeddedLyrics(retriever)
            lyrics?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun extractEmbeddedLyrics(retriever: MediaMetadataRetriever): String? {
        try {
            val lyrics = retriever.extractMetadata(27)
            if (!lyrics.isNullOrBlank()) return lyrics
        } catch (_: Exception) {}
        try {
            val writer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
            if (!writer.isNullOrBlank() && writer.contains("[")) return writer
        } catch (_: Exception) {}
        return null
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

    fun parseLrc(content: String): List<LyricLine> {
        val timeRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
        val wordTimeRegex = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>""")
        val translationRegex = Regex("""\[tr:\s*(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        val romanizationRegex = Regex("""\[romaji:\s*(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        val offsetRegex = Regex("""\[offset:\s*([+-]?\d+)]""")

        val offset = offsetRegex.find(content)?.let { it.groupValues[1].toLongOrNull() ?: 0L } ?: 0L

        val translations = mutableMapOf<Long, String>()
        val romanizations = mutableMapOf<Long, String>()

        val result = mutableListOf<LyricLine>()
        content.lineSequence().forEach { rawLine ->
            val trMatch = translationRegex.find(rawLine)
            val romMatch = romanizationRegex.find(rawLine)
            val matches = timeRegex.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach
            val text = rawLine.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) return@forEach

            val wordTimestamps = wordTimeRegex.findAll(text).toList()
            val words = if (wordTimestamps.isNotEmpty()) {
                // 按时间戳切分文本（支持中文逐字），每个时间戳到下一个时间戳之间是一个字/词
                buildList {
                    wordTimestamps.forEachIndexed { i, m ->
                        val start = m.range.last + 1
                        val end = wordTimestamps.getOrNull(i + 1)?.range?.first ?: text.length
                        val wordText = text.substring(start, end).trim()
                        if (wordText.isNotEmpty()) {
                            add(LyricWord(parseTimeMs(m.groupValues[1], m.groupValues[2], m.groupValues[3]) + offset, wordText))
                        }
                    }
                }
            } else emptyList()

            matches.forEach { m ->
                val timeMs = parseTimeMs(m.groupValues[1], m.groupValues[2], m.groupValues[3]) + offset
                val plainText = text.replace(wordTimeRegex, "").trim()
                val existing = result.firstOrNull { it.timeMs == timeMs }
                if (existing != null) {
                    val idx = result.indexOf(existing)
                    result[idx] = existing.copy(
                        words = existing.words.ifEmpty { words },
                        translation = trMatch?.groupValues?.get(1)?.trim()?.ifBlank { null } ?: existing.translation,
                        romanization = romMatch?.groupValues?.get(1)?.trim()?.ifBlank { null } ?: existing.romanization
                    )
                } else {
                    result.add(LyricLine(
                        timeMs = timeMs,
                        text = plainText,
                        words = words,
                        translation = trMatch?.groupValues?.get(1)?.trim()?.ifBlank { null },
                        romanization = romMatch?.groupValues?.get(1)?.trim()?.ifBlank { null }
                    ))
                }
            }
        }
        return result.sortedBy { it.timeMs }
    }

    private fun parseTimeMs(minStr: String, secStr: String, fracStr: String): Long {
        val min = minStr.toLongOrNull() ?: 0L
        val sec = secStr.toLongOrNull() ?: 0L
        val frac = when (fracStr.length) {
            1 -> (fracStr.toLongOrNull() ?: 0L) * 100
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10
            3 -> fracStr.toLongOrNull() ?: 0L
            else -> 0L
        }
        return min * 60_000 + sec * 1_000 + frac
    }

    fun currentLineIndex(lyrics: List<LyricLine>, positionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        var idx = -1
        for (i in lyrics.indices) {
            if (lyrics[i].timeMs <= positionMs) idx = i else break
        }
        return idx
    }

    fun currentWordIndex(words: List<LyricWord>, positionMs: Long): Int {
        if (words.isEmpty()) return -1
        var idx = -1
        for (i in words.indices) {
            if (words[i].timeMs <= positionMs) idx = i else break
        }
        return idx
    }
}
