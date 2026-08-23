package com.caipan.music.online

data class RemotePlaylistSummary(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
    val creatorName: String = "",
    /** NetEase account that owns the playlist, when the response exposes it. */
    val creatorUserId: Long? = null,
    val playCount: Long = 0L,
    /** True for a playlist the signed-in account has collected/subscribed to. */
    val subscribed: Boolean = false,
    /** Provider-defined marker; NetEase uses non-zero values for special playlists. */
    val specialType: Int = 0,
    /** Playlist description, with NetEase's recommendation copy as a fallback. */
    val description: String? = null
)

data class RemotePlaylistDetail(
    val summary: RemotePlaylistSummary,
    val tracks: List<OnlineTrack>
)

data class NeteaseHomePodcast(
    val id: Long,
    val name: String,
    val artworkUrl: String?
)

data class NeteaseHomeContent(
    val playlists: List<RemotePlaylistSummary> = emptyList(),
    val newSongs: List<OnlineTrack> = emptyList(),
    val recentlyTrending: List<OnlineTrack> = emptyList(),
    val tailoredSongs: List<OnlineTrack> = emptyList(),
    val chartPlaylists: List<RemotePlaylistSummary> = emptyList(),
    val radarPlaylists: List<RemotePlaylistSummary> = emptyList(),
    val personalPlaylists: List<RemotePlaylistSummary> = emptyList(),
    val regionalSongs: List<OnlineTrack> = emptyList(),
    val roamingSongs: List<OnlineTrack> = emptyList(),
    val similarSongs: List<OnlineTrack> = emptyList(),
    val podcasts: List<NeteaseHomePodcast> = emptyList()
)

data class NeteaseAccount(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String
)

/**
 * Extended profile data returned by NetEase's user-detail endpoint.
 *
 * This deliberately remains separate from [NeteaseAccount]: account
 * verification only needs the stable identity fields, while profile details
 * are refreshed with the online library and can fail independently.
 */
data class NeteaseProfileDetails(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?,
    val backgroundUrl: String?,
    val signature: String?,
    val level: Int,
    val listenSongs: Int,
    val follows: Int,
    val followers: Int,
    val playlistCount: Int
)
