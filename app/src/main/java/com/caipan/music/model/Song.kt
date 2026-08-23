package com.caipan.music.model

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore.Audio.Media
import org.json.JSONObject

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
    val sampleRate: Int = 0,
    val remoteUri: String? = null,
    val artworkUrl: String? = null,
    val onlineSource: String? = null,
    val onlineSongId: String? = null,
    val onlineData: String? = null
) {
    val isOnline: Boolean get() = !onlineSource.isNullOrBlank() && !onlineSongId.isNullOrBlank()

    val uri: Uri get() = remoteUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        ?: ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, id)

    val formattedDuration: String get() {
        val totalSec = durationMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    val formatLabel: String get() = fileName.substringAfterLast('.', "")
        .ifBlank { mimeType.substringAfterLast('/', if (isOnline) "在线" else "未知") }.uppercase()

    val albumArtUri: Uri? get() {
        artworkUrl?.takeIf { it.isNotBlank() }?.let { return Uri.parse(it) }
        if (albumId <= 0) return null
        return Uri.withAppendedPath(
            Uri.parse("content://media/external/audio/albumart"),
            albumId.toString()
        )
    }

    fun toPlaylistPayload(): String = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        put("album", album)
        put("durationMs", durationMs)
        artworkUrl?.let { put("artworkUrl", it) }
        onlineSource?.let { put("onlineSource", it) }
        onlineSongId?.let { put("onlineSongId", it) }
        onlineData?.let { put("onlineData", it) }
    }.toString()

    companion object {
        val PROJECTION = arrayOf(
            Media._ID, Media.TITLE, Media.ARTIST, Media.ALBUM,
            "duration", Media.ALBUM_ID
        )
        fun fromPlaylistPayload(json: String): Song? = runCatching {
            val obj = JSONObject(json)
            Song(
                id = obj.getLong("id"),
                title = obj.getString("title"),
                artist = obj.optString("artist"),
                album = obj.optString("album"),
                durationMs = obj.optLong("durationMs"),
                albumId = 0L,
                folderPath = "在线音乐",
                mimeType = "audio/*",
                artworkUrl = if (obj.has("artworkUrl")) obj.optString("artworkUrl") else null,
                onlineSource = if (obj.has("onlineSource")) obj.optString("onlineSource") else null,
                onlineSongId = if (obj.has("onlineSongId")) obj.optString("onlineSongId") else null,
                onlineData = if (obj.has("onlineData")) obj.optString("onlineData") else null
            )
        }.getOrNull()

        const val SORT_ORDER = "title ASC"
    }
}
