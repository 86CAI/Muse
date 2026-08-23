package com.caipan.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.caipan.music.R
import com.caipan.music.data.NeteaseSession
import com.caipan.music.online.NeteaseHomeContent
import com.caipan.music.online.OnlineTrack
import com.caipan.music.online.RemotePlaylistSummary

/**
 * Online home reimplemented from the visual hierarchy of Mei's mobile home, not its GPL code.
 * It deliberately leaves the Muse profile route and the Muse liquid dock outside this layout.
 */
@Composable
internal fun MeiOnlineHomeContent(
    home: NeteaseHomeContent?,
    session: NeteaseSession?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    onPlaylist: (Long) -> Unit,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onOpenComments: (OnlineTrack) -> Unit,
    onAccount: () -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    isLightTheme: Boolean,
    isChinese: Boolean
) {
    val playlists = home?.playlists.orEmpty()
    val newSongs = home?.newSongs.orEmpty()
    val greeting = remember(isChinese) { meiGreeting(isChinese) }
    val firstTrack = newSongs.firstOrNull()
    val firstPlaylist = playlists.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item(key = "mei-home-title") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MeiAvatar(
                        avatarUrl = session?.avatarUrl,
                        onClick = onAccount,
                        tint = accentColor,
                        isLightTheme = isLightTheme,
                        description = if (isChinese) "打开个人页" else "Open profile"
                    )
                }
                Spacer(Modifier.height(38.dp))
                Text(
                    text = if (isChinese) "首页" else "Home",
                    color = textColor,
                    fontSize = 50.sp,
                    lineHeight = 54.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1.4).sp
                )
                Spacer(Modifier.height(38.dp))
                Text(
                    text = greeting,
                    color = textColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.4).sp
                )
            }
        }

        item(key = "mei-home-feature-cards") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MeiFeatureCard(
                    title = if (isChinese) "新歌推荐" else "Fresh picks",
                    subtitle = if (isChinese) "符合你口味的新鲜好歌" else "Fresh music for you",
                    artworkUrl = firstTrack?.artworkUrl,
                    accent = Color(0xFF1A73D9),
                    onClick = {
                        if (firstTrack != null) onPlay(firstTrack, newSongs) else onRefresh()
                    },
                    modifier = Modifier.weight(1f),
                    isLightTheme = isLightTheme
                )
                MeiFeatureCard(
                    title = if (isChinese) "推荐歌单" else "Top playlists",
                    subtitle = firstPlaylist?.name ?: if (isChinese) "为你挑选的音乐" else "Picked for you",
                    artworkUrl = firstPlaylist?.coverUrl,
                    accent = Color(0xFFE83F66),
                    onClick = {
                        if (firstPlaylist != null) onPlaylist(firstPlaylist.id) else onRefresh()
                    },
                    modifier = Modifier.weight(1f),
                    isLightTheme = isLightTheme
                )
            }
        }

        if (session == null) {
            item(key = "mei-home-login") {
                MeiLoginPanel(
                    onLogin = onLogin,
                    textColor = textColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    isLightTheme = isLightTheme,
                    isChinese = isChinese
                )
            }
        }

        if (loading && home == null) {
            item(key = "mei-home-loading") {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            }
        }

        error?.let { message ->
            item(key = "mei-home-error") {
                MeiInlineStatus(
                    message = message,
                    onRetry = onRefresh,
                    textColor = textColor,
                    mutedColor = mutedColor,
                    isLightTheme = isLightTheme,
                    isChinese = isChinese
                )
            }
        }

        if (playlists.isNotEmpty()) {
            item(key = "mei-home-playlists-title") {
                MeiSectionTitle(
                    title = if (isChinese) "推荐歌单" else "Recommended playlists",
                    textColor = textColor
                )
            }
            item(key = "mei-home-playlists") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        MeiPlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylist(playlist.id) },
                            textColor = textColor,
                            mutedColor = mutedColor,
                            isLightTheme = isLightTheme
                        )
                    }
                }
            }
        }

        if (newSongs.isNotEmpty()) {
            item(key = "mei-home-new-title") {
                MeiSectionTitle(
                    title = if (isChinese) "新歌推荐" else "Fresh tracks",
                    trailing = if (isChinese) "${newSongs.size} 首" else "${newSongs.size}",
                    textColor = textColor,
                    mutedColor = mutedColor
                )
            }
            items(newSongs.take(6), key = { it.stableId }) { track ->
                MeiTrackRow(
                    track = track,
                    queue = newSongs,
                    onPlay = onPlay,
                    onOpenComments = onOpenComments,
                    textColor = textColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    isLightTheme = isLightTheme,
                    isChinese = isChinese
                )
            }
        }

        if (!loading && home == null && error == null) {
            item(key = "mei-home-empty") {
                MeiEmptyPanel(
                    icon = Icons.Default.Cloud,
                    title = if (isChinese) "暂时没有推荐内容" else "No recommendations yet",
                    detail = if (isChinese) "下拉或点击推荐卡重新加载" else "Try refreshing to load recommendations",
                    textColor = textColor,
                    mutedColor = mutedColor,
                    isLightTheme = isLightTheme
                )
            }
        }
    }
}

