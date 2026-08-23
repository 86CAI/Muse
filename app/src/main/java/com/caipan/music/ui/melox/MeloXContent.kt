/*
 * Muse 数据 → MeloX 首页/发现内容映射
 *
 * 区块结构与文案沿用 NEORUAA/Mei_MeloX_Android（每日推荐/热歌榜/推荐歌单/排行榜），
 * 数据源按 Muse 实际能力适配：本地曲库/歌单 + 网易云在线内容。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import com.caipan.music.data.Playlist
import com.caipan.music.model.Song
import com.caipan.music.online.NeteaseHomeContent
import com.caipan.music.online.OnlineTrack
import com.caipan.music.online.RemotePlaylistSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 在线首页内容（供首页/发现页渲染）。 */
data class MeloXOnlineHomeContent(
    val recommendedPlaylists: List<RemotePlaylistSummary> = emptyList(),
    val newSongs: List<OnlineTrack> = emptyList(),
    val chartPlaylists: List<RemotePlaylistSummary> = emptyList(),
)

internal fun meloXOnlineHomeContent(home: NeteaseHomeContent?): MeloXOnlineHomeContent? {
    if (home == null) return null
    val content = MeloXOnlineHomeContent(
        recommendedPlaylists = home.playlists,
        newSongs = home.newSongs,
        chartPlaylists = home.chartPlaylists,
    )
    return if (content.recommendedPlaylists.isEmpty() && content.newSongs.isEmpty() &&
        content.chartPlaylists.isEmpty()
    ) null else content
}

internal fun meloXHomeGreeting(): String = meloXGetGreeting()

/** 问候区大卡：每日推荐(日期角标)/热歌榜/心动模式，与上游 PAGE_RECOMMEND_DAILY_RECOMMEND 结构一致。 */
internal fun meloXRecommendCards(
    songs: List<Song>,
    repeatCountsBySongId: Map<Long, Int>,
    playlists: List<Playlist>,
    onlineActive: Boolean,
    onlineHome: MeloXOnlineHomeContent?,
    onDailyMix: () -> Unit,
    onHotSongs: () -> Unit,
    onHeartMode: (() -> Unit)?,
    onPlayFromQueue: (List<Song>, Int) -> Unit,
): List<MeloXRecommendCardData> = buildList {
    val dailyCover = onlineHome?.newSongs?.firstOrNull()?.artworkUrl
        ?: songs.firstOrNull()?.albumArtUri
        ?: playlists.firstOrNull()?.coverUri?.let(android.net.Uri::parse)
    add(
        MeloXRecommendCardData(
            key = "daily",
            cover = dailyCover,
            title = if (onlineActive && onlineHome?.recommendedPlaylists.isNullOrEmpty()) "每日推荐"
            else "每日推荐",
            eyebrow = LocalDate.now().format(DateTimeFormatter.ofPattern("MM月dd日")),
            onClick = onDailyMix,
        )
    )
    add(
        MeloXRecommendCardData(
            key = "hot",
            cover = hotSongsQueue(songs, repeatCountsBySongId).firstOrNull()?.albumArtUri
                ?: songs.firstOrNull { it.albumArtUri != null }?.albumArtUri,
            title = "热歌榜",
            eyebrow = "全站热门",
            onClick = onHotSongs,
        )
    )
    if (onHeartMode != null) {
        add(
            MeloXRecommendCardData(
                key = "heart",
                cover = playlists.firstOrNull {
                    it.id == "favorites" || it.name == "我喜欢的音乐"
                }?.coverUri?.let(android.net.Uri::parse)
                    ?: songs.firstOrNull { it.albumArtUri != null }?.albumArtUri,
                title = "心动模式",
                eyebrow = "为你心动",
                onClick = onHeartMode,
            )
        )
    }
}

