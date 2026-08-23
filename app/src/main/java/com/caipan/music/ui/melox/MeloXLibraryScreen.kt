/*
 * MeloX 音乐库
 *
 * Ported from NEORUAA/Mei_MeloX_Android
 * (ui/screen/main/library/LibraryScreen.kt + component/LibraryMobileLayout.kt)：
 * IosPinnedPage 固定标题 + GlassSegmentedControl 分页(歌曲/歌单，按 Muse 能力裁剪) +
 * 播放全部/心动模式 IosGroupedList 行(52/62dp, 54dp 封面圆角 9dp)。数值与上游一致。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.caipan.music.data.Playlist
import com.caipan.music.model.Song
import com.caipan.music.online.OnlineTrack
import com.caipan.music.online.RemotePlaylistSummary
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousRoundedRectangle

enum class MeloXLibraryPage(val title: String) {
    Songs("歌曲"),
    Playlists("歌单"),
    Albums("专辑"),
    Artists("艺人"),
    Folders("文件夹"),
}

@Composable
fun MeloXLibraryScreen(
    songs: List<Song>,
    playlists: List<Playlist>,
    currentSongId: Long?,
    isLoading: Boolean,
    bottomPadding: Dp,
    onPlayFromQueue: (List<Song>, Int) -> Unit,
    onHeartMode: (() -> Unit)?,
    onPlaylistClick: (Playlist) -> Unit,
    onSongMore: (Song) -> Unit,
    onlineTracks: List<OnlineTrack> = emptyList(),
    onlinePlaylistSummaries: List<RemotePlaylistSummary> = emptyList(),
    onPlayOnlineTracks: (List<OnlineTrack>, OnlineTrack) -> Unit = { _, _ -> },
    onOpenOnlinePlaylist: (RemotePlaylistSummary) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val glassColors = LocalGlassColors.current
    val listState = rememberLazyListState()
    var selectedPage by rememberSaveable { mutableStateOf(MeloXLibraryPage.Songs) }
    // Online mode has no local albums/artists/folders, so only expose the
    // pages that can actually be filled for the current source.
    val onlineMode = onlineTracks.isNotEmpty() || onlinePlaylistSummaries.isNotEmpty()
    val pages = if (onlineMode) {
        listOf(MeloXLibraryPage.Songs, MeloXLibraryPage.Playlists)
    } else {
        MeloXLibraryPage.entries.toList()
    }
    if (selectedPage !in pages) selectedPage = MeloXLibraryPage.Songs

    IosPinnedListPage(
        title = "音乐库",
        bottomPadding = bottomPadding,
        listState = listState,
        backgroundColor = if (glassColors.isDark) glassColors.groupedBackground else Color.White,
        modifier = modifier,
    ) {
        item(key = "library-pages") {
            GlassSegmentedControl(
                items = pages.map { it to it.title },
                selected = selectedPage,
                onSelected = { selectedPage = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (selectedPage) {
            MeloXLibraryPage.Songs -> meloXLibrarySongsItems(
                songs = songs,
                currentSongId = currentSongId,
                isLoading = isLoading,
                onPlayFromQueue = onPlayFromQueue,
                onHeartMode = onHeartMode,
                onSongMore = onSongMore,
                onlineTracks = onlineTracks,
                onPlayOnlineTracks = onPlayOnlineTracks,
            )
            MeloXLibraryPage.Playlists -> meloXLibraryPlaylistsItems(
                playlists = playlists,
                onPlaylistClick = onPlaylistClick,
                onlinePlaylistSummaries = onlinePlaylistSummaries,
                onOpenOnlinePlaylist = onOpenOnlinePlaylist,
            )
            MeloXLibraryPage.Albums -> meloXLibraryGroupItems(
                keyPrefix = "album",
                groups = songs.groupBy { it.album.ifBlank { "未知专辑" } },
                emptyText = "暂无专辑",
                emptySymbol = SfSymbol.MusicNote,
                onPlayGroup = { groupSongs -> onPlayFromQueue(groupSongs, 0) },
            )
            MeloXLibraryPage.Artists -> meloXLibraryGroupItems(
                keyPrefix = "artist",
                groups = songs.groupBy { it.artist.ifBlank { "未知艺术家" } },
                emptyText = "暂无艺人",
                emptySymbol = SfSymbol.PersonFilled,
                onPlayGroup = { groupSongs -> onPlayFromQueue(groupSongs, 0) },
            )
            MeloXLibraryPage.Folders -> meloXLibraryGroupItems(
                keyPrefix = "folder",
                groups = songs.groupBy { it.folderPath.ifBlank { "未知文件夹" } },
                emptyText = "暂无文件夹",
                emptySymbol = SfSymbol.MusicNoteList,
                onPlayGroup = { groupSongs -> onPlayFromQueue(groupSongs, 0) },
            )
        }
    }
}

/** 本地专辑 / 艺人 / 文件夹分组行。 */
private fun LazyListScope.meloXLibraryGroupItems(
    keyPrefix: String,
    groups: Map<String, List<Song>>,
    emptyText: String,
    emptySymbol: SfSymbol,
    onPlayGroup: (List<Song>) -> Unit,
) {
    if (groups.isEmpty()) {
        item(key = "$keyPrefix-empty") {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SfIcon(emptySymbol, null, size = 42.dp, tint = LocalGlassColors.current.secondaryContent)
                    Text(
                        emptyText,
                        style = IosTypography.subheadline,
                        color = LocalGlassColors.current.secondaryContent,
                    )
                }
            }
        }
        return
    }
    val entries = groups.entries.sortedBy { it.key.lowercase() }
    items(count = entries.size, key = { "$keyPrefix-${entries[it].key}" }) { index ->
        val entry = entries[index]
        MeloXLibraryMediaRow(
            image = entry.value.firstOrNull { it.albumArtUri != null }?.albumArtUri,
            title = entry.key,
            subtitle = "${entry.value.size} 首歌曲",
            highlighted = false,
            onClick = { onPlayGroup(entry.value) },
        )
    }
}

