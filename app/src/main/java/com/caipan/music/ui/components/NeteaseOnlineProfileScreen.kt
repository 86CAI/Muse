package com.caipan.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.caipan.music.data.NeteaseSession
import com.caipan.music.online.NeteaseProfileDetails
import com.caipan.music.online.OnlineTrack
import com.caipan.music.online.RemotePlaylistSummary
import com.caipan.music.online.neteaseImageRequestUrl
import com.kyant.backdrop.Backdrop

/**
 * Online-mode profile inspired by the information hierarchy of NetEase Music's mobile profile.
 *
 * This deliberately does not share Muse's local [ProfileScreen]: identity comes from the
 * authenticated NetEase session, while every active surface is backed by a route callback.
 * Provider features that are not wired yet (podcasts and notes) are shown as passive labels
 * rather than tappable placeholders.
 */
@Composable
fun NeteaseOnlineProfileScreen(
    session: NeteaseSession?,
    avatarUrl: String?,
    nickname: String,
    likedTracks: List<OnlineTrack>,
    playlists: List<RemotePlaylistSummary>,
    recentTracks: List<OnlineTrack>,
    loading: Boolean,
    backdrop: Backdrop?,
    accentColor: Color,
    isLightTheme: Boolean,
    isChinese: Boolean,
    profileDetails: NeteaseProfileDetails? = null,
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenWebdav: () -> Unit,
    onPlaylist: (Long) -> Unit,
    onPlayLiked: () -> Unit,
    onLogin: (() -> Unit)? = null,
    onPlayRecent: ((OnlineTrack, List<OnlineTrack>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val signedIn = session?.isLoggedIn == true
    val visibleAvatar = profileDetails?.avatarUrl?.takeIf(String::isNotBlank)
        ?: avatarUrl?.takeIf(String::isNotBlank)
        ?: session?.avatarUrl?.takeIf(String::isNotBlank)
    val visibleName = profileDetails?.nickname?.takeIf(String::isNotBlank) ?: nickname.ifBlank {
        session?.nickname?.takeIf(String::isNotBlank)
            ?: if (isChinese) "网易云音乐用户" else "NetEase Music user"
    }
    val userId = session?.userId?.takeIf { it > 0L }
    val createdPlaylists = playlists.filter {
        userId != null && it.creatorUserId == userId && !it.subscribed
    }
    val collectedPlaylists = playlists.filterNot {
        userId != null && it.creatorUserId == userId && !it.subscribed
    }
    var collectionSection by rememberSaveable { mutableStateOf(NeteaseProfileCollection.Playlists) }
    val topText = Color.White.copy(alpha = .97f)
    val topMuted = Color.White.copy(alpha = .69f)
    // The profile artwork is intentionally allowed to remain visible behind the header.  The
    // music library is a separate, heavier surface – mirroring the actual NetEase hierarchy
    // instead of placing a stack of unrelated glass cards over a full-screen background.
    val panelColor = if (isLightTheme) Color(0xFF101014) else Color(0xFF0B0B0F)
    val panelText = Color(0xFFF8F7FA)
    val panelMuted = Color(0xFFA6A3AD)
    val glassTint = if (isLightTheme) Color.White.copy(alpha = .10f) else Color.White.copy(alpha = .075f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF243F40))
    ) {
        NeteaseProfileAmbientArtwork(
            artworkUrl = profileDetails?.backgroundUrl?.takeIf(String::isNotBlank) ?: visibleAvatar,
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFF244143).copy(alpha = .06f),
                        .36f to Color(0xFF183135).copy(alpha = .18f),
                        .58f to panelColor,
                        1f to panelColor
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 126.dp)
        ) {
            item(key = "netease-profile-hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    NeteaseProfileTopBar(
                        onDismiss = onDismiss,
                        onSearch = onSearch,
                        onSettings = onSettings,
                        tint = topText,
                        isChinese = isChinese
                    )
                    Spacer(Modifier.height(8.dp))
                    NeteaseProfileAvatar(
                        avatarUrl = visibleAvatar,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        fallbackTint = topText
                    )
                    Spacer(Modifier.height(11.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = visibleName,
                            color = topText,
                            fontSize = 27.sp,
                            lineHeight = 31.sp,
                            letterSpacing = (-.55).sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (signedIn) {
                            if (isChinese) "网易云音乐 · 已同步" else "NetEase Music · synced"
                        } else {
                            if (isChinese) "在线模式" else "Online mode"
                        },
                        color = topMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 4.dp)
                    )
                    profileDetails?.signature?.takeIf(String::isNotBlank)?.let { signature ->
                        Text(
                            text = signature,
                            color = topMuted.copy(alpha = .90f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(horizontal = 42.dp)
                                .padding(top = 5.dp)
                        )
                    }
                    NeteaseProfileStats(
                        profileDetails = profileDetails,
                        likedCount = likedTracks.size,
                        playlistCount = playlists.size,
                        recentCount = recentTracks.size,
                        textColor = topText,
                        mutedColor = topMuted,
                        isChinese = isChinese,
                        modifier = Modifier.padding(top = 17.dp)
                    )
                    profileDetails?.let { details ->
                        Text(
                            text = if (isChinese) {
                                "累计听歌 ${details.listenSongs} 首"
                            } else {
                                "${details.listenSongs} songs played in total"
                            },
                            color = topMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 5.dp)
                        )
                    }
                    if (!signedIn && onLogin != null) {
                        NeteaseProfileLoginChip(
                            onClick = onLogin,
                            textColor = topText,
                            glassTint = glassTint,
                            backdrop = backdrop,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 12.dp),
                            isChinese = isChinese
                        )
                    }
                    NeteaseProfileQuickStrip(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 20.dp, end = 16.dp),
                        recentCount = recentTracks.size,
                        likedCount = likedTracks.size,
                        playlistCount = playlists.size,
                        onOpenWebdav = onOpenWebdav,
                        onSettings = onSettings,
                        textColor = topText,
                        mutedColor = topMuted,
                        accentColor = accentColor,
                        isLightTheme = isLightTheme,
                        isChinese = isChinese
                    )
                }
            }

            item(key = "netease-profile-panel-tabs") {
                NeteaseProfileMusicPanelTabs(
                    panelColor = panelColor,
                    textColor = panelText,
                    mutedColor = panelMuted,
                    playlistCount = profileDetails?.playlistCount ?: playlists.size,
                    createdCount = createdPlaylists.size,
                    selectedCollection = collectionSection,
                    onCollectionSelected = { collectionSection = it },
                    isChinese = isChinese
                )
            }

            item(key = "netease-profile-liked") {
                NeteaseProfileLikedRow(
                    likedTracks = likedTracks,
                    panelColor = panelColor,
                    textColor = panelText,
                    mutedColor = panelMuted,
                    accentColor = accentColor,
                    onPlayLiked = onPlayLiked,
                    isChinese = isChinese
                )
            }

            if (loading && playlists.isEmpty() && likedTracks.isEmpty()) {
                item(key = "netease-profile-loading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(panelColor)
                            .padding(vertical = 30.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = accentColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (isChinese) "正在同步你的音乐" else "Syncing your music",
                            color = panelMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (collectionSection == NeteaseProfileCollection.Recent && onPlayRecent != null && recentTracks.isNotEmpty()) {
                item(key = "netease-profile-recent-heading") {
                    NeteaseProfileSectionHeading(
                        title = if (isChinese) "最近播放" else "Recently played",
                        detail = if (isChinese) "${recentTracks.size} 首" else "${recentTracks.size} tracks",
                        panelColor = panelColor,
                        textColor = panelText,
                        mutedColor = panelMuted
                    )
                }
                // The client already requests the NetEase history endpoint's bounded 100-song
                // window.  Do not apply a second UI-only preview cap here: the "最近" tab is
                // the full listening history view, and each visible row must remain playable.
                items(recentTracks, key = { "recent-${it.stableId}" }) { track ->
                    NeteaseProfileRecentRow(
                        track = track,
                        queue = recentTracks,
                        panelColor = panelColor,
                        textColor = panelText,
                        mutedColor = panelMuted,
                        accentColor = accentColor,
                        onPlay = onPlayRecent
                    )
                }
            }

            if (collectionSection == NeteaseProfileCollection.Playlists && createdPlaylists.isNotEmpty()) {
                item(key = "netease-profile-created-heading") {
                    NeteaseProfileSectionHeading(
                        title = if (isChinese) "创建的歌单" else "Created playlists",
                        detail = createdPlaylists.size.toString(),
                        panelColor = panelColor,
                        textColor = panelText,
                        mutedColor = panelMuted
                    )
                }
                items(createdPlaylists, key = { "created-${it.id}" }) { playlist ->
                    NeteaseProfilePlaylistRow(
                        playlist = playlist,
                        panelColor = panelColor,
                        textColor = panelText,
                        mutedColor = panelMuted,
                        accentColor = accentColor,
                        onClick = { onPlaylist(playlist.id) },
                        isChinese = isChinese
                    )
                }
            }

            if (collectionSection == NeteaseProfileCollection.Playlists && collectedPlaylists.isNotEmpty()) {
                item(key = "netease-profile-collected-heading") {
                    NeteaseProfileSectionHeading(
                        title = if (isChinese) "收藏的歌单" else "Collected playlists",
                        detail = collectedPlaylists.size.toString(),
                        panelColor = panelColor,
                        textColor = panelText,
                        mutedColor = panelMuted
                    )
                }
                items(collectedPlaylists, key = { "collected-${it.id}" }) { playlist ->
                    NeteaseProfilePlaylistRow(
                        playlist = playlist,
                        panelColor = panelColor,
                        textColor = panelText,
                        mutedColor = panelMuted,
                        accentColor = accentColor,
                        onClick = { onPlaylist(playlist.id) },
                        isChinese = isChinese
                    )
                }
            }

            if (!loading && playlists.isEmpty() && likedTracks.isEmpty() && recentTracks.isEmpty()) {
                item(key = "netease-profile-empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(panelColor)
                            .padding(horizontal = 28.dp, vertical = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            tint = panelMuted.copy(alpha = .7f),
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = if (isChinese) "暂时没有同步到音乐" else "No music synced yet",
                            color = panelText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        Text(
                            text = if (signedIn) {
                                if (isChinese) "收藏歌曲或创建歌单后会显示在这里" else "Liked songs and playlists will appear here."
                            } else {
                                if (isChinese) "登录网易云音乐后可同步收藏和歌单" else "Sign in to sync your likes and playlists."
                            },
                            color = panelMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                }
            }

            item(key = "netease-profile-panel-end") {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(panelColor)
                )
            }
        }
    }
}

@Composable
private fun NeteaseProfileAmbientArtwork(artworkUrl: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val model = remember(artworkUrl) {
        artworkUrl?.takeIf(String::isNotBlank)?.let { url ->
            ImageRequest.Builder(context)
                .data(neteaseImageRequestUrl(url, 720) ?: url)
                .crossfade(true)
                .build()
        }
    }
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .blur(28.dp)
                .alpha(.76f)
        )
    } else {
        Box(
            modifier.background(
                Brush.radialGradient(
                    listOf(Color(0xFF86A9A5), Color(0xFF263E40), Color(0xFF172729))
                )
            )
        )
    }
}