/** 歌单分区：在线推荐歌单 + 排行榜，本地回退到用户歌单与最近歌曲。 */
internal fun meloXHomeSections(
    songs: List<Song>,
    playlists: List<Playlist>,
    onlineActive: Boolean,
    onlineHome: MeloXOnlineHomeContent?,
    onOpenPlaylistDetail: (Playlist) -> Unit,
    onOpenOnlinePlaylist: (RemotePlaylistSummary) -> Unit,
    onPlayOnlineTracks: (List<OnlineTrack>, OnlineTrack) -> Unit,
    onPlayFromQueue: (List<Song>, Int) -> Unit,
): List<MeloXHomePlaylistSection> = buildList {
    val recommended = onlineHome?.recommendedPlaylists.orEmpty()
    val charts = onlineHome?.chartPlaylists.orEmpty()

    if (onlineActive && recommended.isNotEmpty()) {
        add(
            MeloXHomePlaylistSection(
                key = "recommend",
                title = "推荐歌单",
                playlists = recommended.map { it.toCollectionCard(onOpenOnlinePlaylist) },
            )
        )
    } else if (playlists.isNotEmpty()) {
        add(
            MeloXHomePlaylistSection(
                key = "mine",
                title = "我的歌单",
                playlists = playlists.map { it.toCollectionCard(onOpenPlaylistDetail) },
            )
        )
    }

    if (charts.isNotEmpty()) {
        add(
            MeloXHomePlaylistSection(
                key = "rank",
                title = "排行榜",
                playlists = charts.map { it.toCollectionCard(onOpenOnlinePlaylist) },
            )
        )
    } else if (songs.isNotEmpty() && !onlineActive) {
        // 本地回退：最近添加的歌曲以歌单卡形式展示封面墙
        add(
            MeloXHomePlaylistSection(
                key = "recent",
                title = "最近添加",
                playlists = songs.take(12).map { song ->
                    MeloXCollectionCard(
                        key = "local-song-${song.id}",
                        title = song.title,
                        coverUri = song.albumArtUri,
                        onClick = {
                            val index = songs.indexOfFirst { it.id == song.id }
                            if (index >= 0) onPlayFromQueue(songs, index)
                        },
                    )
                },
            )
        )
    }
}

internal fun meloXHomeTrackSections(
    songs: List<Song>,
    onlineActive: Boolean,
    onlineHome: MeloXOnlineHomeContent?,
    onPlayFromQueue: (List<Song>, Int) -> Unit,
    onPlayOnlineTracks: (List<OnlineTrack>, OnlineTrack) -> Unit,
): List<MeloXHomeTrackSection> {
    val onlineTracks = onlineHome?.newSongs.orEmpty()
    if (onlineActive && onlineTracks.isNotEmpty()) {
        return listOf(
            MeloXHomeTrackSection(
                key = "new-songs",
                title = "为你推荐",
                tracks = onlineTracks.map { track ->
                    MeloXHomeTrack(
                        key = track.stableId,
                        title = track.title,
                        artist = track.artist,
                        artwork = track.artworkUrl,
                        onClick = { onPlayOnlineTracks(onlineTracks, track) },
                    )
                },
            )
        )
    }
    if (!onlineActive && songs.isNotEmpty()) {
        return listOf(
            MeloXHomeTrackSection(
                key = "local-recent",
                title = "最近添加",
                tracks = songs.take(6).mapIndexed { index, song ->
                    MeloXHomeTrack(
                        key = song.id.toString(),
                        title = song.title,
                        artist = song.artist,
                        artwork = song.albumArtUri,
                        onClick = { onPlayFromQueue(songs, index) },
                    )
                },
            )
        )
    }
    return emptyList()
}

/** 热歌榜：本地播放次数排序的队列。 */
internal fun hotSongsQueue(songs: List<Song>, repeatCountsBySongId: Map<Long, Int>): List<Song> =
    songs.filter { (repeatCountsBySongId[it.id] ?: 0) > 0 }
        .sortedByDescending { repeatCountsBySongId[it.id] ?: 0 }

private fun RemotePlaylistSummary.toCollectionCard(
    onClick: (RemotePlaylistSummary) -> Unit,
): MeloXCollectionCard = MeloXCollectionCard(
    key = "remote-playlist-$id",
    title = name,
    coverUrl = coverUrl,
    creatorName = creatorName,
    playCount = playCount,
    description = description,
    onClick = { onClick(this@toCollectionCard) },
)

private fun Playlist.toCollectionCard(
    onClick: (Playlist) -> Unit,
): MeloXCollectionCard = MeloXCollectionCard(
    key = "playlist-$id",
    title = name,
    coverUri = coverUri?.let(android.net.Uri::parse),
    onClick = { onClick(this@toCollectionCard) },
)
