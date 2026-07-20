package com.caipan.music.data

import android.content.Context
import android.provider.MediaStore
import com.caipan.music.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    suspend fun loadAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = """
            duration > 30000 AND _data NOT LIKE '%/Recordings/%'
            AND _data NOT LIKE '%/录音/%'
            AND _data NOT LIKE '%/Voice Recorder/%'
            AND _data NOT LIKE '%/WhatsApp Voice Notes/%'
            AND _data NOT LIKE '%/recording/%'
        """.trimIndent().replace('\n', ' ')

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            "duration",
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.BITRATE
        )
        val cursor = context.contentResolver.query(
            uri, projection, selection, null, "title ASC"
        )
        cursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durIdx = c.getColumnIndex("duration")
            val albumIdIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val pathIdx = c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            val nameIdx = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataIdx = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            val mimeIdx = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val sizeIdx = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val bitrateIdx = c.getColumnIndex(MediaStore.Audio.Media.BITRATE)

            while (c.moveToNext()) {
                if (durIdx < 0) continue
                val dur = c.getLong(durIdx)
                if (dur <= 30000) continue
                songs.add(Song(
                    id = c.getLong(idIdx),
                    title = c.getString(titleIdx) ?: "Unknown",
                    artist = c.getString(artistIdx) ?: "Unknown",
                    album = c.getString(albumIdx) ?: "Unknown",
                    durationMs = dur,
                    albumId = c.getLong(albumIdIdx),
                    folderPath = folderName(if (pathIdx >= 0) c.getString(pathIdx) else null),
                    fileName = if (nameIdx >= 0) c.getString(nameIdx).orEmpty() else "",
                    filePath = if (dataIdx >= 0) c.getString(dataIdx).orEmpty() else "",
                    mimeType = if (mimeIdx >= 0) c.getString(mimeIdx).orEmpty() else "",
                    sizeBytes = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0,
                    bitrate = if (bitrateIdx >= 0) c.getInt(bitrateIdx) else 0,
                    sampleRate = 0
                ))
            }
        }
        songs
    }

    suspend fun getSongsByIds(ids: List<Long>): List<Song> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val songs = mutableListOf<Song>()
        val selection = MediaStore.Audio.Media._ID + " IN (" + ids.joinToString(",") + ")"
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            "duration",
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.BITRATE
        )
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null
        )
        cursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durIdx = c.getColumnIndex("duration")
            val albumIdIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val pathIdx = c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            val nameIdx = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataIdx = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            val mimeIdx = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val sizeIdx = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val bitrateIdx = c.getColumnIndex(MediaStore.Audio.Media.BITRATE)
            while (c.moveToNext()) {
                songs.add(Song(
                    id = c.getLong(idIdx),
                    title = c.getString(titleIdx) ?: "Unknown",
                    artist = c.getString(artistIdx) ?: "Unknown",
                    album = c.getString(albumIdx) ?: "Unknown",
                    durationMs = if (durIdx >= 0) c.getLong(durIdx) else 0L,
                    albumId = c.getLong(albumIdIdx),
                    folderPath = folderName(if (pathIdx >= 0) c.getString(pathIdx) else null),
                    fileName = if (nameIdx >= 0) c.getString(nameIdx).orEmpty() else "",
                    filePath = if (dataIdx >= 0) c.getString(dataIdx).orEmpty() else "",
                    mimeType = if (mimeIdx >= 0) c.getString(mimeIdx).orEmpty() else "",
                    sizeBytes = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0,
                    bitrate = if (bitrateIdx >= 0) c.getInt(bitrateIdx) else 0,
                    sampleRate = 0
                ))
            }
        }
        songs
    }

    private fun folderName(relativePath: String?): String {
        val path = relativePath?.trim('/')?.takeIf { it.isNotBlank() } ?: return "根目录"
        return path.substringAfterLast('/').ifBlank { "根目录" }
    }
}