@Composable
private fun NeteaseProfileTopBar(
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    tint: Color,
    isChinese: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // This deliberately uses the flat NetEase-style toolbar rather than MuseIconButton:
        // circular controls compete with the profile identity and made the old header read as
        // a row of unrelated black buttons.
        NeteaseProfileToolbarAction(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = if (isChinese) "返回首页" else "Back to home",
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (isChinese) "+ 添加状态" else "+ Add status",
            color = tint.copy(alpha = .76f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(14.dp))
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = tint.copy(alpha = .92f),
            modifier = Modifier.size(25.dp)
        )
        Spacer(Modifier.width(8.dp))
        NeteaseProfileToolbarAction(onClick = onSearch) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = if (isChinese) "搜索" else "Search",
                tint = tint,
                modifier = Modifier.size(25.dp)
            )
        }
        Spacer(Modifier.width(2.dp))
        NeteaseProfileToolbarAction(onClick = onSettings) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = if (isChinese) "设置" else "Settings",
                tint = tint,
                modifier = Modifier.size(25.dp)
            )
        }
    }
}

@Composable
private fun NeteaseProfileToolbarAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .museLiquidCardPress(onClick = onClick, pressedScale = .93f),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun NeteaseProfileAvatar(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    fallbackTint: Color
) {
    val context = LocalContext.current
    val model = remember(avatarUrl) {
        avatarUrl?.takeIf(String::isNotBlank)?.let { url ->
            ImageRequest.Builder(context)
                .data(neteaseImageRequestUrl(url, 360) ?: url)
                .crossfade(true)
                .build()
        }
    }
    Box(
        modifier = modifier
            .size(82.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = .12f))
            .border(2.dp, Color.White.copy(alpha = .88f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = fallbackTint.copy(alpha = .9f),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun NeteaseProfileStats(
    profileDetails: NeteaseProfileDetails?,
    likedCount: Int,
    playlistCount: Int,
    recentCount: Int,
    textColor: Color,
    mutedColor: Color,
    isChinese: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 50.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        if (profileDetails != null) {
            NeteaseProfileStat(
                value = profileDetails.follows.toString(),
                label = if (isChinese) "关注" else "Following",
                textColor = textColor,
                mutedColor = mutedColor
            )
            NeteaseProfileStat(
                value = profileDetails.followers.toString(),
                label = if (isChinese) "粉丝" else "Followers",
                textColor = textColor,
                mutedColor = mutedColor
            )
            NeteaseProfileStat(
                value = "Lv.${profileDetails.level}",
                label = if (isChinese) "等级" else "Level",
                textColor = textColor,
                mutedColor = mutedColor
            )
        } else {
            NeteaseProfileStat(
                value = likedCount.toString(),
                label = if (isChinese) "喜欢" else "Likes",
                textColor = textColor,
                mutedColor = mutedColor
            )
            NeteaseProfileStat(
                value = playlistCount.toString(),
                label = if (isChinese) "歌单" else "Lists",
                textColor = textColor,
                mutedColor = mutedColor
            )
            NeteaseProfileStat(
                value = recentCount.toString(),
                label = if (isChinese) "最近" else "Recent",
                textColor = textColor,
                mutedColor = mutedColor
            )
        }
    }
}

@Composable
private fun NeteaseProfileStat(value: String, label: String, textColor: Color, mutedColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 23.sp)
        Text(label, color = mutedColor, fontSize = 11.sp, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun NeteaseProfileLoginChip(
    onClick: () -> Unit,
    textColor: Color,
    glassTint: Color,
    backdrop: Backdrop?,
    isChinese: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .museGlass(
                backdrop = backdrop,
                shape = shape,
                tint = glassTint,
                blurRadius = 10.dp,
                borderColor = Color.White.copy(alpha = .08f)
            )
            .museLiquidCardPress(onClick = onClick, pressedScale = .96f)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            tint = textColor.copy(alpha = .88f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (isChinese) "登录网易云音乐" else "Sign in to NetEase",
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 5.dp)
        )
    }
}

/**
 * These are compact identity shortcuts, not full cards.  A route only receives touch feedback
 * when there is a real destination; counters deliberately remain static so they do not promise
 * pages the online mode cannot currently open.
 */
@Composable
private fun NeteaseProfileQuickStrip(
    recentCount: Int,
    likedCount: Int,
    playlistCount: Int,
    onOpenWebdav: () -> Unit,
    onSettings: () -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    isLightTheme: Boolean,
    isChinese: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        NeteaseProfileQuickAction(
            title = if (isChinese) "最近" else "Recent",
            detail = if (isChinese) "$recentCount 首" else "$recentCount",
            icon = Icons.Default.History,
            onClick = null,
            textColor = textColor,
            mutedColor = mutedColor,
            iconColor = textColor.copy(alpha = .80f),
            isLightTheme = isLightTheme,
            modifier = Modifier.weight(1f)
        )
        NeteaseProfileQuickAction(
            title = if (isChinese) "云盘" else "WebDAV",
            detail = if (isChinese) "WebDAV" else "Cloud",
            icon = Icons.Default.Cloud,
            onClick = onOpenWebdav,
            textColor = textColor,
            mutedColor = mutedColor,
            iconColor = accentColor.copy(alpha = .94f),
            isLightTheme = isLightTheme,
            modifier = Modifier.weight(1f)
        )
        NeteaseProfileQuickAction(
            title = if (isChinese) "喜欢" else "Likes",
            detail = if (isChinese) "$likedCount 首" else "$likedCount",
            icon = Icons.Default.Favorite,
            onClick = null,
            textColor = textColor,
            mutedColor = mutedColor,
            iconColor = textColor.copy(alpha = .80f),
            isLightTheme = isLightTheme,
            modifier = Modifier.weight(1f)
        )
        NeteaseProfileQuickAction(
            title = if (isChinese) "歌单" else "Lists",
            detail = if (isChinese) "$playlistCount 个" else "$playlistCount",
            icon = Icons.Default.LibraryMusic,
            onClick = null,
            textColor = textColor,
            mutedColor = mutedColor,
            iconColor = textColor.copy(alpha = .80f),
            isLightTheme = isLightTheme,
            modifier = Modifier.weight(1f)
        )
        NeteaseProfileQuickAction(
            title = if (isChinese) "设置" else "Settings",
            detail = if (isChinese) "在线" else "Online",
            icon = Icons.Default.Settings,
            onClick = onSettings,
            textColor = textColor,
            mutedColor = mutedColor,
            iconColor = accentColor.copy(alpha = .94f),
            isLightTheme = isLightTheme,
            compact = true,
            modifier = Modifier.weight(.58f)
        )
    }
}

