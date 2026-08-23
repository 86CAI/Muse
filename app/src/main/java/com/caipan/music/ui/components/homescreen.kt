/*
 * Muse non-player home UI. Library-first: all content comes from the device or configured WebDAV.
 *
 * 页面结构最初 adapted from zyrouge/Symphony (AGPL-3.0-only) 的
 * `ui/view/Home.kt`、`ui/view/home/Songs.kt`、`ui/view/home/Playlists.kt`：
 * 列表优先的媒体库分区、Songs/Library 导航切换、播放/随机播放动作。
 * 后续 Muse 移除了 Symphony 的 Scaffold/TopAppBar/NavigationBar，改用自有玻璃容器，
 * 数据全部来自 Muse 的 MusicViewModel。
 *
 * Upstream: https://github.com/zyrouge/symphony @ dd04b872b8b4e6dd56172c053a5776c4d56ad080
 * License: GNU Affero General Public License v3.0 only —— 见 licenses/AGPL-3.0.txt
 */
package com.caipan.music.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.caipan.music.R
import com.caipan.music.model.Song
import com.caipan.music.ui.theme.MuseDesign
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay

/** 入场交错动画：首次组合时子项依次淡入上移。animatedKeys 用于跨重组记忆已播放的项，避免滚动回收后重复播放。 */
@Composable
private fun staggeredEnterModifier(index: Int, baseDelay: Int = 40, animKey: String, animatedKeys: MutableSet<String>): Modifier {
    val alreadyDone = animKey in animatedKeys
    val progress = remember(animKey) { Animatable(if (alreadyDone) 1f else 0f) }
    LaunchedEffect(animKey) {
        if (!alreadyDone) {
            animatedKeys.add(animKey)
            delay((index * baseDelay).toLong().coerceAtMost(400L))
            progress.animateTo(1f, spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy))
        }
    }
    return Modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 36.dp.toPx()
    }
}