/** Mei-style six-page library shell fed only by data Muse actually has. */
@Composable
internal fun MeiOnlineLibraryContent(
    playlists: List<RemotePlaylistSummary>,
    likedSongs: List<OnlineTrack>,
    recentSongs: List<OnlineTrack>,
    session: NeteaseSession?,
    loading: Boolean,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onPlaylist: (Long) -> Unit,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onOpenComments: (OnlineTrack) -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    isLightTheme: Boolean,
    isChinese: Boolean
) {
    var selectedPage by rememberSaveable { mutableIntStateOf(0) }
    val pages = if (isChinese) {
        listOf("歌曲", "歌单", "播客", "下载", "云盘", "历史")
    } else {
        listOf("Songs", "Lists", "Pods", "Downloads", "Cloud", "History")
    }
    val currentUserId = session?.userId?.takeIf { it > 0L }
    val createdPlaylists = playlists.filter {
        currentUserId != null && it.creatorUserId == currentUserId && !it.subscribed
    }
    val collectedPlaylists = playlists.filterNot {
        currentUserId != null && it.creatorUserId == currentUserId && !it.subscribed
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "mei-library-title") {
            Column {
                Text(
                    text = if (isChinese) "音乐库" else "Music library",
                    color = textColor,
                    fontSize = 48.sp,
                    lineHeight = 52.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1.5).sp
                )
                session?.nickname?.takeIf { it.isNotBlank() }?.let { name ->
                    Text(
                        text = if (isChinese) "$name 的音乐" else "$name's music",
                        color = mutedColor,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        item(key = "mei-library-pages") {
            MeiLibrarySegments(
                labels = pages,
                selectedIndex = selectedPage,
                onSelect = { selectedPage = it },
                textColor = textColor,
                mutedColor = mutedColor,
                isLightTheme = isLightTheme
            )
        }

        if (session == null) {
            item(key = "mei-library-login") {
                MeiLoginPanel(
                    onLogin = onLogin,
                    textColor = textColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    isLightTheme = isLightTheme,
                    isChinese = isChinese
                )
            }
        }

        when (selectedPage) {
            0 -> {
                item(key = "mei-library-actions") {
                    MeiLibraryActionCard(
                        enabled = likedSongs.isNotEmpty(),
                        onPlayAll = { likedSongs.firstOrNull()?.let { onPlay(it, likedSongs) } },
                        textColor = textColor,
                        mutedColor = mutedColor,
                        accentColor = accentColor,
                        isLightTheme = isLightTheme,
                        isChinese = isChinese
                    )
                }
                if (likedSongs.isNotEmpty()) {
                    items(likedSongs, key = { it.stableId }) { track ->
                        MeiTrackRow(
                            track = track,
                            queue = likedSongs,
                            onPlay = onPlay,
                            onOpenComments = onOpenComments,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            accentColor = accentColor,
                            isLightTheme = isLightTheme,
                            isChinese = isChinese
                        )
                    }
                } else if (!loading) {
                    item(key = "mei-library-songs-empty") {
                        MeiEmptyPanel(
                            icon = Icons.Default.Favorite,
                            title = if (isChinese) "还没有收藏歌曲" else "No liked songs",
                            detail = if (isChinese) "收藏的网易云歌曲会显示在这里" else "Your liked NetEase tracks appear here",
                            textColor = textColor,
                            mutedColor = mutedColor,
                            isLightTheme = isLightTheme
                        )
                    }
                }
            }

            1 -> {
                if (playlists.isEmpty() && !loading) {
                    item(key = "mei-library-playlists-empty") {
                        MeiEmptyPanel(
                            icon = Icons.Default.LibraryMusic,
                            title = if (isChinese) "还没有歌单" else "No playlists",
                            detail = if (isChinese) "创建和收藏的歌单会同步到这里" else "Created and collected playlists appear here",
                            textColor = textColor,
                            mutedColor = mutedColor,
                            isLightTheme = isLightTheme
                        )
                    }
                } else {
                    if (createdPlaylists.isNotEmpty()) {
                        item(key = "mei-library-created-title") {
                            MeiSectionTitle(
                                title = if (isChinese) "我创建的歌单" else "Created playlists",
                                trailing = if (isChinese) "${createdPlaylists.size} 个" else "${createdPlaylists.size}",
                                textColor = textColor,
                                mutedColor = mutedColor
                            )
                        }
                        items(createdPlaylists, key = { it.id }) { playlist ->
                            MeiLibraryPlaylistRow(playlist, onPlaylist, textColor, mutedColor, isLightTheme, isChinese)
                        }
                    }
                    if (collectedPlaylists.isNotEmpty()) {
                        item(key = "mei-library-collected-title") {
                            MeiSectionTitle(
                                title = if (isChinese) "收藏的歌单" else "Collected playlists",
                                trailing = if (isChinese) "${collectedPlaylists.size} 个" else "${collectedPlaylists.size}",
                                textColor = textColor,
                                mutedColor = mutedColor
                            )
                        }
                        items(collectedPlaylists, key = { it.id }) { playlist ->
                            MeiLibraryPlaylistRow(playlist, onPlaylist, textColor, mutedColor, isLightTheme, isChinese)
                        }
                    }
                }
            }

            2 -> item(key = "mei-library-podcast-empty") {
                MeiUnavailablePanel(
                    icon = Icons.Default.Podcasts,
                    title = if (isChinese) "播客正在准备中" else "Podcasts are coming",
                    detail = if (isChinese) "Muse 暂未接入网易云播客数据" else "Muse has not connected NetEase podcast data yet",
                    textColor = textColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    isLightTheme = isLightTheme
                )
            }

            3 -> item(key = "mei-library-downloads-empty") {
                MeiUnavailablePanel(
                    icon = Icons.Default.Download,
                    title = if (isChinese) "没有离线下载" else "No downloads",
                    detail = if (isChinese) "在线下载接入后会显示在这里" else "Online downloads will appear here",
                    textColor = textColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    isLightTheme = isLightTheme
                )
            }

            4 -> item(key = "mei-library-cloud-empty") {
                MeiUnavailablePanel(
                    icon = Icons.Default.Cloud,
                    title = if (isChinese) "云盘正在准备中" else "Cloud music is coming",
                    detail = if (isChinese) "暂不伪造未接入的云盘内容" else "Cloud content is shown when it is actually connected",
                    textColor = textColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    isLightTheme = isLightTheme
                )
            }

            else -> {
                if (recentSongs.isEmpty() && !loading) {
                    item(key = "mei-library-history-empty") {
                        MeiEmptyPanel(
                            icon = Icons.Default.History,
                            title = if (isChinese) "暂无最近播放" else "Nothing played recently",
                            detail = if (isChinese) "网易云播放记录会显示在这里" else "Your NetEase listening history appears here",
                            textColor = textColor,
                            mutedColor = mutedColor,
                            isLightTheme = isLightTheme
                        )
                    }
                } else {
                    items(recentSongs, key = { it.stableId }) { track ->
                        MeiTrackRow(
                            track = track,
                            queue = recentSongs,
                            onPlay = onPlay,
                            onOpenComments = onOpenComments,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            accentColor = accentColor,
                            isLightTheme = isLightTheme,
                            isChinese = isChinese
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MeiFeatureCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier,
    isLightTheme: Boolean
) {
    val shape = RoundedCornerShape(16.dp)
    val panel = if (isLightTheme) Color(0xFFF2F2F7) else Color(0xFF19191D)
    Column(
        modifier = modifier
            .height(196.dp)
            .clip(shape)
            .background(panel)
            .pressScale()
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).background(accent.copy(alpha = 0.22f))) {
            MeiArtwork(artworkUrl, title, Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Text(
            text = subtitle,
            color = if (isLightTheme) Color(0xFF1C1C1E) else Color.White,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun MeiPlaylistCard(
    playlist: RemotePlaylistSummary,
    onClick: () -> Unit,
    textColor: Color,
    mutedColor: Color,
    isLightTheme: Boolean
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .pressScale()
            .clickable(onClick = onClick)
    ) {
        MeiArtwork(
            url = playlist.coverUrl,
            description = playlist.name,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isLightTheme) Color(0xFFE8E8ED) else Color(0xFF1B1B1E))
        )
        Text(
            text = playlist.name,
            color = textColor,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp)
        )
        Text(
            text = "${playlist.trackCount} ${if (playlist.trackCount == 1) "song" else "songs"}",
            color = mutedColor,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun MeiTrackRow(
    track: OnlineTrack,
    queue: List<OnlineTrack>,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onOpenComments: (OnlineTrack) -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    isLightTheme: Boolean,
    isChinese: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .pressScale()
                .clickable { onPlay(track, queue) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MeiArtwork(
                url = track.artworkUrl,
                description = track.title,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isLightTheme) Color(0xFFE8E8ED) else Color(0xFF1B1B1E))
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = track.title,
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(track.artist, track.album).filter(String::isNotBlank).joinToString(" · "),
                    color = mutedColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            if (track.durationMs > 0L) {
                Text(track.formattedDuration, color = mutedColor, fontSize = 13.sp)
            }
            IconButton(
                onClick = { onOpenComments(track) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = if (isChinese) "查看评论" else "View comments",
                    tint = mutedColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = if (isChinese) "播放 ${track.title}" else "Play ${track.title}",
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 66.dp),
            color = mutedColor.copy(alpha = 0.17f)
        )
    }
}

@Composable
private fun MeiLibrarySegments(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    textColor: Color,
    mutedColor: Color,
    isLightTheme: Boolean
) {
    val container = if (isLightTheme) Color(0xFFE5E5EA) else Color(0xFF202023)
    val selected = if (isLightTheme) Color(0xFF8E8E93) else Color(0xFF69696E)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(container)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val selectedColor by animateColorAsState(
                targetValue = if (index == selectedIndex) selected else Color.Transparent,
                label = "meiLibrarySegment"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(selectedColor)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (index == selectedIndex) Color.White else mutedColor,
                    fontSize = 15.sp,
                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MeiLibraryActionCard(
    enabled: Boolean,
    onPlayAll: () -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    isLightTheme: Boolean,
    isChinese: Boolean
) {
    val panel = if (isLightTheme) Color(0xFFF2F2F7) else Color(0xFF1E1E21)
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(panel)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        MeiLibraryActionRow(
            icon = Icons.Default.PlayArrow,
            iconTint = accentColor,
            title = if (isChinese) "播放全部" else "Play all",
            onClick = onPlayAll,
            enabled = enabled,
            textColor = textColor,
            mutedColor = mutedColor
        )
        HorizontalDivider(color = mutedColor.copy(alpha = 0.22f))
        MeiLibraryActionRow(
            icon = Icons.Default.Favorite,
            iconTint = accentColor,
            title = if (isChinese) "心动模式" else "Heart mode",
            onClick = {},
            enabled = false,
            textColor = textColor,
            mutedColor = mutedColor
        )
    }
}

@Composable
private fun MeiLibraryActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    onClick: () -> Unit,
    enabled: Boolean,
    textColor: Color,
    mutedColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(30.dp))
        Text(
            text = title,
            color = if (enabled) textColor else mutedColor,
            fontSize = 23.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 18.dp).weight(1f)
        )
        Text("›", color = mutedColor, fontSize = 42.sp, lineHeight = 42.sp)
    }
}

@Composable
private fun MeiLibraryPlaylistRow(
    playlist: RemotePlaylistSummary,
    onPlaylist: (Long) -> Unit,
    textColor: Color,
    mutedColor: Color,
    isLightTheme: Boolean,
    isChinese: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .pressScale()
                .clickable { onPlaylist(playlist.id) }
                .padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MeiArtwork(
                url = playlist.coverUrl,
                description = playlist.name,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isLightTheme) Color(0xFFE8E8ED) else Color(0xFF1B1B1E))
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(playlist.name, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val metadata = buildString {
                    append(if (isChinese) "${playlist.trackCount} 首歌曲" else "${playlist.trackCount} songs")
                    playlist.creatorName.takeIf(String::isNotBlank)?.let { append(" · $it") }
                }
                Text(metadata, color = mutedColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            Text("›", color = mutedColor, fontSize = 32.sp)
        }
        HorizontalDivider(modifier = Modifier.padding(start = 66.dp), color = mutedColor.copy(alpha = 0.17f))
    }
}

@Composable
private fun MeiSectionTitle(title: String, textColor: Color, trailing: String? = null, mutedColor: Color = textColor.copy(alpha = 0.58f)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = textColor, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.7).sp, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, color = mutedColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun MeiAvatar(avatarUrl: String?, onClick: () -> Unit, tint: Color, isLightTheme: Boolean, description: String) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(if (isLightTheme) Color(0xFFE5E5EA) else Color(0xFF202023))
            .pressScale()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        MeiArtwork(avatarUrl, description, Modifier.fillMaxSize().clip(CircleShape))
        if (avatarUrl.isNullOrBlank()) {
            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun MeiArtwork(url: String?, description: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_apple_music),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
            modifier = Modifier.size(24.dp)
        )
        val normalized = com.caipan.music.online.neteaseImageRequestUrl(url, 480)
        if (normalized != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(normalized).crossfade(true).build(),
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun MeiLoginPanel(
    onLogin: () -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    isLightTheme: Boolean,
    isChinese: Boolean
) {
    val panel = if (isLightTheme) Color(0xFFF2F2F7) else Color(0xFF1C1C20)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(panel)
            .pressScale()
            .clickable(onClick = onLogin)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = accentColor, modifier = Modifier.size(34.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(if (isChinese) "登录网易云音乐" else "Sign in to NetEase Music", color = textColor, fontWeight = FontWeight.Bold)
            Text(if (isChinese) "同步推荐、歌单和播放记录" else "Sync recommendations, playlists and history", color = mutedColor, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Text(if (isChinese) "登录" else "Sign in", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun MeiInlineStatus(message: String, onRetry: () -> Unit, textColor: Color, mutedColor: Color, isLightTheme: Boolean, isChinese: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isLightTheme) Color(0xFFFFF1F2) else Color(0xFF2B1B20))
            .clickable(onClick = onRetry)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(message, color = textColor, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (isChinese) "点按重试" else "Tap to retry", color = mutedColor, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Icon(Icons.Default.Search, contentDescription = null, tint = mutedColor)
    }
}

@Composable
private fun MeiEmptyPanel(icon: ImageVector, title: String, detail: String, textColor: Color, mutedColor: Color, isLightTheme: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (isLightTheme) Color(0xFFF2F2F7) else Color(0xFF19191D))
            .padding(vertical = 36.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = mutedColor, modifier = Modifier.size(30.dp))
        Text(title, color = textColor, fontWeight = FontWeight.Bold)
        Text(detail, color = mutedColor, fontSize = 12.sp)
    }
}

@Composable
private fun MeiUnavailablePanel(icon: ImageVector, title: String, detail: String, textColor: Color, mutedColor: Color, accentColor: Color, isLightTheme: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (isLightTheme) Color(0xFFF2F2F7) else Color(0xFF19191D))
            .padding(vertical = 38.dp, horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(32.dp))
        Text(title, color = textColor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = mutedColor, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

private fun meiGreeting(isChinese: Boolean): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    if (!isChinese) return when (hour) {
        in 5..10 -> "Good morning"
        in 11..17 -> "Good afternoon"
        else -> "Good evening"
    }
    return when (hour) {
        in 5..10 -> "早上好"
        in 11..17 -> "下午好"
        else -> "晚上好"
    }
}
