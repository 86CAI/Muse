package com.caipan.music.model

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore.Audio.Media

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumId: Long,
    val folderPath: String = "未知文件夹",
    val fileName: String = "",
    val filePath: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0,
    val bitrate: Int = 0,
    val sampleRate: Int = 0
) {
    val uri: Uri get() = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, id)

    val formattedDuration: String get() {
        val totalSec = durationMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    val formatLabel: String get() = fileName.substringAfterLast('.', "")
        .ifBlank { mimeType.substringAfterLast('/', "未知") }.uppercase()

    val albumArtUri: Uri? get() {
        if (albumId <= 0) return null
        return Uri.withAppendedPath(
            Uri.parse("content://media/external/audio/albumart"),
            albumId.toString()
        )
    }

    companion object {
        val PROJECTION = arrayOf(
            Media._ID, Media.TITLE, Media.ARTIST, Media.ALBUM,
            "duration", Media.ALBUM_ID
        )
        const val SORT_ORDER = "title ASC"
    }
}