@Composable
fun HomeScreen(
    accentColor: Color = MuseDesign.Red, isLightTheme: Boolean = false,
    isMonetStyle: Boolean = false,
    contentText: Color? = null,
    contentMuted: Color? = null,
    onPlaylistsTap: () -> Unit, onLocalMusicTap: () -> Unit, onSettingsTap: () -> Unit,
    onWebdavTap: () -> Unit = {}, onPickAvatar: () -> Unit = {}, onProfileNameChange: (String) -> Unit = {},
    recentSongs: List<Song> = emptyList(), allSongs: List<Song> = recentSongs,
    profileName: String = "Muse 用户", profileAvatar: Uri? = null,
    listeningTimeMs: Long = 0, completedPlays: Int = 0, repeatCount: Int = 0,
    onRecentSongTap: (Song) -> Unit = {},
    currentSong: Song? = null, isPlaying: Boolean = false, onPlayPause: () -> Unit = {},
    onTapPlayer: () -> Unit = {}, backdrop: Backdrop? = null,
    onlineSearchEnabled: Boolean = false, onOnlineSearchTap: () -> Unit = {},
    selectedTab: Int = 0, onSelectedTabChanged: ((Int) -> Unit)? = null,
    isLoading: Boolean = false,
    onLocalMusicBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    onPlaylistsBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    onWebdavBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    bottomBarBackdrop: com.kyant.backdrop.Backdrop? = null
) {
    var currentTab by rememberSaveable { mutableIntStateOf(selectedTab) }
    LaunchedEffect(selectedTab) { if (selectedTab != currentTab) currentTab = selectedTab }
    val nowListeningPainter = painterResource(R.drawable.ic_apple_play)
    val libraryTabPainter = painterResource(R.drawable.ic_apple_library)
    val profileTabPainter = painterResource(R.drawable.ic_apple_user)
    val tabs = remember {
        listOf(
            MuseLiquidTab("现在听", Icons.Default.PlayCircle, nowListeningPainter),
            MuseLiquidTab("资料库", Icons.Default.LibraryMusic, libraryTabPainter),
            MuseLiquidTab("个人", Icons.Default.Person, profileTabPainter)
        )
    }
    val surface = MaterialTheme.colorScheme.surface
    val glassTint = surface
    // 背景感知文字色：壁纸/视频背景下由 MainScreen 传入动态对比色，无背景时跟随主题
    val text = contentText ?: MaterialTheme.colorScheme.onBackground
    val muted = contentMuted ?: MaterialTheme.colorScheme.onSurfaceVariant
    // MainScreen's backdrop records only the wallpaper/video layer. Record the
    // HomeScreen content separately so the floating bottom lens can refract
    // controls and cards that continue underneath it.
    val contentBackdrop = rememberLayerBackdrop()
    val pageBackdrop = bottomBarBackdrop ?: backdrop
    val bottomTabsBackdrop = if (pageBackdrop != null) {
        rememberCombinedBackdrop(pageBackdrop, contentBackdrop)
    } else {
        contentBackdrop
    }
    // Keep the page and dock in one full-screen stack. Material Scaffold
    // measures its body above bottomBar, leaving no recorded pixels beneath
    // the dock for a backdrop to sample. Liquid Glass records the full page
    // first and draws the dock over it instead.
    Box(Modifier.fillMaxSize()) {
        // 内容区延伸到屏幕底部（不占 bottomBar 位置），悬浮药丸才能透出滚动的页面内容
        val statusBarInset = WindowInsets.statusBars.asPaddingValues()
        val contentInset = PaddingValues(
            top = statusBarInset.calculateTopPadding(),
            bottom = 0.dp
        )
        CompositionLocalProvider(LocalMuseLiquidGlass provides isMonetStyle) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop)
        ) {
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                // 根据 tab 索引决定滑动方向
                if (targetState > initialState) {
                    slideInHorizontally(
                        animationSpec = tween(MuseDesign.DurationNormal, easing = FastOutSlowInEasing),
                        initialOffsetX = { it }
                    ) + fadeIn(tween(MuseDesign.DurationNormal)) togetherWith
                    slideOutHorizontally(
                        animationSpec = tween(MuseDesign.DurationNormal, easing = FastOutSlowInEasing),
                        targetOffsetX = { -it }
                    ) + fadeOut(tween(MuseDesign.DurationFast))
                } else {
                    slideInHorizontally(
                        animationSpec = tween(MuseDesign.DurationNormal, easing = FastOutSlowInEasing),
                        initialOffsetX = { -it }
                    ) + fadeIn(tween(MuseDesign.DurationNormal)) togetherWith
                    slideOutHorizontally(
                        animationSpec = tween(MuseDesign.DurationNormal, easing = FastOutSlowInEasing),
                        targetOffsetX = { it }
                    ) + fadeOut(tween(MuseDesign.DurationFast))
                }
            },
            label = "homeTab"
        ) { tab ->
        when (tab) {
            0 -> NowListening(contentInset, recentSongs, currentSong, isPlaying, onRecentSongTap, onPlayPause, onTapPlayer, onLocalMusicTap, onPlaylistsTap, text, muted, glassTint, accentColor, backdrop, isLoading)
            1 -> UnifiedLibrary(contentInset, allSongs, onRecentSongTap, onLocalMusicTap, onPlaylistsTap,
                onWebdavTap, onlineSearchEnabled, onOnlineSearchTap, text, muted, glassTint, accentColor, backdrop,
                profileAvatar = profileAvatar, profileName = profileName)
            else -> ProfileVisuals(
                inset = contentInset,
                name = profileName,
                avatar = profileAvatar,
                listeningTimeMs = listeningTimeMs,
                completedPlays = completedPlays,
                repeatCount = repeatCount,
                songs = allSongs,
                onSong = onRecentSongTap,
                pickAvatar = onPickAvatar,
                changeName = onProfileNameChange,
                settings = onSettingsTap,
                accent = accentColor,
                isLightTheme = isLightTheme,
                backdrop = backdrop
            )
        }
        }
        }

        MuseLiquidBottomTabs(
            selectedIndex = currentTab,
            onSelected = { currentTab = it; onSelectedTabChanged?.invoke(it) },
            tabs = tabs,
            backdrop = bottomTabsBackdrop,
            accentColor = accentColor,
            contentColor = text,
            isLightTheme = isLightTheme,
            liquidGlass = isMonetStyle,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String? = null, action: (() -> Unit)? = null, text: Color, muted: Color) {
    Row(Modifier.fillMaxWidth().padding(start = MuseDesign.PagePadding, end = MuseDesign.CompactPadding, top = MuseDesign.Spacing16, bottom = MuseDesign.Spacing12), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, color = text, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = muted, fontSize = 13.sp, modifier = Modifier.padding(top = MuseDesign.Spacing4)) }
        }
        action?.let { MuseIconButton(it) { Icon(painterResource(R.drawable.ic_apple_user), "个人资料", tint = muted, modifier = Modifier.size(29.dp)) } }
    }
}
@Composable private fun Page(modifier: Modifier, content: LazyListScope.() -> Unit) { LazyColumn(modifier, contentPadding = PaddingValues(bottom = 112.dp), content = content) }
@Composable private fun NowListening(inset: PaddingValues, songs: List<Song>, current: Song?, playing: Boolean, onSong: (Song) -> Unit, playPause: () -> Unit, tapPlayer: () -> Unit, local: () -> Unit, playlists: () -> Unit, text: Color, muted: Color, surface: Color, accent: Color, backdrop: Backdrop?, isLoading: Boolean) {
    val recent = remember(songs) { songs.take(12) }
    val animatedKeys = remember { mutableSetOf<String>() }
    Page(Modifier.fillMaxSize().padding(inset)) {
        item { Header("现在听", "你的音乐，按你的方式播放", null, text, muted) }
        if (current != null) item(key = "now-playing") { Column(staggeredEnterModifier(0, animKey = "np", animatedKeys = animatedKeys)) { Section("正在播放", text, accent); NowPlaying(current, playing, playPause, tapPlayer, text, muted, surface, accent, backdrop) } }
        item(key = "recent-header") { Column(staggeredEnterModifier(1, animKey = "rh", animatedKeys = animatedKeys)) { Section("最近添加", text, accent, "查看全部", local) } }
        if (songs.isEmpty()) {
            if (isLoading) item(key = "loading") { Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = accent); Text("正在扫描音乐…", color = muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) } }
            else item(key = "empty") { Column(staggeredEnterModifier(2, animKey = "empty", animatedKeys = animatedKeys)) { Empty(local, text, muted, accent) } }
        } else item(key = "recent-row") { Column(staggeredEnterModifier(2, animKey = "rr", animatedKeys = animatedKeys)) { AlbumRow(recent, onSong, text, muted) } }
        item(key = "access-header") { Column(staggeredEnterModifier(3, animKey = "ah", animatedKeys = animatedKeys)) { Section("快速访问", text, accent) } }
        item(key = "access-card") { Column(staggeredEnterModifier(4, animKey = "ac", animatedKeys = animatedKeys)) { Row(Modifier.padding(horizontal = MuseDesign.PagePadding)) { AccessCard("本地歌曲", painterResource(R.drawable.ic_apple_music), local, surface, text, accent, Modifier.fillMaxWidth(), backdrop) } } }
    }
}
@Composable private fun UnifiedLibrary(inset: PaddingValues, songs: List<Song>, onSong: (Song) -> Unit, local: () -> Unit, playlists: () -> Unit, webdav: () -> Unit, onlineSearchEnabled: Boolean, onlineSearch: () -> Unit, text: Color, muted: Color, surface: Color, accent: Color, backdrop: Backdrop?, profileAvatar: Uri? = null, profileName: String = "Muse 用户") {
    var query by rememberSaveable { mutableStateOf("") }
    val uniqueSongs = remember(songs) { songs.distinctBy { it.id } }
    val results = remember(query, uniqueSongs) { if (query.isBlank()) emptyList() else uniqueSongs.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) } }
    val artists = remember(uniqueSongs) { uniqueSongs.map { it.artist.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sorted() }
    val animatedKeys = remember { mutableSetOf<String>() }
    val recentAlbums = remember(uniqueSongs) { uniqueSongs.takeLast(5) }
    Page(Modifier.fillMaxSize().padding(inset)) {
        // ── Apple 风格大标题 + 头像 ──
        item(key = "lib-header") {
            Row(
                Modifier.fillMaxWidth().padding(start = MuseDesign.PagePadding, end = MuseDesign.CompactPadding, top = MuseDesign.Spacing8, bottom = MuseDesign.Spacing12),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("资料库", color = text, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    if (profileAvatar != null) {
                        AsyncImage(profileAvatar, "头像", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(
                            profileName.takeIf { it.isNotBlank() }?.firstOrNull()?.toString() ?: "M",
                            color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        // ── 搜索栏 ──
        item(key = "lib-search") {
            OutlinedTextField(
                query, { query = it },
                Modifier.fillMaxWidth().padding(horizontal = MuseDesign.PagePadding)
                    .museGlass(backdrop, RoundedCornerShape(MuseDesign.RadiusStandard), surface.copy(alpha = .42f)),
                placeholder = { Text("搜索歌曲、艺术家或专辑") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_apple_search), null) },
                trailingIcon = { if (query.isNotEmpty()) MuseIconButton({ query = "" }) { Icon(painterResource(R.drawable.ic_apple_clear), "清除") } },
                singleLine = true,
                shape = RoundedCornerShape(MuseDesign.RadiusStandard),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent, unfocusedBorderColor = muted.copy(alpha = .22f),
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    cursorColor = accent
                )
            )
        }
        if (query.isNotBlank()) {
            // ── 搜索结果 ──
            if (results.isEmpty()) item(key = "no-result") { Text("没有找到“$query”", color = muted, modifier = Modifier.padding(20.dp)) }
            else items(results.size, key = { results[it].id }) { i ->
                val song = results[i]
                Column(staggeredEnterModifier(i, 24, "search-${song.id}", animatedKeys)) { SongResult(song, { onSong(song) }, text, muted, accent) }
            }
        } else {
            // ── 最近添加 — 横向滚动 ──
            if (recentAlbums.isNotEmpty()) {
                item(key = "lib-recent") {
                    Column(staggeredEnterModifier(0, animKey = "recent", animatedKeys = animatedKeys)) {
                        Section("最近添加", text, accent, "查看全部", local)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = MuseDesign.PagePadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recentAlbums.size, key = { recentAlbums[it].id }) { i ->
                                val song = recentAlbums[i]
                                Column(Modifier.width(130.dp).pressScale().clickable { onSong(song) }) {
                                    Box(Modifier.size(130.dp).clip(RoundedCornerShape(12.dp)).background(muted.copy(alpha = .16f))) {
                                        AlbumArtwork(song, Modifier.matchParentSize(), song.title)
                                    }
                                    Text(song.title, color = text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                    Text(song.artist, color = muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            // ── 快速访问 ──
            item(key = "lib-access") {
                Column(staggeredEnterModifier(2, animKey = "access", animatedKeys = animatedKeys)) {
                    Section("快速访问", text, accent)
                    Column(
                        Modifier.padding(horizontal = MuseDesign.PagePadding).fillMaxWidth()
                            .museGlass(backdrop, RoundedCornerShape(MuseDesign.RadiusCard), surface.copy(alpha = .38f))
                            .clip(RoundedCornerShape(MuseDesign.RadiusCard)).padding(vertical = 4.dp)
                    ) {
                        QuickAccessItem(painterResource(R.drawable.ic_apple_music), "歌曲 · ${uniqueSongs.size}", local, text, muted, accent, true)
                        if (onlineSearchEnabled) {
                            QuickAccessItem(painterResource(R.drawable.ic_apple_explore), "在线搜索", onlineSearch, text, muted, accent, true)
                        }
                        QuickAccessItem(painterResource(R.drawable.ic_apple_queue), "播放列表", playlists, text, muted, accent, true)
                        QuickAccessItem(painterResource(R.drawable.ic_apple_cloud), "WebDAV 音乐源", webdav, text, muted, accent, false)
                    }
                }
            }
            // ── 艺术家 ──
            if (artists.isNotEmpty()) {
                item(key = "lib-artists") {
                    Column(staggeredEnterModifier(3, animKey = "artists", animatedKeys = animatedKeys)) {
                        Section("艺术家 · ${artists.size}", text, accent)
                        LazyRow(contentPadding = PaddingValues(horizontal = MuseDesign.PagePadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(artists) { artist ->
                                AssistChip(onClick = { query = artist }, label = { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { Icon(painterResource(R.drawable.ic_apple_user), null, Modifier.size(18.dp), tint = accent) })
                            }
                        }
                    }
                }
            }
            // ── 全部歌曲 ──
            item(key = "lib-songs-header") {
                Column(staggeredEnterModifier(4, animKey = "songs-h", animatedKeys = animatedKeys)) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 24.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("全部歌曲", color = text, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Row(
                            Modifier.clickable { local() }.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(painterResource(R.drawable.ic_apple_sort), null, tint = accent, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("排序", color = accent, fontSize = 13.sp)
                        }
                    }
                }
            }
            // 歌曲列表（取前 20 首预览）
            val previewSongs = uniqueSongs.take(20)
            items(previewSongs.size, key = { previewSongs[it].id }) { i ->
                val song = previewSongs[i]
                Column(staggeredEnterModifier(i + 5, 24, "song-${song.id}", animatedKeys)) {
                    SongResult(song, { onSong(song) }, text, muted, accent)
                    if (i < previewSongs.size - 1) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = muted.copy(alpha = .12f))
                }
            }
        }
    }
}

@Composable private fun ProfileTab(inset: PaddingValues, name: String, avatar: Uri?, listeningTimeMs: Long, completedPlays: Int, repeatCount: Int, songCount: Int, pickAvatar: () -> Unit, changeName: (String) -> Unit, settings: () -> Unit, text: Color, muted: Color, surface: Color, accent: Color, backdrop: Backdrop?) {
    val hours = listeningTimeMs / 3_600_000
    val minutes = listeningTimeMs / 60_000 % 60
    var editingName by rememberSaveable { mutableStateOf(false) }
    var draftName by remember(name) { mutableStateOf(name) }
    Page(Modifier.fillMaxSize().padding(inset)) {
        item { Row(Modifier.fillMaxWidth().padding(start = MuseDesign.PagePadding, end = MuseDesign.CompactPadding, top = MuseDesign.Spacing16, bottom = MuseDesign.Spacing16), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("个人", color = text, fontSize = 34.sp, fontWeight = FontWeight.Bold); Text("你的聆听轨迹", color = muted, fontSize = 13.sp) }; MuseIconButton(settings) { Icon(painterResource(R.drawable.ic_apple_settings), "设置", tint = accent, modifier = Modifier.size(27.dp)) } } }
        item {
            Column(
                Modifier.padding(horizontal = MuseDesign.PagePadding).fillMaxWidth()
                    .museGlass(backdrop, RoundedCornerShape(MuseDesign.RadiusFloating), surface.copy(.65f))
                    .clip(RoundedCornerShape(MuseDesign.RadiusFloating)).padding(MuseDesign.Spacing16)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(76.dp).clip(androidx.compose.foundation.shape.CircleShape).background(accent.copy(.15f)).clickable(onClick = pickAvatar), contentAlignment = Alignment.Center) {
                        if (avatar != null) AsyncImage(avatar, "头像", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                        else Icon(painterResource(R.drawable.ic_apple_user), null, tint = accent, modifier = Modifier.size(42.dp))
                    }
                    Column(Modifier.padding(start = 16.dp).weight(1f).clickable { editingName = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, color = text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(painterResource(R.drawable.ic_apple_edit), "修改名字", tint = accent, modifier = Modifier.size(20.dp))
                        }
                        Text("点击名字修改 · 点击头像更换", color = muted, fontSize = 12.sp)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = MuseDesign.Spacing16), color = muted.copy(alpha = .16f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("聆听速写", color = text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("把这段时间留给音乐", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Icon(painterResource(R.drawable.ic_apple_headphones), null, tint = accent, modifier = Modifier.size(27.dp))
                }
                Spacer(Modifier.height(MuseDesign.Spacing12))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MuseDesign.Spacing8)) {
                    ProfileStat("${hours}h ${minutes}m", "聆听时间", Modifier.weight(1f), muted, accent)
                    ProfileStat(completedPlays.toString(), "完整播放", Modifier.weight(1f), muted, accent)
                }
                Spacer(Modifier.height(MuseDesign.Spacing8))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MuseDesign.Spacing8)) {
                    ProfileStat(repeatCount.toString(), "循环时刻", Modifier.weight(1f), muted, accent)
                    ProfileStat(songCount.toString(), "私人曲库", Modifier.weight(1f), muted, accent)
                }
            }
        }
    }
    if (editingName) MuseAlertDialog(onDismissRequest = { editingName = false }, title = { Text("修改个人名字") }, text = { OutlinedTextField(draftName, { draftName = it.take(24) }, singleLine = true, label = { Text("名字") }) }, confirmButton = { MuseTextButton({ changeName(draftName); editingName = false }) { Text("保存", color = accent) } }, dismissButton = { MuseTextButton({ editingName = false }) { Text("取消") } }, shape = RoundedCornerShape(24.dp))
}

@Composable private fun ProfileStat(value: String, label: String, modifier: Modifier, muted: Color, accent: Color) { Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .055f)).padding(vertical = 14.dp, horizontal = 10.dp), horizontalAlignment = Alignment.Start) { Text(value, color = accent, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1); Text(label, color = muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1) } }
@Composable private fun Section(title: String, text: Color, accent: Color, action: String? = null, onAction: (() -> Unit)? = null) { Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, color = text, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); if (action != null && onAction != null) Text(action, color = accent, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onAction).padding(6.dp)) } }
@Composable private fun DividerLine(muted: Color) { HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = muted.copy(alpha = .18f)) }
@Composable private fun AlbumArt(song: Song, modifier: Modifier, muted: Color, contentDescription: String? = null) { Box(modifier.clip(RoundedCornerShape(10.dp)).background(muted.copy(alpha = .16f)), contentAlignment = Alignment.Center) { AlbumArtwork(song, Modifier.matchParentSize(), contentDescription) } }
@Composable private fun AlbumRow(songs: List<Song>, onSong: (Song) -> Unit, text: Color, muted: Color, width: Dp = 150.dp, label: (Song) -> String = { it.title }) {
    val distinct = remember(songs) { songs.distinctBy { it.id } }
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(distinct.size, key = { distinct[it].id }) { i ->
            val song = distinct[i]
            Column(Modifier.width(width).pressScale().clickable { onSong(song) }) {
                AlbumArt(song, Modifier.fillMaxWidth().aspectRatio(1f), muted, label(song))
                Text(label(song), color = text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
                Text(song.artist, color = muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
@Composable private fun AlbumColumn(song: Song, onSong: (Song) -> Unit, text: Color, muted: Color, modifier: Modifier = Modifier) {
    val label = song.album.ifBlank { song.title }
    Column(modifier.clip(RoundedCornerShape(10.dp)).pressScale().clickable { onSong(song) }) {
        AlbumArt(song, Modifier.fillMaxWidth().aspectRatio(1f), muted, label)
        Text(label, color = text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(song.artist, color = muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
    }
}
@Composable private fun NowPlaying(song: Song, playing: Boolean, playPause: () -> Unit, tap: () -> Unit, text: Color, muted: Color, surface: Color, accent: Color, backdrop: Backdrop?) { val shape = RoundedCornerShape(18.dp); val playPainter = painterResource(R.drawable.ic_apple_play); val pausePainter = painterResource(R.drawable.ic_apple_pause); Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().museGlass(backdrop, shape, surface.copy(alpha = .18f), 20.dp).clip(shape).pressScale().clickable(onClick = tap).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { AlbumArt(song, Modifier.size(64.dp), muted, song.title); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(song.title, color = text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(song.artist, color = muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }; MuseIconButton(playPause) { Icon(if (playing) pausePainter else playPainter, "播放/暂停", tint = accent, modifier = Modifier.size(32.dp)) } } }
@Composable private fun AccessCard(title: String, icon: Painter, onClick: () -> Unit, surface: Color, text: Color, accent: Color, modifier: Modifier, backdrop: Backdrop?) { val shape = RoundedCornerShape(18.dp); Column(modifier.height(104.dp).museGlass(backdrop, shape, surface.copy(alpha = .16f), 20.dp).clip(shape).pressScale().clickable(onClick = onClick).padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) { Icon(icon, null, tint = accent); Text(title, color = text, fontWeight = FontWeight.SemiBold) } }
@Composable private fun BrowseCard(title: String, subtitle: String, icon: Painter, onClick: () -> Unit, surface: Color, text: Color, muted: Color, tint: Color, backdrop: Backdrop?) { val shape = RoundedCornerShape(14.dp); Row(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth().height(90.dp).museGlass(backdrop, shape, surface.copy(alpha = .16f), 20.dp).clip(shape).pressScale().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = tint, modifier = Modifier.size(32.dp)); Column(Modifier.padding(start = 15.dp)) { Text(title, color = text, fontWeight = FontWeight.Bold); Text(subtitle, color = muted, fontSize = 13.sp) } } }
@Composable private fun LibraryItem(icon: Painter, title: String, onClick: () -> Unit, text: Color, muted: Color, accent: Color) { val chev = painterResource(R.drawable.ic_apple_chevron_right); Column(Modifier.padding(horizontal = 20.dp)) { Row(Modifier.fillMaxWidth().pressScale().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp)); Text(title, color = text, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(start = 15.dp)); Icon(chev, null, tint = muted) }; DividerLine(muted) } }
@Composable private fun QuickAccessItem(icon: Painter, title: String, onClick: () -> Unit, text: Color, muted: Color, accent: Color, divider: Boolean) { val chev = painterResource(R.drawable.ic_apple_chevron_right); Column(Modifier.padding(horizontal = 16.dp)) { Row(Modifier.fillMaxWidth().pressScale().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp)); Text(title, color = text, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(start = 15.dp)); Icon(chev, null, tint = muted) }; if (divider) HorizontalDivider(color = muted.copy(alpha = .18f)) } }
@Composable private fun SongResult(song: Song, onClick: () -> Unit, text: Color, muted: Color, accent: Color) { val chev = painterResource(R.drawable.ic_apple_chevron_right); Row(Modifier.fillMaxWidth().pressScale().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { AlbumArt(song, Modifier.size(52.dp), muted, song.title); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(song.title, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${song.artist} · ${song.album}", color = muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Icon(chev, null, tint = muted) } }
@Composable private fun Empty(open: () -> Unit, text: Color, muted: Color, accent: Color) { Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(painterResource(R.drawable.ic_apple_library), null, tint = accent, modifier = Modifier.size(56.dp)); Text("这里还没有音乐", color = text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)); Text("扫描设备音频后会显示在这里", color = muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp)); MuseTextButton(open) { Text("打开媒体库", color = accent) } } }