private fun LazyListScope.meloXLibrarySongsItems(
    songs: List<Song>,
    currentSongId: Long?,
    isLoading: Boolean,
    onPlayFromQueue: (List<Song>, Int) -> Unit,
    onHeartMode: (() -> Unit)?,
    onSongMore: (Song) -> Unit,
    onlineTracks: List<OnlineTrack>,
    onPlayOnlineTracks: (List<OnlineTrack>, OnlineTrack) -> Unit,
) {
    if (songs.isEmpty() && onlineTracks.isEmpty()) {
        item(key = "songs-empty") {
            Box(
                Modifier.fillMaxWidth().height(if (isLoading) 120.dp else 200.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SfIcon(SfSymbol.MusicNote, null, size = 42.dp, tint = LocalGlassColors.current.secondaryContent)
                        Text("暂无歌曲", style = IosTypography.subheadline, color = LocalGlassColors.current.secondaryContent)
                    }
                }
            }
        }
        return
    }
    item(key = "songs-actions") {
        IosGroupedList {
            IosListRow(
                title = "播放全部",
                systemName = "play.fill",
                showTopSeparator = false,
                onClick = {
                    if (onlineTracks.isNotEmpty()) onPlayOnlineTracks(onlineTracks, onlineTracks.first())
                    else onPlayFromQueue(songs, 0)
                },
            )
            if (onHeartMode != null) {
                IosListRow(
                    title = "心动模式",
                    leading = { SfIcon(SfSymbol.Heart, null, size = 23.dp, tint = LocalGlassColors.current.accent) },
                    onClick = onHeartMode,
                )
            }
        }
    }
    if (onlineTracks.isNotEmpty()) {
        items(count = onlineTracks.size, key = { "online-song-${onlineTracks[it].stableId}" }) { index ->
            val track = onlineTracks[index]
            MeloXLibraryMediaRow(
                image = track.artworkUrl,
                title = track.title,
                subtitle = track.artist,
                highlighted = false,
                onClick = { onPlayOnlineTracks(onlineTracks, track) },
            )
        }
    } else items(count = songs.size, key = { "song-${songs[it].id}" }) { index ->
        val song = songs[index]
        MeloXLibraryMediaRow(
            image = song.albumArtUri,
            title = song.title,
            subtitle = song.artist,
            highlighted = song.id == currentSongId,
            onClick = { onPlayFromQueue(songs, index) },
            trailing = {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(ContinuousRoundedRectangle(22.dp))
                        .clickable(
                            interactionSource = null,
                            indication = null,
                        ) { onSongMore(song) },
                    contentAlignment = Alignment.Center,
                ) {
                    SfIcon(SfSymbol.Ellipsis, "更多操作", size = 18.dp)
                }
            },
        )
    }
}

private fun LazyListScope.meloXLibraryPlaylistsItems(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onlinePlaylistSummaries: List<RemotePlaylistSummary>,
    onOpenOnlinePlaylist: (RemotePlaylistSummary) -> Unit,
) {
    if (playlists.isEmpty() && onlinePlaylistSummaries.isEmpty()) {
        item(key = "playlists-empty") {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SfIcon(SfSymbol.MusicNoteList, null, size = 42.dp, tint = LocalGlassColors.current.secondaryContent)
                    Text("还没有歌单", style = IosTypography.subheadline, color = LocalGlassColors.current.secondaryContent)
                }
            }
        }
        return
    }
    if (onlinePlaylistSummaries.isNotEmpty()) {
        items(count = onlinePlaylistSummaries.size, key = { "online-playlist-${onlinePlaylistSummaries[it].id}" }) { index ->
            val playlist = onlinePlaylistSummaries[index]
            MeloXLibraryMediaRow(
                image = playlist.coverUrl,
                title = playlist.name,
                subtitle = "${playlist.trackCount} 首歌曲",
                highlighted = false,
                onClick = { onOpenOnlinePlaylist(playlist) },
            )
        }
    } else items(count = playlists.size, key = { "playlist-${playlists[it].id}" }) { index ->
        val playlist = playlists[index]
        MeloXLibraryMediaRow(
            image = playlist.coverUri?.let(Uri::parse),
            title = playlist.name,
            subtitle = "${playlist.songIds.size} 首歌曲",
            highlighted = false,
            onClick = { onPlaylistClick(playlist) },
        )
    }
}

@Composable
internal fun MeloXLibraryMediaRow(
    image: Any?,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(54.dp)
                .clip(ContinuousRoundedRectangle(9.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = IosTypography.body,
                color = if (highlighted) LocalGlassColors.current.accent else LocalGlassColors.current.content,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = IosTypography.caption,
                color = LocalGlassColors.current.secondaryContent,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
        if (trailing == null) {
            Spacer(Modifier.width(8.dp))
        }
    }
}