@Composable
private fun NeteaseProfileQuickAction(
    title: String,
    detail: String,
    icon: ImageVector,
    onClick: (() -> Unit)?,
    textColor: Color,
    mutedColor: Color,
    iconColor: Color,
    isLightTheme: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    // These shortcuts are tonal controls, not glass surfaces. A quiet fill keeps the
    // artwork visible without adding a second blur layer or a heavy border around every item.
    val surfaceColor = Color.White.copy(alpha = if (isLightTheme) .20f else .12f)
    val surface = modifier
        .height(44.dp)
        .clip(shape)
        .background(surfaceColor)
    Column(
        modifier = if (onClick == null) surface else surface.museLiquidCardPress(
            onClick = onClick,
            pressedScale = .96f
        )
            .padding(horizontal = 7.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (compact) {
            Icon(
                imageVector = icon,
                contentDescription = "$title $detail",
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "$title $detail",
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
private fun NeteaseProfileMusicPanelTabs(
    panelColor: Color,
    textColor: Color,
    mutedColor: Color,
    playlistCount: Int,
    createdCount: Int,
    selectedCollection: NeteaseProfileCollection,
    onCollectionSelected: (NeteaseProfileCollection) -> Unit,
    isChinese: Boolean
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(panelColor)
            .padding(top = 22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeteaseProfileTabLabel(
                title = if (isChinese) "音乐" else "Music",
                selected = true,
                textColor = textColor,
                mutedColor = mutedColor
            )
            NeteaseProfileTabLabel(
                title = if (isChinese) "播客" else "Podcasts",
                selected = false,
                textColor = textColor,
                mutedColor = mutedColor
            )
            NeteaseProfileTabLabel(
                title = if (isChinese) "笔记" else "Notes",
                selected = false,
                textColor = textColor,
                mutedColor = mutedColor
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 25.dp, top = 16.dp, end = 25.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeteaseProfileCollectionLabel(
                label = if (isChinese) "最近" else "Recent",
                selected = selectedCollection == NeteaseProfileCollection.Recent,
                textColor = textColor,
                mutedColor = mutedColor,
                onClick = { onCollectionSelected(NeteaseProfileCollection.Recent) }
            )
            Spacer(Modifier.width(22.dp))
            NeteaseProfileCollectionLabel(
                label = if (isChinese) "创建 $createdCount" else "Created $createdCount",
                selected = selectedCollection == NeteaseProfileCollection.Playlists,
                textColor = textColor,
                mutedColor = mutedColor,
                onClick = { onCollectionSelected(NeteaseProfileCollection.Playlists) }
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (isChinese) "$playlistCount 个歌单" else "$playlistCount lists",
                color = mutedColor.copy(alpha = .82f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun NeteaseProfileCollectionLabel(
    label: String,
    selected: Boolean,
    textColor: Color,
    mutedColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .museLiquidCardPress(onClick = onClick, pressedScale = .96f)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (selected) textColor else mutedColor.copy(alpha = .68f),
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
        )
        Box(
            Modifier
                .padding(top = 5.dp)
                .width(19.dp)
                .height(2.dp)
                .clip(CircleShape)
                .background(if (selected) textColor else Color.Transparent)
        )
    }
}

@Composable
private fun NeteaseProfileTabLabel(
    title: String,
    selected: Boolean,
    textColor: Color,
    mutedColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            color = if (selected) textColor else mutedColor.copy(alpha = .62f),
            fontSize = if (selected) 26.sp else 24.sp,
            lineHeight = 30.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
        )
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .width(32.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(if (selected) textColor else Color.Transparent)
        )
    }
}

@Composable
private fun NeteaseProfileLikedRow(
    likedTracks: List<OnlineTrack>,
    panelColor: Color,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onPlayLiked: () -> Unit,
    isChinese: Boolean
) {
    val cover = likedTracks.firstOrNull()?.artworkUrl
    val shape = RoundedCornerShape(14.dp)
    val rowModifier = Modifier
        .fillMaxWidth()
        .background(panelColor)
        .padding(horizontal = 24.dp, vertical = 8.dp)
        .clip(shape)
    Row(
        modifier = if (likedTracks.isNotEmpty()) {
            rowModifier.museLiquidCardPress(onClick = onPlayLiked)
        } else {
            rowModifier
        }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeteaseProfileSquareArtwork(
            artworkUrl = cover,
            fallbackIcon = Icons.Default.Favorite,
            fallbackColor = accentColor,
            modifier = Modifier.size(60.dp)
        )
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(
                text = if (isChinese) "我喜欢的音乐" else "My liked music",
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (likedTracks.isEmpty()) {
                    if (isChinese) "还没有收藏歌曲" else "No liked songs yet"
                } else {
                    if (isChinese) "${likedTracks.size} 首 · 已收藏" else "${likedTracks.size} tracks · saved"
                },
                color = mutedColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Icon(
            imageVector = if (likedTracks.isNotEmpty()) Icons.Default.PlayArrow else Icons.Default.Favorite,
            contentDescription = if (likedTracks.isNotEmpty()) {
                if (isChinese) "播放喜欢的音乐" else "Play liked music"
            } else null,
            tint = if (likedTracks.isNotEmpty()) accentColor else mutedColor.copy(alpha = .45f),
            modifier = Modifier.size(23.dp)
        )
    }
}

@Composable
private fun NeteaseProfileSectionHeading(
    title: String,
    detail: String,
    panelColor: Color,
    textColor: Color,
    mutedColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(panelColor)
            .padding(start = 24.dp, top = 21.dp, end = 24.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = textColor, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(detail, color = mutedColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NeteaseProfileRecentRow(
    track: OnlineTrack,
    queue: List<OnlineTrack>,
    panelColor: Color,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(panelColor)
            .clip(RoundedCornerShape(16.dp))
            .museLiquidCardPress(onClick = { onPlay(track, queue) })
            .padding(horizontal = 28.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeteaseProfileSquareArtwork(
            artworkUrl = track.artworkUrl,
            fallbackIcon = Icons.Default.History,
            fallbackColor = mutedColor,
            modifier = Modifier.size(45.dp)
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = track.title,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = mutedColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun NeteaseProfilePlaylistRow(
    playlist: RemotePlaylistSummary,
    panelColor: Color,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    isChinese: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth().background(panelColor)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .museLiquidCardPress(onClick = onClick)
                .padding(horizontal = 28.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeteaseProfileSquareArtwork(
                artworkUrl = playlist.coverUrl,
                fallbackIcon = Icons.Default.LibraryMusic,
                fallbackColor = accentColor,
                modifier = Modifier.size(58.dp)
            )
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(
                    text = playlist.name,
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildList {
                    add(if (isChinese) "${playlist.trackCount} 首" else "${playlist.trackCount} tracks")
                    playlist.creatorName.takeIf(String::isNotBlank)?.let { creator ->
                        add(if (isChinese) creator else "by $creator")
                    }
                }.joinToString(" · ")
                Text(
                    text = meta,
                    color = mutedColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = if (isChinese) "打开歌单" else "Open playlist",
                tint = accentColor,
                modifier = Modifier.size(21.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 99.dp, end = 28.dp),
            color = mutedColor.copy(alpha = .14f)
        )
    }
}

@Composable
private fun NeteaseProfileSquareArtwork(
    artworkUrl: String?,
    fallbackIcon: ImageVector,
    fallbackColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val model = remember(artworkUrl) {
        artworkUrl?.takeIf(String::isNotBlank)?.let { url ->
            ImageRequest.Builder(context)
                .data(neteaseImageRequestUrl(url, 180) ?: url)
                .crossfade(true)
                .build()
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(fallbackColor.copy(alpha = .20f)),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = fallbackColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private enum class NeteaseProfileCollection {
    Recent,
    Playlists
}
