package com.caipan.music.online

import java.io.IOException

data class LxProviderDescriptor(
    val id: String,
    val name: String,
    val actions: List<String> = emptyList(),
    val qualities: List<String> = emptyList()
)

/**
 * Capability a music data source can declare. Unlike MeloX's full capability
 * matrix, Muse only needs the three operations its online surface actually
 * exercises today. [Search] and [Lyrics] are the shared baseline every built-in
 * [OnlineCatalog] exposes; [Playback] is the optional "official direct-link"
 * resolution that only some sources implement (the rest fall back to LX 音源).
 */
enum class MusicCapability { Search, Playback, Lyrics }

/**
 * Optional capability: resolve a platform's own direct playback URL. Sources that
 * cannot resolve a URL themselves simply omit this interface and let playback
 * fall through to the user-installed LX source plugins.
 */
interface PlaybackCapability {
    suspend fun resolvePlayback(track: OnlineTrack): Result<LxResolvedMusicUrl>
}

/** A built-in online search catalog backed by a platform's public search API. */
interface OnlineCatalog {
    val sourceId: String
    val displayName: String

    /** Declared capabilities; the UI asks [supports] instead of probing methods. */
    val capabilities: Set<MusicCapability>

    fun supports(capability: MusicCapability): Boolean = capability in capabilities

    suspend fun search(query: String, limit: Int = 30, offset: Int = 0): Result<List<OnlineTrack>>

    /** Resolves LRC lyrics for a track from this catalog's platform, or null when absent. */
    suspend fun resolveLyrics(track: OnlineTrack): Result<String?> =
        Result.failure(IOException("内置源不支持歌词"))
}

/** Metadata for a user-imported LX source script. The script body is stored separately. */
data class LxSourceDescriptor(
    val id: String,
    val name: String,
    val version: String = "unknown",
    val author: String = "",
    val description: String = "",
    val originalSource: String,
    val sha256: String,
    val enabled: Boolean = false,
    val importedAtEpochMs: Long = System.currentTimeMillis(),
    val scriptSizeBytes: Long = 0L,
    val providers: List<LxProviderDescriptor> = emptyList()
)

data class LxResolvedMusicUrl(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val quality: String? = null,
    val expiresAtEpochMs: Long? = null
)

/** Result of an online search together with the source that produced it. */
data class OnlineSearchResult(
    val sourceLabel: String,
    val tracks: List<OnlineTrack>
)
