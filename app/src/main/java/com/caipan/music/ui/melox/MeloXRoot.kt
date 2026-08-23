/*
 * MeloX 根宿主
 *
 * Ported from NEORUAA/Mei_MeloX_Android（MainActivity.kt 的底部控制架构）：
 * glassBackdrop(页面玻璃取样) / bottomBackdrop(页面内容记录层) /
 * bottomControlsBackdrop(两者合成，供底栏/搜索/迷你播放器取样)；滚动收起时
 * 底栏收缩为单图标胶囊、搜索按钮 64→48dp。页面为 首页/发现/音乐库。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.caipan.music.data.Playlist
import com.caipan.music.model.Song
import com.caipan.music.online.OnlineTrack
import com.caipan.music.online.RemotePlaylistDetail
import com.caipan.music.online.RemotePlaylistSummary
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** 底部标签；与上游 Index/Home、FindMusic、Library、Settings 对应。 */
enum class MeloXTab(val label: String, val symbol: SfSymbol) {
    Home("首页", SfSymbol.House),
    Explore("发现", SfSymbol.Safari),
    Library("音乐库", SfSymbol.MusicNoteList),
    Profile("我的", SfSymbol.PersonFilled),
}

@Composable
fun MeloXRoot(
    dark: Boolean,
    songs: List<Song>,
    playlists: List<Playlist>,
    onlineLikedSongs: List<OnlineTrack>,
    onlinePlaylists: List<RemotePlaylistSummary>,
    repeatCountsBySongId: Map<Long, Int>,
    profileName: String,
    profileAvatar: Uri?,
    onlineActive: Boolean,
    onlineHomeContent: MeloXOnlineHomeContent?,
    onlinePlaylistDetail: RemotePlaylistDetail?,
    currentSongId: Long?,
    isPlaying: Boolean,
    hasNext: Boolean,
    isLoading: Boolean,
    onlineSearchEnabled: Boolean,
    onPlayFromQueue: (List<Song>, Int) -> Unit,
    onPlaySong: (Song) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOnlineSearch: () -> Unit,
    onOpenLocalMusic: () -> Unit,
    onOpenPlaylistDetail: (Playlist) -> Unit,
    onOpenOnlinePlaylist: (RemotePlaylistSummary) -> Unit,
    onDismissOnlinePlaylist: () -> Unit,
    onPlayOnlineTracks: (List<OnlineTrack>, OnlineTrack) -> Unit,
    onSongMore: (Song) -> Unit,
    onDailyMix: () -> Unit,
    onHotSongs: () -> Unit,
    onHeartMode: (() -> Unit)?,
    miniSong: Song?,
    miniArtworkUri: Uri?,
    onMiniArtworkBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)?,
    playerExpanded: Boolean,
    onPlayerExpandedChange: (Boolean) -> Unit,
    settingsState: MeloXSettingsState? = null,
    settingsActions: MeloXSettingsActions? = null,
    pluginContent: (@Composable (bottomPadding: androidx.compose.ui.unit.Dp, onBack: () -> Unit) -> Unit)? = null,
    aboutContent: (@Composable (bottomPadding: androidx.compose.ui.unit.Dp, onBack: () -> Unit) -> Unit)? = null,
    moreSettingsContent: (@Composable (bottomPadding: androidx.compose.ui.unit.Dp, onBack: () -> Unit) -> Unit)? = null,
    playerHost: (@Composable (
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        compactNavigationProgress: Float,
        backdrop: Backdrop,
        onProgressChange: (Float) -> Unit,
    ) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(MeloXTab.Home.name) }
    var tabBarVisible by rememberSaveable { mutableStateOf(true) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var pluginsOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    var moreSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var playerSheetProgress by remember { mutableFloatStateOf(if (playerExpanded) 1f else 0f) }

    val glassColors = remember(dark) { defaultGlassColors(isDark = dark) }
    val glassBackdrop = rememberLayerBackdrop()
    // Keep the base page backdrop and the bottom controls' sample layer separate.
    val bottomBackdrop = rememberLayerBackdrop()
    val bottomControlsBackdrop = rememberCombinedBackdrop(glassBackdrop, bottomBackdrop)

    // 滚动收起：向下滚动收起底栏，向上滚动恢复（阈值与上游一致 ±18f 累积）
    val scrollCollapseConnection = remember {
        meloXNavCollapseConnection(
            isVisible = { tabBarVisible },
            setVisible = { tabBarVisible = it },
        )
    }

    val compactNavigationProgress by animateFloatAsState(
        targetValue = if (!tabBarVisible) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "CompactBottomNavigation",
    )

    val currentTab = runCatching { MeloXTab.valueOf(selectedTab) }.getOrDefault(MeloXTab.Home)

    fun selectTab(tab: MeloXTab) {
        // A detail page owns the page body. Clear it before changing tabs so
        // tab taps never appear inert behind the detail route.
        if (onlinePlaylistDetail != null) onDismissOnlinePlaylist()
        settingsOpen = false
        pluginsOpen = false
        aboutOpen = false
        moreSettingsOpen = false
        when (tab) {
            else -> {
                tabBarVisible = true
                selectedTab = tab.name
            }
        }
    }

    CompositionLocalProvider(
        LocalGlassColors provides glassColors,
        LocalGlassBackdrop provides glassBackdrop,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .nestedScroll(scrollCollapseConnection)
        ) {
            // MainActivity.MainGlassBackdrop(): page-level glass first samples
            // this stable grouped background, never an empty LayerBackdrop.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(glassColors.groupedBackground)
                    .layerBackdrop(glassBackdrop),
            )
            // 页面内容记录层：底栏玻璃对该层取景折射
            Box(Modifier.fillMaxSize().layerBackdrop(bottomBackdrop)) {
                val detail = onlinePlaylistDetail
                if (pluginsOpen && pluginContent != null) {
                    pluginContent(meloXBottomPadding()) { pluginsOpen = false }
                } else if (aboutOpen && aboutContent != null) {
                    aboutContent(meloXBottomPadding()) { aboutOpen = false }
                } else if (moreSettingsOpen && moreSettingsContent != null) {
                    moreSettingsContent(meloXBottomPadding()) { moreSettingsOpen = false }
                } else if (settingsOpen && settingsState != null && settingsActions != null) {
                    MeloXSettingsScreen(
                        state = settingsState,
                        actions = settingsActions.copy(
                            onOpenPlugins = {
                                if (pluginContent != null) pluginsOpen = true
                                else settingsActions.onOpenPlugins()
                            },
                            onOpenAbout = {
                                if (aboutContent != null) aboutOpen = true
                                else settingsActions.onOpenAbout()
                            },
                            onOpenUiSettings = {
                                if (moreSettingsContent != null) moreSettingsOpen = true
                                else settingsActions.onOpenUiSettings()
                            },
                        ),
                        bottomPadding = meloXBottomPadding(),
                        onNavigateBack = { settingsOpen = false },
                    )
                } else if (detail != null) {
                    MeloXOnlinePlaylistDetailScreen(
                        detail = detail,
                        bottomPadding = meloXBottomPadding(),
                        onDismiss = onDismissOnlinePlaylist,
                        onPlay = onPlayOnlineTracks,
                    )
                } else {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(200)) },
                        label = "melox-root-tabs",
                    ) { tab ->
                        // Online mode must never fall back to device songs or local playlists.
                        val pageSongs = if (onlineActive) emptyList() else songs
                        val pagePlaylists = if (onlineActive) emptyList() else playlists
                        when (tab) {
                        MeloXTab.Home -> MeloXHomeScreen(
                            title = "首页",
                            greetingTitle = meloXHomeGreeting(),
                            recommendCards = meloXRecommendCards(
                                pageSongs, repeatCountsBySongId, pagePlaylists,
                                onlineActive, onlineHomeContent,
                                onDailyMix, onHotSongs, onHeartMode,
                                onPlayFromQueue,
                            ),
                            playlistSections = meloXHomeSections(
                                pageSongs, pagePlaylists, onlineActive, onlineHomeContent,
                                onOpenPlaylistDetail, onOpenOnlinePlaylist, onPlayOnlineTracks,
                                onPlayFromQueue,
                            ),
                            trackSections = meloXHomeTrackSections(
                                pageSongs,
                                onlineActive,
                                onlineHomeContent,
                                onPlayFromQueue,
                                onPlayOnlineTracks,
                            ),
                            bottomPadding = meloXBottomPadding(),
                        )
                        MeloXTab.Explore -> MeloXExploreScreen(
                            onlineHome = onlineHomeContent,
                            playlists = pagePlaylists,
                            bottomPadding = meloXBottomPadding(),
                            onOpenOnlinePlaylist = onOpenOnlinePlaylist,
                            onOpenPlaylistDetail = onOpenPlaylistDetail,
                        )
                        MeloXTab.Library -> MeloXLibraryScreen(
                            songs = pageSongs,
                            playlists = pagePlaylists,
                            currentSongId = currentSongId,
                            isLoading = isLoading,
                            bottomPadding = meloXBottomPadding(),
                            onPlayFromQueue = { queue, index ->
                                onPlayFromQueue(queue, index)
                                onOpenPlayer()
                            },
                            onHeartMode = onHeartMode,
                            onPlaylistClick = onOpenPlaylistDetail,
                            onSongMore = onSongMore,
                            onlineTracks = onlineLikedSongs,
                            onlinePlaylistSummaries = onlinePlaylists,
                            onPlayOnlineTracks = onPlayOnlineTracks,
                            onOpenOnlinePlaylist = onOpenOnlinePlaylist,
                        )
                            MeloXTab.Profile -> MeloXProfileScreen(
                            profileName = profileName,
                            profileAvatar = profileAvatar,
                            bottomPadding = meloXBottomPadding(),
                            onOpenSettings = {
                                if (settingsState != null && settingsActions != null) {
                                    settingsOpen = true
                                } else {
                                    onOpenSettings()
                                }
                            },
                            )
                        }
                    }
                }
            }

            // The original BottomSheetPlayer keeps collapsed and expanded
            // player content under one host. Keep that host alive above pages
            // rather than mounting a separate full-screen route.
            if (miniSong != null && playerHost != null) {
                CompositionLocalProvider(LocalGlassBackdrop provides bottomControlsBackdrop) {
                    Box(Modifier.fillMaxSize().zIndex(10f)) {
                        playerHost(
                            playerExpanded,
                            onPlayerExpandedChange,
                            compactNavigationProgress,
                            bottomControlsBackdrop,
                            { playerSheetProgress = it },
                        )
                    }
                }
            }

            // ── 迷你播放器 + 底栏 + 搜索按钮 ──
            // Match MainActivity.kt: the navigation row remains composed while
            // the sheet expands, then moves below the viewport with progress.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .offset(y = (NavigationBarHeight + NavigationBarBottomMargin) * playerSheetProgress)
                    .zIndex(12f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (miniSong != null && playerHost == null) {
                    MeloXMiniPlayer(
                        song = miniSong,
                        isPlaying = isPlaying,
                        compactProgress = compactNavigationProgress,
                        modifier = Modifier.offset(y = (NavigationBarHeight - 16.dp) * compactNavigationProgress),
                        backdrop = bottomControlsBackdrop,
                        artworkUri = miniArtworkUri,
                        hasNext = hasNext,
                        onArtworkBoundsChanged = onMiniArtworkBoundsChanged,
                        onClick = onOpenPlayer,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = meloXNavBarBottomPadding()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(LocalGlassBackdrop provides bottomControlsBackdrop) {
                        GlassBottomBar(
                            items = listOf(
                                MeloXTab.Home,
                                MeloXTab.Explore,
                                MeloXTab.Library,
                                MeloXTab.Profile,
                            ).map { tab ->
                                GlassTabItem(
                                    key = tab,
                                    label = tab.label,
                                    symbol = tab.symbol,
                                )
                            },
                            selectedKey = currentTab,
                            onExpand = { tabBarVisible = true },
                            onSelected = ::selectTab,
                            backdrop = bottomControlsBackdrop,
                            compactProgress = compactNavigationProgress,
                            compactSize = MiniPlayerHeight,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.size(width = 8.dp, height = 1.dp))
                    CompositionLocalProvider(LocalGlassBackdrop provides bottomControlsBackdrop) {
                        Box(
                            modifier = Modifier.size(64.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            GlassIconButton(
                                onClick = {
                                    if (onlineSearchEnabled) onOpenOnlineSearch() else onOpenLocalMusic()
                                },
                                backdrop = bottomControlsBackdrop,
                                style = GlassSurfaceStyle.Navigation,
                                modifier = Modifier.size(64.dp - 16.dp * compactNavigationProgress),
                            ) {
                                SfIcon(
                                    SfSymbol.Search,
                                    "搜索",
                                    tint = LocalGlassColors.current.content,
                                    size = 26.dp + (CompactBottomControlIconSize - 26.dp) * compactNavigationProgress,
                                    weight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun meloXBottomPadding(): androidx.compose.ui.unit.Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        NavigationBarHeight + MiniPlayerHeight + NavigationBarBottomMargin

@Composable
private fun meloXNavBarBottomPadding(): androidx.compose.ui.unit.Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + NavigationBarBottomMargin

/** 与上游一致：滚动累积 ±18f 触发底栏收起/展开。 */
private fun meloXNavCollapseConnection(
    isVisible: () -> Boolean,
    setVisible: (Boolean) -> Unit,
): NestedScrollConnection = object : NestedScrollConnection {
    private var accumulator = 0f

    override fun onPreScroll(
        available: androidx.compose.ui.geometry.Offset,
        source: NestedScrollSource,
    ): androidx.compose.ui.geometry.Offset {
        if (source != NestedScrollSource.UserInput) return androidx.compose.ui.geometry.Offset.Zero
        if (available.y < 0f) {
            if (accumulator > 0f) accumulator = 0f
            accumulator += available.y
            if (accumulator <= -18f) {
                setVisible(false)
                accumulator = 0f
            }
        } else if (available.y > 0f) {
            if (accumulator < 0f) accumulator = 0f
            accumulator += available.y
            if (accumulator >= 18f) {
                setVisible(true)
                accumulator = 0f
            }
        }
        return androidx.compose.ui.geometry.Offset.Zero
    }
}
