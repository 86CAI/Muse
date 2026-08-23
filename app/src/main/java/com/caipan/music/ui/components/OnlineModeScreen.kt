package com.caipan.music.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.caipan.music.R
import com.caipan.music.data.NeteaseSession
import com.caipan.music.online.NeteaseHomeContent
import com.caipan.music.online.OnlineTrack
import com.caipan.music.online.RemotePlaylistDetail
import com.caipan.music.online.RemotePlaylistSummary
import com.caipan.music.ui.theme.MuseDesign
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

@Composable
fun OnlineModeScreen(
    home: NeteaseHomeContent?,
    playlists: List<RemotePlaylistSummary>,
    likedSongs: List<OnlineTrack>,
    recentSongs: List<OnlineTrack>,
    session: NeteaseSession?,
    playlistDetail: RemotePlaylistDetail?,
    loading: Boolean,
    error: String?,
    selectedTab: Int,
    onSelectedTab: (Int) -> Unit,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenWebdav: () -> Unit,
    onPlaylist: (Long) -> Unit,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onOpenComments: (OnlineTrack) -> Unit,
    onPlayPodcast: (com.caipan.music.online.NeteaseHomePodcast) -> Unit,
    onBackFromDetail: () -> Unit,
    accentColor: Color,
    textColor: Color,
    mutedColor: Color,
    backdrop: Backdrop?,
    bottomBarBackdrop: Backdrop?,
    liquidGlass: Boolean,
    isLightTheme: Boolean,
    isChinese: Boolean,
    accountContent: @Composable ((Long) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var openedPlaylistId by remember { mutableLongStateOf(0L) }
    val detailOpen = openedPlaylistId > 0L
    val contentBackdrop = rememberLayerBackdrop()
    val pageBackdrop = bottomBarBackdrop ?: backdrop
    val onlineBottomBackdrop = pageBackdrop?.let { rememberCombinedBackdrop(it, contentBackdrop) } ?: contentBackdrop
    BackHandler(enabled = detailOpen) {
        openedPlaylistId = 0L
        onBackFromDetail()
    }
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
        when {
            detailOpen -> {
                RemotePlaylistDetailContentV2(
                    detail = playlistDetail,
                    loading = loading,
                    onBack = { openedPlaylistId = 0L; onBackFromDetail() },
                    onPlay = onPlay,
                    onOpenComments = onOpenComments,
                    textColor = textColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    backdrop = backdrop,
                    isChinese = isChinese
                )
            }
            selectedTab == 0 -> MeiStyleOnlineHomeContent(
                home = home,
                session = session,
                loading = loading,
                error = error,
                onRefresh = onRefresh,
                onLogin = onLogin,
                onSearch = onSearch,
                onPlaylist = { id -> openedPlaylistId = id; onPlaylist(id) },
                onPlay = onPlay,
                onOpenComments = onOpenComments,
                onPlayPodcast = onPlayPodcast,
                onAccount = { onSelectedTab(2) },
                textColor = textColor,
                mutedColor = mutedColor,
                accentColor = accentColor,
                isLightTheme = isLightTheme,
                isChinese = isChinese
            )
            selectedTab == 1 -> MeiStyleOnlineLibraryContent(
                playlists = playlists,
                likedSongs = likedSongs,
                recentSongs = recentSongs,
                session = session,
                loading = loading,
                onLogin = onLogin,
                onRefresh = onRefresh,
                onSearch = onSearch,
                onOpenWebdav = onOpenWebdav,
                onPlaylist = { id -> openedPlaylistId = id; onPlaylist(id) },
                onPlay = onPlay,
                onOpenComments = onOpenComments,
                textColor = textColor,
                mutedColor = mutedColor,
                accentColor = accentColor,
                isLightTheme = isLightTheme,
                isChinese = isChinese
            )
            else -> accountContent { id ->
                openedPlaylistId = id
                onPlaylist(id)
            }
        }
        }
        /*
        if (!detailOpen) MuseLiquidBottomTabs(
            selectedIndex = selectedTab.coerceIn(0, 2),
            onSelected = onSelectedTab,
            tabs = listOf(
                MuseLiquidTab(if (isChinese) "首页" else "Home", Icons.Default.Cloud, painterResource(R.drawable.ic_apple_home)),
                MuseLiquidTab(if (isChinese) "资料库" else "Library", Icons.Default.LibraryMusic, painterResource(R.drawable.ic_apple_library)),
                MuseLiquidTab(if (isChinese) "账号" else "Account", Icons.Default.AccountCircle, painterResource(R.drawable.ic_apple_user))
            ),
            backdrop = onlineBottomBackdrop,
            accentColor = accentColor,
            contentColor = textColor,
            isLightTheme = isLightTheme,
            liquidGlass = liquidGlass,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        */
        /* Legacy five-destination dock. The compact dock below keeps only real destinations.
        if (!detailOpen) MuseLiquidBottomTabs(
            selectedIndex = when (selectedTab) {
                0 -> 0
                1 -> 2
                else -> 4
            },
            onSelected = { destination ->
                when (destination) {
                    0 -> onSelectedTab(0)
                    1 -> onSearch()
                    2 -> onSelectedTab(1)
                    3 -> onSettings()
                    else -> onSelectedTab(2)
                }
            },
            tabs = listOf(
                MuseLiquidTab(if (isChinese) "首页" else "Home", Icons.Default.Cloud, painterResource(R.drawable.ic_apple_home)),
                MuseLiquidTab(if (isChinese) "发现" else "Discover", Icons.Default.Search, painterResource(R.drawable.ic_apple_explore)),
                MuseLiquidTab(if (isChinese) "音乐库" else "Library", Icons.Default.LibraryMusic, painterResource(R.drawable.ic_apple_library)),
                MuseLiquidTab(if (isChinese) "设置" else "Settings", Icons.Default.Settings, painterResource(R.drawable.ic_apple_settings)),
                MuseLiquidTab(if (isChinese) "账户" else "Account", Icons.Default.AccountCircle, painterResource(R.drawable.ic_apple_user))
            ),
            backdrop = onlineBottomBackdrop,
            accentColor = accentColor,
            contentColor = textColor,
            isLightTheme = isLightTheme,
            liquidGlass = liquidGlass,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        */
        if (!detailOpen) MuseLiquidBottomTabs(
            selectedIndex = selectedTab.coerceIn(0, 2),
            onSelected = onSelectedTab,
            tabs = listOf(
                MuseLiquidTab(if (isChinese) "\u9996\u9875" else "Home", Icons.Default.Cloud, painterResource(R.drawable.ic_apple_home)),
                MuseLiquidTab(if (isChinese) "\u97f3\u4e50\u5e93" else "Library", Icons.Default.LibraryMusic, painterResource(R.drawable.ic_apple_library)),
                MuseLiquidTab(if (isChinese) "\u8d26\u6237" else "Account", Icons.Default.AccountCircle, painterResource(R.drawable.ic_apple_user))
            ),
            backdrop = onlineBottomBackdrop,
            accentColor = accentColor,
            contentColor = textColor,
            isLightTheme = isLightTheme,
            liquidGlass = liquidGlass,
            showLabels = true,
            // Keep the compact NetEase navigation translucent while retaining
            // the open-source Liquid Glass container and moving selection lens.
            // The smaller dock avoids the oversized capsule that previously
            // swallowed the bottom content.
            showContainer = true,
            showSelector = true,
            dockHeight = 56.dp,
            horizontalInset = 20.dp,
            verticalInset = 4.dp,
            selectorHeight = 48.dp,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/*
@Composable
private fun OnlineHomeContent(
    home: NeteaseHomeContent?, session: NeteaseSession?, loading: Boolean, error: String?,
    onRefresh: () -> Unit, onLogin: () -> Unit, onSearch: () -> Unit,
    onPlaylist: (Long) -> Unit, onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit,
    textColor: Color, mutedColor: Color, accentColor: Color, backdrop: Backdrop?, isChinese: Boolean
) {
    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(top = 18.dp, bottom = 116.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (isChinese) "网易云" else "NetEase", color = textColor, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(if (session == null) (if (isChinese) "在线推荐" else "Recommendations") else (if (isChinese) "为你推荐" else "For you"), color = mutedColor, fontSize = 13.sp)
                }
                IconButton(onClick = onSearch) { Icon(Icons.Default.Search, if (isChinese) "搜索" else "Search", tint = accentColor) }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, if (isChinese) "刷新" else "Refresh", tint = accentColor) }
            }
        }
        if (session == null) item {
                    ModeLoginCard(onLogin, textColor, mutedColor, accentColor, backdrop, isChinese)
        }
        if (loading && home == null) item {
            Box(Modifier.fillMaxWidth().padding(42.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accentColor) }
        }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp)) } }
        home?.playlists?.takeIf { it.isNotEmpty() }?.let { values ->
                    item { SectionTitle(if (isChinese) "推荐歌单" else "Recommended playlists", textColor, accentColor) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(values, key = { it.id }) { playlist -> RemotePlaylistCard(playlist, accentColor, textColor, onPlaylist, isChinese) }
                }
            }
        }
        home?.newSongs?.takeIf { it.isNotEmpty() }?.let { values ->
            item { SectionTitle(if (isChinese) "新歌推荐" else "New songs", textColor, accentColor) }
            items(values, key = { it.stableId }) { track -> OnlineTrackItem(track, accentColor, { onPlay(track, values) }, isChinese) }
        }
        if (home == null && !loading && error == null) item { Text(if (isChinese) "暂无在线内容" else "No online content yet", color = mutedColor, modifier = Modifier.padding(20.dp)) }
    }
}

/*
@Composable
private fun OnlineLibraryContent(
    playlists: List<RemotePlaylistSummary>, session: NeteaseSession?, loading: Boolean,
    onLogin: () -> Unit, onLogout: () -> Unit, onRefresh: () -> Unit, onPlaylist: (Long) -> Unit,
    textColor: Color, mutedColor: Color, accentColor: Color, backdrop: Backdrop?, isChinese: Boolean
) {
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(top = 18.dp, bottom = 116.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isChinese) "资料库" else "Your library", color = textColor, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, if (isChinese) "刷新" else "Refresh", tint = accentColor) }
            }
        }
        if (session == null) item { ModeLoginCard(onLogin, textColor, mutedColor, accentColor, backdrop, isChinese) }
        if (session != null && playlists.isEmpty() && loading) item { Box(Modifier.fillMaxWidth().padding(42.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accentColor) } }
        items(playlists, key = { it.id }) { playlist ->
            Row(Modifier.fillMaxWidth().clickable { onPlaylist(playlist.id) }.padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                NeteaseArtwork(playlist.coverUrl, playlist.name, Modifier.size(62.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(playlist.name, color = textColor, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (isChinese) "${playlist.trackCount} 首${if (playlist.creatorName.isBlank()) "" else " · ${playlist.creatorName}"}" else "${playlist.trackCount} songs${if (playlist.creatorName.isBlank()) "" else " - ${playlist.creatorName}"}", color = mutedColor, fontSize = 12.sp)
                }
                Icon(Icons.Default.PlayArrow, if (isChinese) "打开" else "Open", tint = accentColor)
            }
            HorizontalDivider(Modifier.padding(start = 96.dp), color = mutedColor.copy(alpha = .13f))
        }
        if (session != null) item {
            Text(if (isChinese) "退出登录" else "Sign out", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp).clickable(onClick = onLogout))
        }
    }
}

*/

@Composable
private fun OnlineLibraryContent(
    playlists: List<RemotePlaylistSummary>,
    likedSongs: List<OnlineTrack>,
    recentSongs: List<OnlineTrack>,
    session: NeteaseSession?,
    loading: Boolean,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onPlaylist: (Long) -> Unit,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    backdrop: Backdrop?,
    isChinese: Boolean
) {
    var section by rememberSaveable(session?.userId) { mutableIntStateOf(0) }
    val createdPlaylists = remember(playlists, session?.userId) {
        val userId = session?.userId
        playlists.filter { it.creatorUserId == userId && userId != null }
    }
    val collectedPlaylists = remember(playlists, createdPlaylists) {
        playlists.filter { playlist -> playlist !in createdPlaylists }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(top = 18.dp, bottom = 116.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isChinese) "资料库" else "Your library",
                        color = textColor,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (session == null) {
                            if (isChinese) "登录以同步你的网易云音乐" else "Sign in to sync your NetEase music"
                        } else {
                            if (isChinese) "你的音乐与播放记录" else "Your music and listening history"
                        },
                        color = mutedColor,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        if (isChinese) "刷新资料库" else "Refresh library",
                        tint = accentColor
                    )
                }
            }
        }

        if (session == null) {
            item { ModeLoginCard(onLogin, textColor, mutedColor, accentColor, backdrop, isChinese) }
            return@LazyColumn
        }

        item {
            LibrarySegmentControl(
                selectedSection = section,
                isChinese = isChinese,
                textColor = textColor,
                mutedColor = mutedColor,
                accentColor = accentColor,
                onSectionChanged = { section = it }
            )
        }

        if (loading && playlists.isEmpty() && likedSongs.isEmpty() && recentSongs.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 54.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = accentColor) }
            }
        }

        when (section) {
            0 -> {
                item {
                    LibraryFeatureCard(
                        icon = Icons.Default.Favorite,
                        title = if (isChinese) "我喜欢的音乐" else "Liked songs",
                        subtitle = if (isChinese) {
                            "${likedSongs.size} 首歌曲 · 点击播放全部"
                        } else {
                            "${likedSongs.size} songs · Play all"
                        },
                        accentColor = accentColor,
                        textColor = textColor,
                        mutedColor = mutedColor,
                        backdrop = backdrop,
                        enabled = likedSongs.isNotEmpty(),
                        onClick = { likedSongs.firstOrNull()?.let { onPlay(it, likedSongs) } }
                    )
                }
                if (likedSongs.isNotEmpty()) {
                    item { LibrarySectionHeading(if (isChinese) "收藏歌曲" else "Liked songs", likedSongs.size, textColor, mutedColor) }
                    items(likedSongs, key = { it.stableId }) { track ->
                        OnlineTrackItem(track, accentColor, { onPlay(track, likedSongs) }, isChinese)
                    }
                } else if (!loading) {
                    item {
                        LibraryEmptyState(
                            if (isChinese) "还没有可显示的收藏歌曲" else "No liked songs to show yet",
                            mutedColor
                        )
                    }
                }
            }

            1 -> {
                if (createdPlaylists.isNotEmpty()) {
                    item { LibrarySectionHeading(if (isChinese) "我创建的歌单" else "Created by you", createdPlaylists.size, textColor, mutedColor) }
                    items(createdPlaylists, key = { it.id }) { playlist ->
                        LibraryPlaylistRow(playlist, accentColor, textColor, mutedColor, isChinese) { onPlaylist(playlist.id) }
                    }
                }
                if (collectedPlaylists.isNotEmpty()) {
                    item { LibrarySectionHeading(if (isChinese) "我收藏的歌单" else "Collected playlists", collectedPlaylists.size, textColor, mutedColor) }
                    items(collectedPlaylists, key = { it.id }) { playlist ->
                        LibraryPlaylistRow(playlist, accentColor, textColor, mutedColor, isChinese) { onPlaylist(playlist.id) }
                    }
                }
                if (playlists.isEmpty() && !loading) {
                    item {
                        LibraryEmptyState(
                            if (isChinese) "还没有同步到歌单" else "No playlists to show yet",
                            mutedColor
                        )
                    }
                }
            }

            else -> {
                item {
                    LibraryFeatureCard(
                        icon = Icons.Default.History,
                        title = if (isChinese) "最近播放" else "Recently played",
                        subtitle = if (isChinese) {
                            "网易云账户最近听过的 ${recentSongs.size} 首"
                        } else {
                            "${recentSongs.size} songs from your NetEase history"
                        },
                        accentColor = accentColor,
                        textColor = textColor,
                        mutedColor = mutedColor,
                        backdrop = backdrop,
                        enabled = recentSongs.isNotEmpty(),
                        onClick = { recentSongs.firstOrNull()?.let { onPlay(it, recentSongs) } }
                    )
                }
                if (recentSongs.isNotEmpty()) {
                    item { LibrarySectionHeading(if (isChinese) "最近播放" else "Recently played", recentSongs.size, textColor, mutedColor) }
                    items(recentSongs, key = { it.stableId }) { track ->
                        OnlineTrackItem(track, accentColor, { onPlay(track, recentSongs) }, isChinese)
                    }
                } else if (!loading) {
                    item {
                        LibraryEmptyState(
                            if (isChinese) "还没有最近播放记录" else "No listening history to show yet",
                            mutedColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySegmentControl(
    selectedSection: Int,
    isChinese: Boolean,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onSectionChanged: (Int) -> Unit
) {
    val labels = if (isChinese) listOf("歌曲", "歌单", "最近播放") else listOf("Songs", "Playlists", "Recent")
    Row(
        modifier = Modifier
            .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(textColor.copy(alpha = 0.08f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = selectedSection == index
            Text(
                text = label,
                color = if (selected) accentColor else mutedColor,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) accentColor.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onSectionChanged(index) }
                    .padding(vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun LibraryFeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    textColor: Color,
    mutedColor: Color,
    backdrop: Backdrop?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .museGlass(
                backdrop = backdrop,
                shape = shape,
                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
                blurRadius = 16.dp,
                borderColor = Color.White.copy(alpha = 0.12f)
            )
            .museLiquidCardPress(onClick = onClick, enabled = enabled)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentColor)
        }
        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Text(title, color = textColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = mutedColor, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (enabled) accentColor else mutedColor.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun LibrarySectionHeading(title: String, count: Int, textColor: Color, mutedColor: Color) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(7.dp))
        Text(count.toString(), color = mutedColor, fontSize = 12.sp)
    }
}

@Composable
private fun LibraryPlaylistRow(
    playlist: RemotePlaylistSummary,
    accentColor: Color,
    textColor: Color,
    mutedColor: Color,
    isChinese: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .museLiquidCardPress(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeteaseArtwork(playlist.coverUrl, playlist.name, Modifier.size(60.dp).clip(RoundedCornerShape(13.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(playlist.name, color = textColor, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = buildList {
                add(if (isChinese) "${playlist.trackCount} 首" else "${playlist.trackCount} songs")
                playlist.creatorName.takeIf(String::isNotBlank)?.let(::add)
            }.joinToString(if (isChinese) " · " else " · ")
            Text(detail, color = mutedColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.PlayArrow, contentDescription = if (isChinese) "打开歌单" else "Open playlist", tint = accentColor)
    }
}

@Composable
private fun LibraryEmptyState(text: String, mutedColor: Color) {
    Text(
        text,
        color = mutedColor,
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 34.dp)
    )
}

@Composable
private fun RemotePlaylistDetailContent(detail: RemotePlaylistDetail?, loading: Boolean, onBack: () -> Unit, onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit, textColor: Color, mutedColor: Color, accentColor: Color, backdrop: Backdrop?, isChinese: Boolean) {
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 116.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 18.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, if (isChinese) "返回" else "Back", tint = textColor) }
                Text(detail?.summary?.name ?: if (isChinese) "歌单" else "Playlist", color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (loading && detail == null) item { Box(Modifier.fillMaxWidth().padding(42.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accentColor) } }
        detail?.let { value ->
            item { NeteaseArtwork(value.summary.coverUrl, value.summary.name, Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(220.dp).clip(RoundedCornerShape(18.dp))) }
            item { Text(if (isChinese) "${value.tracks.size} 首歌曲" else "${value.tracks.size} songs", color = mutedColor, modifier = Modifier.padding(20.dp, 14.dp, 20.dp, 4.dp)) }
            items(value.tracks, key = { it.stableId }) { track -> OnlineTrackItem(track, accentColor, { onPlay(track, value.tracks) }, isChinese) }
        }
    }
}

@Composable
private fun ModeLoginCard(onLogin: () -> Unit, textColor: Color, mutedColor: Color, accentColor: Color, backdrop: Backdrop?, isChinese: Boolean = false) {
    Column(Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(accentColor.copy(alpha = .10f)).padding(18.dp)) {
        Text(if (isChinese) "解锁网易云资料库" else "Unlock your NetEase library", color = textColor, fontWeight = FontWeight.SemiBold)
        Text(if (isChinese) "登录后查看个人歌单并使用更高音质播放。" else "Sign in to see personal playlists and higher-quality playback.", color = mutedColor, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Text(if (isChinese) "登录" else "Sign in", color = accentColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp).clickable(onClick = onLogin))
    }
}

@Composable
private fun RemotePlaylistCard(playlist: RemotePlaylistSummary, accentColor: Color, textColor: Color, onClick: (Long) -> Unit, isChinese: Boolean = false) {
    Column(Modifier.width(142.dp).clickable { onClick(playlist.id) }) {
        NeteaseArtwork(playlist.coverUrl, playlist.name, Modifier.size(142.dp).clip(RoundedCornerShape(14.dp)))
        Text(playlist.name, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(if (isChinese) "${playlist.trackCount} 首歌曲" else "${playlist.trackCount} songs", color = accentColor, fontSize = 11.sp)
    }
}

@Composable
private fun OnlineTrackItem(track: OnlineTrack, accentColor: Color, onClick: () -> Unit, isChinese: Boolean = false) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        NeteaseArtwork(track.artworkUrl, track.title, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        Text(if (isChinese) listOf(track.artist, track.album).filter(String::isNotBlank).joinToString(" · ") else "${track.artist} - ${track.album}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.PlayArrow, if (isChinese) "播放" else "Play", tint = accentColor)
    }
}

@Composable
private fun SectionTitle(title: String, textColor: Color, accentColor: Color) {
    Text(title, color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
}

*/

@Composable
private fun OnlineHomeContentV2(
    home: NeteaseHomeContent?,
    session: NeteaseSession?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onPlaylist: (Long) -> Unit,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onOpenComments: (OnlineTrack) -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    backdrop: Backdrop?,
    isChinese: Boolean
) {
    val recommendedPlaylists = home?.playlists.orEmpty()
    val newSongs = home?.newSongs.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(top = MuseDesign.Spacing16, bottom = 116.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = MuseDesign.PagePadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isChinese) "\u7f51\u6613\u4e91" else "NetEase",
                        color = textColor,
                        fontSize = MuseDesign.FontDisplay,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            session == null && isChinese -> "\u5728\u7ebf\u63a8\u8350"
                            session == null -> "Online recommendations"
                            isChinese -> "\u4e3a\u4f60\u63a8\u8350"
                            else -> "Picked for you"
                        },
                        color = mutedColor,
                        fontSize = MuseDesign.FontCaption
                    )
                }
                IconButton(onClick = onSearch) {
                    Icon(
                        Icons.Default.Search,
                        if (isChinese) "\u641c\u7d22" else "Search",
                        tint = accentColor
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(
                        Icons.Default.Settings,
                        if (isChinese) "\u8bbe\u7f6e" else "Settings",
                        tint = mutedColor
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        if (isChinese) "\u5237\u65b0" else "Refresh",
                        tint = accentColor
                    )
                }
            }
        }

        if (session == null) {
            item {
                OnlineModeLoginCardV2(
                    onLogin = onLogin,
                    textColor = textColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    backdrop = backdrop,
                    isChinese = isChinese
                )
            }
        }

        if (loading && home == null) {
            item { OnlineSyncingIndicatorV2(accentColor, mutedColor, isChinese) }
        }

        error?.let { message ->
            item {
                OnlineStatusMessageV2(
                    message = message,
                    textColor = textColor,
                    mutedColor = mutedColor,
                    backdrop = backdrop,
                    isChinese = isChinese
                )
            }
        }

        if (recommendedPlaylists.isNotEmpty()) {
            item {
                OnlineSectionHeaderV2(
                    title = if (isChinese) "\u63a8\u8350\u6b4c\u5355" else "Recommended playlists",
                    supporting = if (isChinese) "\u4e3a\u4f60\u6311\u9009" else "Curated for you",
                    textColor = textColor,
                    mutedColor = mutedColor
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = MuseDesign.PagePadding),
                    horizontalArrangement = Arrangement.spacedBy(MuseDesign.Spacing12)
                ) {
                    items(recommendedPlaylists, key = { it.id }) { playlist ->
                        OnlinePlaylistCardV2(
                            playlist = playlist,
                            onClick = { onPlaylist(playlist.id) },
                            accentColor = accentColor,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            backdrop = backdrop,
                            isChinese = isChinese
                        )
                    }
                }
            }
        }

        if (newSongs.isNotEmpty()) {
            item {
                OnlineSectionHeaderV2(
                    title = if (isChinese) "\u65b0\u6b4c\u63a8\u8350" else "New songs",
                    supporting = if (isChinese) "${newSongs.size} \u9996" else "${newSongs.size} songs",
                    textColor = textColor,
                    mutedColor = mutedColor
                )
            }
            items(newSongs, key = { it.stableId }) { track ->
                OnlineMusicTrackRowV2(
                    track = track,
                    queue = newSongs,
                    onPlay = onPlay,
                    onOpenComments = onOpenComments,
                    textColor = textColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    isChinese = isChinese
                )
            }
        }

        if (home == null && !loading && error == null) {
            item {
                OnlineEmptyStateV2(
                    icon = Icons.Default.Cloud,
                    title = if (isChinese) "\u6682\u65e0\u5728\u7ebf\u5185\u5bb9" else "No online music yet",
                    detail = if (isChinese) "\u70b9\u51fb\u5237\u65b0\u91cd\u8bd5" else "Pull a fresh recommendation when you are ready.",
                    textColor = textColor,
                    mutedColor = mutedColor
                )
            }
        }
    }
}

@Composable
private fun OnlineLibraryContentV2(
    playlists: List<RemotePlaylistSummary>,
    likedSongs: List<OnlineTrack>,
    recentSongs: List<OnlineTrack>,
    session: NeteaseSession?,
    loading: Boolean,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onPlaylist: (Long) -> Unit,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onOpenComments: (OnlineTrack) -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    backdrop: Backdrop?,
    isChinese: Boolean
) {
    var section by rememberSaveable(session?.userId) { mutableIntStateOf(0) }
    val currentUserId = session?.userId?.takeIf { it > 0L }
    val createdPlaylists = playlists.filter { playlist ->
        currentUserId != null && playlist.creatorUserId == currentUserId && !playlist.subscribed
    }
    val collectedPlaylists = playlists.filterNot { playlist ->
        currentUserId != null && playlist.creatorUserId == currentUserId && !playlist.subscribed
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(top = MuseDesign.Spacing16, bottom = 116.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = MuseDesign.PagePadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isChinese) "\u8d44\u6599\u5e93" else "Your library",
                        color = textColor,
                        fontSize = MuseDesign.FontDisplay,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            session == null && isChinese -> "\u767b\u5f55\u540e\u540c\u6b65\u6536\u85cf\u4e0e\u64ad\u653e\u8bb0\u5f55"
                            session == null -> "Sign in to sync your music"
                            isChinese -> "${session.nickname.ifBlank { "\u7f51\u6613\u4e91\u97f3\u4e50" }}\u7684\u8d44\u6599\u5e93"
                            else -> "${session.nickname.ifBlank { "NetEase Music" }}'s library"
                        },
                        color = mutedColor,
                        fontSize = MuseDesign.FontCaption
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(
                        Icons.Default.Settings,
                        if (isChinese) "\u8bbe\u7f6e" else "Settings",
                        tint = mutedColor
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        if (isChinese) "\u5237\u65b0\u8d44\u6599\u5e93" else "Refresh library",
                        tint = accentColor
                    )
                }
            }
        }

        if (session == null) {
            item {
                OnlineModeLoginCardV2(
                    onLogin = onLogin,
                    textColor = textColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    backdrop = backdrop,
                    isChinese = isChinese
                )
            }
        } else {
            item {
                OnlineLibrarySegmentsV2(
                    selectedSection = section,
                    onSectionChanged = { section = it },
                    accentColor = accentColor,
                    mutedColor = mutedColor,
                    backdrop = backdrop,
                    isChinese = isChinese
                )
            }

            when (section) {
                0 -> {
                    item {
                        LikedSongsHeroV2(
                            likedSongs = likedSongs,
                            onPlayAll = { likedSongs.firstOrNull()?.let { onPlay(it, likedSongs) } },
                            textColor = textColor,
                            mutedColor = mutedColor,
                            accentColor = accentColor,
                            backdrop = backdrop,
                            isChinese = isChinese
                        )
                    }
                    if (likedSongs.isEmpty()) {
                        if (loading) item { OnlineSyncingIndicatorV2(accentColor, mutedColor, isChinese) }
                        else item {
                            OnlineEmptyStateV2(
                                icon = Icons.Default.Favorite,
                                title = if (isChinese) "\u8fd8\u6ca1\u6709\u6536\u85cf\u6b4c\u66f2" else "No liked songs yet",
                                detail = if (isChinese) "\u5728\u7f51\u6613\u4e91\u97f3\u4e50\u4e2d\u70b9\u4eae\u7ea2\u5fc3\uff0c\u6b4c\u66f2\u4f1a\u51fa\u73b0\u5728\u8fd9\u91cc" else "Songs you heart in NetEase Music will appear here.",
                                textColor = textColor,
                                mutedColor = mutedColor
                            )
                        }
                    } else {
                        item {
                            OnlineSectionHeaderV2(
                                title = if (isChinese) "\u5df2\u6536\u85cf\u6b4c\u66f2" else "Saved songs",
                                supporting = if (isChinese) "${likedSongs.size} \u9996" else "${likedSongs.size} songs",
                                textColor = textColor,
                                mutedColor = mutedColor
                            )
                        }
                        items(likedSongs, key = { it.stableId }) { track ->
                            OnlineMusicTrackRowV2(
                                track = track,
                                queue = likedSongs,
                                onPlay = onPlay,
                                onOpenComments = onOpenComments,
                                textColor = textColor,
                                mutedColor = mutedColor,
                                accentColor = accentColor,
                                isChinese = isChinese
                            )
                        }
                    }
                }

                1 -> {
                    if (playlists.isEmpty()) {
                        if (loading) item { OnlineSyncingIndicatorV2(accentColor, mutedColor, isChinese) }
                        else item {
                            OnlineEmptyStateV2(
                                icon = Icons.Default.LibraryMusic,
                                title = if (isChinese) "\u8fd8\u6ca1\u6709\u6b4c\u5355" else "No playlists yet",
                                detail = if (isChinese) "\u4f60\u521b\u5efa\u548c\u6536\u85cf\u7684\u6b4c\u5355\u4f1a\u540c\u6b65\u5230\u8fd9\u91cc" else "Your created and collected playlists will sync here.",
                                textColor = textColor,
                                mutedColor = mutedColor
                            )
                        }
                    } else {
                        if (createdPlaylists.isNotEmpty()) {
                            item {
                                OnlineSectionHeaderV2(
                                    title = if (isChinese) "\u6211\u521b\u5efa\u7684" else "Created by you",
                                    supporting = if (isChinese) "${createdPlaylists.size} \u4e2a\u6b4c\u5355" else "${createdPlaylists.size} playlists",
                                    textColor = textColor,
                                    mutedColor = mutedColor
                                )
                            }
                            items(createdPlaylists, key = { it.id }) { playlist ->
                                OnlineLibraryPlaylistRowV2(
                                    playlist = playlist,
                                    onClick = { onPlaylist(playlist.id) },
                                    textColor = textColor,
                                    mutedColor = mutedColor,
                                    accentColor = accentColor,
                                    isChinese = isChinese
                                )
                            }
                        }
                        if (collectedPlaylists.isNotEmpty()) {
                            item {
                                OnlineSectionHeaderV2(
                                    title = if (isChinese) "\u6536\u85cf\u7684\u6b4c\u5355" else "Collected playlists",
                                    supporting = if (isChinese) "${collectedPlaylists.size} \u4e2a\u6b4c\u5355" else "${collectedPlaylists.size} playlists",
                                    textColor = textColor,
                                    mutedColor = mutedColor
                                )
                            }
                            items(collectedPlaylists, key = { it.id }) { playlist ->
                                OnlineLibraryPlaylistRowV2(
                                    playlist = playlist,
                                    onClick = { onPlaylist(playlist.id) },
                                    textColor = textColor,
                                    mutedColor = mutedColor,
                                    accentColor = accentColor,
                                    isChinese = isChinese
                                )
                            }
                        }
                    }
                }

                else -> {
                    if (recentSongs.isEmpty()) {
                        if (loading) item { OnlineSyncingIndicatorV2(accentColor, mutedColor, isChinese) }
                        else item {
                            OnlineEmptyStateV2(
                                icon = Icons.Default.History,
                                title = if (isChinese) "\u8fd8\u6ca1\u6709\u6700\u8fd1\u64ad\u653e" else "Nothing played recently",
                                detail = if (isChinese) "\u4f60\u5728\u7f51\u6613\u4e91\u97f3\u4e50\u7684\u64ad\u653e\u8bb0\u5f55\u4f1a\u51fa\u73b0\u5728\u8fd9\u91cc" else "Your NetEase listening history will appear here.",
                                textColor = textColor,
                                mutedColor = mutedColor
                            )
                        }
                    } else {
                        item {
                            OnlineSectionHeaderV2(
                                title = if (isChinese) "\u6700\u8fd1\u64ad\u653e" else "Recently played",
                                supporting = if (isChinese) "${recentSongs.size} \u9996" else "${recentSongs.size} songs",
                                textColor = textColor,
                                mutedColor = mutedColor
                            )
                        }
                        items(recentSongs, key = { it.stableId }) { track ->
                            OnlineMusicTrackRowV2(
                                track = track,
                                queue = recentSongs,
                                onPlay = onPlay,
                                onOpenComments = onOpenComments,
                                textColor = textColor,
                                mutedColor = mutedColor,
                                accentColor = accentColor,
                                isChinese = isChinese
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemotePlaylistDetailContentV2(
    detail: RemotePlaylistDetail?,
    loading: Boolean,
    onBack: () -> Unit,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onOpenComments: (OnlineTrack) -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    backdrop: Backdrop?,
    isChinese: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 116.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = MuseDesign.PagePadding, top = MuseDesign.Spacing16, bottom = MuseDesign.Spacing12),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, if (isChinese) "\u8fd4\u56de" else "Back", tint = textColor)
                }
                Text(
                    detail?.summary?.name ?: if (isChinese) "\u6b4c\u5355" else "Playlist",
                    color = textColor,
                    fontSize = MuseDesign.FontTitle,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (loading && detail == null) {
            item { OnlineSyncingIndicatorV2(accentColor, mutedColor, isChinese) }
        }
        detail?.let { playlistDetail ->
            item {
                val shape = RoundedCornerShape(MuseDesign.RadiusCard)
                Row(
                    modifier = Modifier
                        .padding(horizontal = MuseDesign.PagePadding, vertical = MuseDesign.Spacing8)
                        .fillMaxWidth()
                        .museGlass(
                            backdrop = backdrop,
                            shape = shape,
                            tint = MaterialTheme.colorScheme.surface.copy(alpha = .16f),
                            blurRadius = 18.dp,
                            borderColor = Color.White.copy(alpha = .10f)
                        )
                        .clip(shape)
                        .padding(MuseDesign.Spacing12),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeteaseArtwork(
                        playlistDetail.summary.coverUrl,
                        playlistDetail.summary.name,
                        Modifier.size(96.dp).clip(RoundedCornerShape(MuseDesign.RadiusStandard))
                    )
                    Column(Modifier.weight(1f).padding(start = MuseDesign.Spacing12)) {
                        Text(
                            playlistDetail.summary.name,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (isChinese) "${playlistDetail.tracks.size} \u9996\u6b4c\u66f2" else "${playlistDetail.tracks.size} songs",
                            color = mutedColor,
                            fontSize = MuseDesign.FontCaption,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        playlistDetail.summary.description?.takeIf(String::isNotBlank)?.let { description ->
                            Text(
                                description,
                                color = mutedColor,
                                fontSize = MuseDesign.FontMicro,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            item {
                val tracks = playlistDetail.tracks
                val actionsEnabled = tracks.isNotEmpty()
                val actionShape = RoundedCornerShape(MuseDesign.RadiusStandard)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MuseDesign.PagePadding, vertical = MuseDesign.Spacing8),
                    horizontalArrangement = Arrangement.spacedBy(MuseDesign.Spacing8)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .museGlass(
                                backdrop = backdrop,
                                shape = actionShape,
                                tint = MaterialTheme.colorScheme.surface.copy(alpha = if (actionsEnabled) .16f else .08f),
                                blurRadius = 14.dp,
                                borderColor = Color.White.copy(alpha = if (actionsEnabled) .10f else .05f)
                            )
                            .clip(actionShape)
                            .pressScale(active = actionsEnabled)
                            .clickable(enabled = actionsEnabled) {
                                tracks.firstOrNull()?.let { first -> onPlay(first, tracks) }
                            }
                            .padding(horizontal = MuseDesign.Spacing12),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = if (isChinese) "播放全部" else "Play all",
                            tint = if (actionsEnabled) accentColor else mutedColor,
                            modifier = Modifier.size(21.dp)
                        )
                        Text(
                            text = if (isChinese) "播放全部" else "Play all",
                            color = if (actionsEnabled) textColor else mutedColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .museGlass(
                                backdrop = backdrop,
                                shape = actionShape,
                                tint = MaterialTheme.colorScheme.surface.copy(alpha = if (actionsEnabled) .16f else .08f),
                                blurRadius = 14.dp,
                                borderColor = Color.White.copy(alpha = if (actionsEnabled) .10f else .05f)
                            )
                            .clip(actionShape)
                            .pressScale(active = actionsEnabled)
                            .clickable(enabled = actionsEnabled) {
                                val shuffledTracks = tracks.shuffled()
                                shuffledTracks.firstOrNull()?.let { first -> onPlay(first, shuffledTracks) }
                            }
                            .padding(horizontal = MuseDesign.Spacing12),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = if (isChinese) "随机播放" else "Shuffle",
                            tint = if (actionsEnabled) accentColor else mutedColor,
                            modifier = Modifier.size(21.dp)
                        )
                        Text(
                            text = if (isChinese) "随机播放" else "Shuffle",
                            color = if (actionsEnabled) textColor else mutedColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
            item {
                OnlineSectionHeaderV2(
                    title = if (isChinese) "\u6b4c\u66f2" else "Songs",
                    supporting = if (isChinese) "${playlistDetail.tracks.size} \u9996" else "${playlistDetail.tracks.size} songs",
                    textColor = textColor,
                    mutedColor = mutedColor
                )
            }
            items(playlistDetail.tracks, key = { it.stableId }) { track ->
                OnlineMusicTrackRowV2(
                    track = track,
                    queue = playlistDetail.tracks,
                    onPlay = onPlay,
                    onOpenComments = onOpenComments,
                    textColor = textColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    isChinese = isChinese
                )
            }
        }
    }
}

@Composable
private fun OnlineLibrarySegmentsV2(
    selectedSection: Int,
    onSectionChanged: (Int) -> Unit,
    accentColor: Color,
    mutedColor: Color,
    backdrop: Backdrop?,
    isChinese: Boolean
) {
    val shape = RoundedCornerShape(MuseDesign.RadiusStandard)
    val labels = if (isChinese) {
        listOf("\u6b4c\u66f2", "\u6b4c\u5355", "\u6700\u8fd1\u64ad\u653e")
    } else {
        listOf("Songs", "Playlists", "Recent")
    }
    Row(
        modifier = Modifier
            .padding(horizontal = MuseDesign.PagePadding, vertical = MuseDesign.Spacing16)
            .fillMaxWidth()
            .museGlass(
                backdrop = backdrop,
                shape = shape,
                tint = MaterialTheme.colorScheme.surface.copy(alpha = .15f),
                blurRadius = 14.dp,
                borderColor = Color.White.copy(alpha = .08f)
            )
            .clip(shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = selectedSection == index
            val segmentShape = RoundedCornerShape(MuseDesign.RadiusCompact)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(segmentShape)
                    .then(
                        if (selected) Modifier.background(accentColor.copy(alpha = .88f), segmentShape)
                        else Modifier
                    )
                    .museLiquidCardPress(onClick = { onSectionChanged(index) })
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) Color.White else mutedColor,
                    fontSize = MuseDesign.FontCaption,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun LikedSongsHeroV2(
    likedSongs: List<OnlineTrack>,
    onPlayAll: () -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    backdrop: Backdrop?,
    isChinese: Boolean
) {
    val shape = RoundedCornerShape(MuseDesign.RadiusCard)
    val firstTrack = likedSongs.firstOrNull()
    val playable = firstTrack != null
    Row(
        modifier = Modifier
            .padding(horizontal = MuseDesign.PagePadding)
            .fillMaxWidth()
            .height(144.dp)
            .museGlass(
                backdrop = backdrop,
                shape = shape,
                tint = MaterialTheme.colorScheme.surface.copy(alpha = .18f),
                blurRadius = 18.dp,
                borderColor = Color.White.copy(alpha = .11f)
            )
            .clip(shape)
            .museLiquidCardPress(onClick = onPlayAll, enabled = playable)
            .padding(MuseDesign.Spacing16),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeteaseArtwork(
            url = firstTrack?.artworkUrl,
            description = firstTrack?.title ?: if (isChinese) "\u6211\u559c\u6b22\u7684\u97f3\u4e50" else "Liked songs",
            modifier = Modifier.size(104.dp).clip(RoundedCornerShape(MuseDesign.RadiusStandard))
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = MuseDesign.Spacing16),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isChinese) "\u6211\u559c\u6b22\u7684\u97f3\u4e50" else "Liked songs",
                    color = textColor,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                if (playable) {
                    if (isChinese) "${likedSongs.size} \u9996\u5df2\u6536\u85cf" else "${likedSongs.size} saved songs"
                } else {
                    if (isChinese) "\u4f60\u5fc3\u52a8\u7684\u6b4c\uff0c\u90fd\u5728\u8fd9\u91cc" else "The songs you love, all in one place"
                },
                color = mutedColor,
                fontSize = MuseDesign.FontCaption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accentColor.copy(alpha = if (playable) .18f else .10f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = accentColor, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    if (isChinese) "\u64ad\u653e\u5168\u90e8" else "Play all",
                    color = accentColor,
                    fontSize = MuseDesign.FontCaption,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun OnlinePlaylistCardV2(
    playlist: RemotePlaylistSummary,
    onClick: () -> Unit,
    accentColor: Color,
    textColor: Color,
    mutedColor: Color,
    backdrop: Backdrop?,
    isChinese: Boolean
) {
    val shape = RoundedCornerShape(MuseDesign.RadiusStandard)
    Column(
        modifier = Modifier
            .width(156.dp)
            .museGlass(
                backdrop = backdrop,
                shape = shape,
                tint = MaterialTheme.colorScheme.surface.copy(alpha = .14f),
                blurRadius = 14.dp,
                borderColor = Color.White.copy(alpha = .08f)
            )
            .clip(shape)
            .museLiquidCardPress(onClick = onClick)
            .padding(8.dp)
    ) {
        NeteaseArtwork(
            playlist.coverUrl,
            playlist.name,
            Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(MuseDesign.RadiusCompact))
        )
        Text(
            playlist.name,
            color = textColor,
            fontSize = MuseDesign.FontCaption,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            if (isChinese) "${playlist.trackCount} \u9996\u6b4c\u66f2" else "${playlist.trackCount} songs",
            color = accentColor,
            fontSize = MuseDesign.FontMicro,
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
        )
    }
}

@Composable
private fun OnlineLibraryPlaylistRowV2(
    playlist: RemotePlaylistSummary,
    onClick: () -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    isChinese: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MuseDesign.RadiusStandard))
                .pressScale()
                .clickable(onClick = onClick)
                .padding(horizontal = MuseDesign.PagePadding, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeteaseArtwork(
                playlist.coverUrl,
                playlist.name,
                Modifier.size(62.dp).clip(RoundedCornerShape(MuseDesign.RadiusCompact))
            )
            Column(Modifier.weight(1f).padding(horizontal = MuseDesign.Spacing12)) {
                Text(
                    playlist.name,
                    color = textColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildList {
                    add(if (isChinese) "${playlist.trackCount} \u9996" else "${playlist.trackCount} songs")
                    playlist.creatorName.takeIf(String::isNotBlank)?.let { creator ->
                        add(if (isChinese) creator else "by $creator")
                    }
                }.joinToString(" \u00b7 ")
                Text(meta, color = mutedColor, fontSize = MuseDesign.FontCaption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                Icons.Default.PlayArrow,
                if (isChinese) "\u6253\u5f00\u6b4c\u5355" else "Open playlist",
                tint = accentColor,
                modifier = Modifier.size(23.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 94.dp, end = MuseDesign.PagePadding),
            color = mutedColor.copy(alpha = .13f)
        )
    }
}

@Composable
private fun OnlineMusicTrackRowV2(
    track: OnlineTrack,
    queue: List<OnlineTrack>,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onOpenComments: (OnlineTrack) -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    isChinese: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MuseDesign.RadiusStandard))
                .pressScale()
                .clickable { onPlay(track, queue) }
                .padding(horizontal = MuseDesign.PagePadding, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeteaseArtwork(
                track.artworkUrl,
                track.title,
                Modifier.size(54.dp).clip(RoundedCornerShape(MuseDesign.RadiusCompact))
            )
            Column(Modifier.weight(1f).padding(horizontal = MuseDesign.Spacing12)) {
                Text(
                    track.title,
                    color = textColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(track.artist)
                        if (track.album.isNotBlank()) append(" \u00b7 ${track.album}")
                    },
                    color = mutedColor,
                    fontSize = MuseDesign.FontCaption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (track.durationMs > 0L) {
                Text(track.formattedDuration, color = mutedColor, fontSize = MuseDesign.FontMicro)
                Spacer(Modifier.width(8.dp))
            }
            IconButton(
                onClick = { onOpenComments(track) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = if (isChinese) "\u67e5\u770b\u8bc4\u8bba" else "View comments",
                    tint = mutedColor,
                    modifier = Modifier.size(19.dp)
                )
            }
            Icon(
                Icons.Default.PlayArrow,
                if (isChinese) "\u64ad\u653e ${track.title}" else "Play ${track.title}",
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 86.dp, end = MuseDesign.PagePadding),
            color = mutedColor.copy(alpha = .13f)
        )
    }
}

@Composable
private fun OnlineModeLoginCardV2(
    onLogin: () -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    backdrop: Backdrop?,
    isChinese: Boolean
) {
    val shape = RoundedCornerShape(MuseDesign.RadiusCard)
    Row(
        modifier = Modifier
            .padding(horizontal = MuseDesign.PagePadding, vertical = MuseDesign.Spacing20)
            .fillMaxWidth()
            .museGlass(
                backdrop = backdrop,
                shape = shape,
                tint = MaterialTheme.colorScheme.surface.copy(alpha = .17f),
                blurRadius = 18.dp,
                borderColor = Color.White.copy(alpha = .10f)
            )
            .clip(shape)
            .museLiquidCardPress(onClick = onLogin)
            .padding(MuseDesign.Spacing16),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(MuseDesign.RadiusStandard))
                .background(accentColor.copy(alpha = .16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null, tint = accentColor)
        }
        Column(Modifier.weight(1f).padding(start = MuseDesign.Spacing12)) {
            Text(
                if (isChinese) "\u767b\u5f55\u7f51\u6613\u4e91\u97f3\u4e50" else "Sign in to NetEase Music",
                color = textColor,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (isChinese) "\u540c\u6b65\u4f60\u7684\u63a8\u8350\u3001\u6536\u85cf\u548c\u64ad\u653e\u8bb0\u5f55" else "Sync recommendations, likes, and listening history.",
                color = mutedColor,
                fontSize = MuseDesign.FontCaption,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Text(
            if (isChinese) "\u767b\u5f55" else "Sign in",
            color = accentColor,
            fontSize = MuseDesign.FontCaption,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OnlineSectionHeaderV2(title: String, supporting: String, textColor: Color, mutedColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MuseDesign.PagePadding,
                top = MuseDesign.Spacing24,
                end = MuseDesign.PagePadding,
                bottom = MuseDesign.Spacing8
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(supporting, color = mutedColor, fontSize = MuseDesign.FontCaption, maxLines = 1)
    }
}

@Composable
private fun OnlineSyncingIndicatorV2(accentColor: Color, mutedColor: Color, isChinese: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 42.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = accentColor, strokeWidth = 2.dp)
        Spacer(Modifier.width(MuseDesign.Spacing12))
        Text(
            if (isChinese) "\u6b63\u5728\u540c\u6b65" else "Syncing",
            color = mutedColor,
            fontSize = MuseDesign.FontCaption
        )
    }
}

@Composable
private fun OnlineEmptyStateV2(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    textColor: Color,
    mutedColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MuseDesign.Spacing8)
    ) {
        Icon(icon, contentDescription = null, tint = mutedColor.copy(alpha = .72f), modifier = Modifier.size(30.dp))
        Text(title, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(detail, color = mutedColor, fontSize = MuseDesign.FontCaption)
    }
}

@Composable
private fun OnlineStatusMessageV2(
    message: String,
    textColor: Color,
    mutedColor: Color,
    backdrop: Backdrop?,
    isChinese: Boolean
) {
    val shape = RoundedCornerShape(MuseDesign.RadiusStandard)
    Row(
        modifier = Modifier
            .padding(
                start = MuseDesign.PagePadding,
                top = MuseDesign.Spacing16,
                end = MuseDesign.PagePadding
            )
            .fillMaxWidth()
            .museGlass(
                backdrop = backdrop,
                shape = shape,
                tint = MaterialTheme.colorScheme.surface.copy(alpha = .14f),
                blurRadius = 14.dp,
                borderColor = MaterialTheme.colorScheme.error.copy(alpha = .24f)
            )
            .clip(shape)
            .padding(MuseDesign.Spacing12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = MuseDesign.Spacing8)) {
            Text(
                if (isChinese) "\u6682\u65f6\u65e0\u6cd5\u52a0\u8f7d" else "Could not load online music",
                color = textColor,
                fontSize = MuseDesign.FontCaption,
                fontWeight = FontWeight.SemiBold
            )
            Text(message, color = mutedColor, fontSize = MuseDesign.FontMicro, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NeteaseArtwork(url: String?, description: String, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(
            painterResource(R.drawable.ic_apple_music),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .42f),
            modifier = Modifier.size(24.dp)
        )
        val normalized = com.caipan.music.online.neteaseImageRequestUrl(url, 360)
        if (normalized != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(normalized)
                    .crossfade(true)
                    .build(),
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
