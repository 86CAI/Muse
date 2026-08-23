package com.caipan.music.online

import com.caipan.music.model.Song
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * A catalog result whose playable URL is resolved separately by an online source plugin.
 *
 * [sourceId] is deliberately a String: music providers do not all use numeric identifiers.
 * [metadata] keeps provider-specific lookup values without coupling the shared model to one
 * provider. For NetEase results it contains the LX-compatible `songmid` value.
 */
data class OnlineTrack(
    val source: String,
    val sourceId: String,
    val title: String,
    val artists: List<String>,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val metadata: Map<String, String> = emptyMap()
) {
    val stableId: String get() = "$source:$sourceId"

    val artist: String
        get() = artists.filter(String::isNotBlank).joinToString(" / ").ifBlank { "未知艺术家" }

    val formattedDuration: String
        get() {
            val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
            return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
        }

}

fun OnlineTrack.toSong(): Song = Song(
    id = stableSongId(stableId),
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    albumId = 0L,
    folderPath = "在线音乐",
    mimeType = "audio/*",
    artworkUrl = artworkUrl,
    onlineSource = source,
    onlineSongId = sourceId,
    onlineData = JSONObject().apply {
        metadata.forEach { (key, value) -> put(key, value) }
        put("id", sourceId)
        put("songmid", metadata["songmid"] ?: sourceId)
        put("name", title)
        put("title", title)
        put("artist", artist)
        put("singer", artist)
        put("album", album)
        put("duration", durationMs)
    }.toString()
)

fun Song.toOnlineTrack(): OnlineTrack {
    require(isOnline) { "Song is not an online track" }
    val payload = runCatching { JSONObject(onlineData.orEmpty()) }.getOrElse { JSONObject() }
    val metadata = buildMap {
        payload.keys().forEach { key -> put(key, payload.opt(key)?.toString().orEmpty()) }
    }
    return OnlineTrack(
        source = onlineSource.orEmpty(),
        sourceId = onlineSongId.orEmpty(),
        title = title,
        artists = artist.split(" / ").map(String::trim).filter(String::isNotEmpty),
        album = album,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        metadata = metadata
    )
}

private fun stableSongId(key: String): Long {
    val bytes = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
    val positive = ByteBuffer.wrap(bytes, 0, Long.SIZE_BYTES).long and Long.MAX_VALUE
    return -(positive.coerceAtLeast(1L))
}
