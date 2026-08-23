package com.caipan.music.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import com.caipan.music.plugin.BlurLocation
import com.caipan.music.online.NeteaseHomeContent
import com.caipan.music.online.NeteaseHomePodcast
import com.caipan.music.online.OnlineTrack
import com.caipan.music.online.RemotePlaylistSummary
import com.caipan.music.online.neteaseImageRequestUrl

/**
 * A clean-room Compose implementation of the mobile visual hierarchy used by Mei.
 *
 * Content remains flat and scannable: artwork and typography carry the page, while
 * the liquid material stays reserved for the shared bottom chrome and sheets.
 */
@Composable
internal fun MeiStyleOnlineHomeContent(
    home: NeteaseHomeContent?,
    session: NeteaseSession?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    onSearch: () -> Unit,
    onPlaylist: (Long) -> Unit,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onOpenComments: (OnlineTrack) -> Unit,
    onPlayPodcast: (NeteaseHomePodcast) -> Unit,
    onAccount: () -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    isLightTheme: Boolean,
    isChinese: Boolean
) {
    val playlists = home?.playlists.orEmpty()
    val newSongs = home?.newSongs.orEmpty()
    val firstTrack = newSongs.firstOrNull()
    val firstPlaylist = playlists.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item(key = "mei-style-home-header") {
            MeiStyleHomeHeader(
                session = session,
                onSearch = onSearch,
                onAccount = onAccount,
                textColor = textColor,
                mutedColor = mutedColor,
                accentColor = accentColor,
                isLightTheme = isLightTheme,
                isChinese = isChinese
            )
        }

        item(key = "mei-style-home-recommendations") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "mei-style-fresh-picks") {
                    MeiStyleRecommendationCard(
                        title = if (isChinese) "\u65b0\u6b4c\u63a8\u8350" else "Fresh picks",
                        subtitle = if (isChinese) "\u4e3a\u4f60\u6311\u9009\u7684\u65b0\u9c9c\u597d\u6b4c" else "Fresh music for you",
                        artworkUrl = firstTrack?.artworkUrl,
                        fallback = Color(0xFF2077D5),
                        onClick = { if (firstTrack != null) onPlay(firstTrack, newSongs) else onRefresh() },
                        textColor = textColor,
                        mutedColor = mutedColor
                    )
                }
                item(key = "mei-style-playlist-picks") {
                    MeiStyleRecommendationCard(
                        title = if (isChinese) "\u63a8\u8350\u6b4c\u5355" else "Top playlists",
                        subtitle = firstPlaylist?.name
                            ?: if (isChinese) "\u4e3a\u4f60\u6311\u9009\u7684\u97f3\u4e50" else "Picked for you",
                        artworkUrl = firstPlaylist?.coverUrl,
                        fallback = Color(0xFFE55272),
                        onClick = { if (firstPlaylist != null) onPlaylist(firstPlaylist.id) else onRefresh() },
                        textColor = textColor,
                        mutedColor = mutedColor
                    )
                }
            }
        }

        if (session == null) {
            item(key = "mei-style-home-login") {
                MeiStyleLoginStrip(
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
            item(key = "mei-style-home-loading") {
                Box(Modifier.fillMaxWidth().height(108.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor, modifier = Modifier.size(26.dp))
                }
            }
        }

        error?.let { message ->
            item(key = "mei-style-home-error") {
                MeiStyleStatusRow(
                    message = message,
                    onClick = onRefresh,
                    textColor = textColor,
                    mutedColor = mutedColor,
                    isChinese = isChinese
                )
            }
        }

        if (playlists.isNotEmpty()) {
            item(key = "mei-style-home-playlists-heading") {
                MeiStyleSectionHeading(
                    title = if (isChinese) "\u63a8\u8350\u6b4c\u5355" else "Recommended playlists",
                    textColor = textColor
                )
            }
            item(key = "mei-style-home-playlists") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        MeiStylePlaylistCard(
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
            item(key = "mei-style-home-tracks-heading") {
                MeiStyleSectionHeading(
                    title = if (isChinese) "\u65b0\u6b4c\u63a8\u8350" else "Fresh tracks",
                    trailing = newSongs.size.toString(),
                    textColor = textColor,
                    mutedColor = mutedColor
                )
            }
            items(newSongs.take(6), key = { it.stableId }) { track ->
                MeiStyleTrackRow(
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

        MeiStyleHomeTrackSection(
            key = "recently-trending",
            title = if (isChinese) "近期云村热播" else "Trending in Cloud Village",
            tracks = home?.recentlyTrending.orEmpty(),
            onPlay = onPlay,
            onOpenComments = onOpenComments,
            textColor = textColor,
            mutedColor = mutedColor,
            accentColor = accentColor,
            isLightTheme = isLightTheme,
            isChinese = isChinese
        )
        MeiStyleHomeTrackSection(
            key = "tailored-songs",
            title = if (isChinese) "根据你的喜好推荐" else "Picked for your taste",
            tracks = home?.tailoredSongs.orEmpty(),
            onPlay = onPlay,
            onOpenComments = onOpenComments,
            textColor = textColor,
            mutedColor = mutedColor,
            accentColor = accentColor,
            isLightTheme = isLightTheme,
            isChinese = isChinese
        )
        MeiStyleHomePlaylistSection(
            key = "chart-playlists",
            title = if (isChinese) "排行榜" else "Charts",
            playlists = home?.chartPlaylists.orEmpty(),
            onPlaylist = onPlaylist,
            textColor = textColor,
            mutedColor = mutedColor,
            isLightTheme = isLightTheme,
            isChinese = isChinese
        )
        MeiStyleHomePlaylistSection(
            key = "radar-playlists",
            title = if (isChinese) "私人雷达" else "Private radar",
            playlists = home?.radarPlaylists.orEmpty(),
            onPlaylist = onPlaylist,
            textColor = textColor,
            mutedColor = mutedColor,
            isLightTheme = isLightTheme,
            isChinese = isChinese
        )
        MeiStyleHomePlaylistSection(
            key = "personal-playlists",
            title = if (isChinese) "我的歌单" else "My playlists",
            playlists = home?.personalPlaylists.orEmpty(),
            onPlaylist = onPlaylist,
            textColor = textColor,
            mutedColor = mutedColor,
            isLightTheme = isLightTheme,
            isChinese = isChinese
        )
        MeiStyleHomeTrackSection(
            key = "regional-songs",
            title = if (isChinese) "地区热门" else "Regional picks",
            tracks = home?.regionalSongs.orEmpty(),
            onPlay = onPlay,
            onOpenComments = onOpenComments,
            textColor = textColor,
            mutedColor = mutedColor,
            accentColor = accentColor,
            isLightTheme = isLightTheme,
            isChinese = isChinese
        )
        MeiStyleHomeTrackSection(
            key = "roaming-songs",
            title = if (isChinese) "私人漫游" else "Personal radio",
            tracks = home?.roamingSongs.orEmpty(),
            onPlay = onPlay,
            onOpenComments = onOpenComments,
            textColor = textColor,
            mutedColor = mutedColor,
            accentColor = accentColor,
            isLightTheme = isLightTheme,
            isChinese = isChinese
        )
        MeiStyleHomeTrackSection(
            key = "similar-songs",
            title = if (isChinese) "相似歌曲" else "Similar songs",
            tracks = home?.similarSongs.orEmpty(),
            onPlay = onPlay,
            onOpenComments = onOpenComments,
            textColor = textColor,
            mutedColor = mutedColor,
            accentColor = accentColor,
            isLightTheme = isLightTheme,
            isChinese = isChinese
        )
        if (home?.podcasts.orEmpty().isNotEmpty()) {
            item(key = "podcasts") {
                MeiStyleHomePodcastSection(
                    podcasts = home?.podcasts.orEmpty(),
                    textColor = textColor,
                    mutedColor = mutedColor,
                    isLightTheme = isLightTheme,
                    isChinese = isChinese,
                    onPodcast = onPlayPodcast
                )
            }
        }

        if (!loading && home == null && error == null) {
            item(key = "mei-style-home-empty") {
                MeiStyleEmptyState(
                    icon = Icons.Default.Cloud,
                    title = if (isChinese) "\u6682\u65e0\u63a8\u8350\u5185\u5bb9" else "No recommendations yet",
                    detail = if (isChinese) "\u70b9\u51fb\u91cd\u65b0\u52a0\u8f7d" else "Tap to reload",
                    onClick = onRefresh,
                    textColor = textColor,
                    mutedColor = mutedColor
                )
            }
        }
    }
}

private fun LazyListScope.MeiStyleHomePlaylistSection(
    key: String,
    title: String,
    playlists: List<RemotePlaylistSummary>,
    onPlaylist: (Long) -> Unit,
    textColor: Color,
    mutedColor: Color,
    isLightTheme: Boolean,
    isChinese: Boolean
) {
    if (playlists.isEmpty()) return
    item(key = "$key-heading") {
        MeiStyleSectionHeading(title = title, trailing = if (isChinese) "更多" else "See all", textColor = textColor, mutedColor = mutedColor)
    }
    item(key = "$key-row") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(playlists.take(8), key = { "$key-${it.id}" }) { playlist ->
                MeiStylePlaylistCard(playlist, { onPlaylist(playlist.id) }, textColor, mutedColor, isLightTheme)
            }
        }
    }
}

private fun LazyListScope.MeiStyleHomeTrackSection(
    key: String,
    title: String,
    tracks: List<OnlineTrack>,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onOpenComments: (OnlineTrack) -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    isLightTheme: Boolean,
    isChinese: Boolean
) {
    if (tracks.isEmpty()) return
    item(key = "$key-heading") {
        MeiStyleSectionHeading(title = title, trailing = if (isChinese) "更多" else "See all", textColor = textColor, mutedColor = mutedColor)
    }
    item(key = "$key-rows") {
        Column {
            tracks.take(6).forEach { track ->
                MeiStyleTrackRow(track, tracks, onPlay, onOpenComments, textColor, mutedColor, accentColor, isLightTheme, isChinese)
            }
        }
    }
}

@Composable
private fun MeiStyleHomePodcastSection(
    podcasts: List<NeteaseHomePodcast>,
    textColor: Color,
    mutedColor: Color,
    isLightTheme: Boolean,
    isChinese: Boolean,
    onPodcast: (NeteaseHomePodcast) -> Unit
) {
    MeiStyleSectionHeading(title = if (isChinese) "播客" else "Podcasts", trailing = if (isChinese) "推荐" else "For you", textColor = textColor, mutedColor = mutedColor)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 10.dp)) {
        items(podcasts.take(8), key = { "podcast-${it.id}" }) { podcast ->
            Column(Modifier.width(132.dp).clickable { onPodcast(podcast) }.pressScale()) {
                MeiStyleArtwork(podcast.artworkUrl, podcast.name, Modifier.size(132.dp).clip(RoundedCornerShape(12.dp)).background(if (isLightTheme) Color(0xFFE8E8ED) else Color(0xFF1B1B1E)))
                Text(podcast.name, color = textColor, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
                Text(if (isChinese) "网易云播客" else "NetEase podcast", color = mutedColor, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

/** Six compact library sections mirroring Mei's mobile information structure. */
@Composable
internal fun MeiStyleOnlineLibraryContent(
    playlists: List<RemotePlaylistSummary>,
    likedSongs: List<OnlineTrack>,
    recentSongs: List<OnlineTrack>,
    session: NeteaseSession?,
    loading: Boolean,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onOpenWebdav: () -> Unit,
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
    val labels = if (isChinese) {
        listOf("\u6b4c\u66f2", "\u6b4c\u5355", "\u64ad\u5ba2", "\u4e0b\u8f7d", "\u4e91\u76d8", "\u5386\u53f2")
    } else {
        listOf("Songs", "Lists", "Pods", "Downloads", "Cloud", "History")
    }
    val userId = session?.userId?.takeIf { it > 0L }
    val created = playlists.filter { userId != null && it.creatorUserId == userId && !it.subscribed }
    val collected = playlists.filterNot { userId != null && it.creatorUserId == userId && !it.subscribed }
    // Muse has no provider-side intelligence endpoint yet, so expose the
    // truthful local capability: shuffle the signed-in user's liked queue.
    val shuffleQueue = remember(likedSongs) { likedSongs.shuffled() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "mei-style-library-header") {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isChinese) "\u97f3\u4e50\u5e93" else "Music library",
                        color = textColor,
                        fontSize = 34.sp,
                        lineHeight = 41.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.7).sp
                    )
                    session?.nickname?.takeIf { it.isNotBlank() }?.let { name ->
                        Text(
                            text = if (isChinese) "$name\u7684\u97f3\u4e50" else "$name's music",
                            color = mutedColor,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
                IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = if (isChinese) "\u641c\u7d22" else "Search",
                        tint = textColor,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        item(key = "mei-style-library-tabs") {
            MeiStyleLibraryTabs(
                labels = labels,
                selectedIndex = selectedPage,
                onSelect = { page ->
                    // Cloud is backed by Muse's existing WebDAV browser rather
                    // than an unimplemented NetEase Cloud placeholder.
                    if (page == 4) onOpenWebdav() else selectedPage = page
                },
                textColor = textColor,
                mutedColor = mutedColor,
                isLightTheme = isLightTheme
            )
        }

        if (session == null) {
            item(key = "mei-style-library-login") {
                MeiStyleLoginStrip(
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
                item(key = "mei-style-library-actions") {
                    MeiStyleLibraryActions(
                        playAllEnabled = likedSongs.isNotEmpty(),
                        shuffleEnabled = shuffleQueue.isNotEmpty(),
                        onPlayAll = { likedSongs.firstOrNull()?.let { onPlay(it, likedSongs) } },
                        onShuffle = { shuffleQueue.firstOrNull()?.let { onPlay(it, shuffleQueue) } },
                        textColor = textColor,
                        mutedColor = mutedColor,
                        accentColor = accentColor,
                        isLightTheme = isLightTheme,
                        isChinese = isChinese
                    )
                }
                if (likedSongs.isEmpty() && !loading) {
                    item(key = "mei-style-library-songs-empty") {
                        MeiStyleEmptyState(
                            icon = Icons.Default.Favorite,
                            title = if (isChinese) "\u8fd8\u6ca1\u6709\u6536\u85cf\u6b4c\u66f2" else "No liked songs",
                            detail = if (isChinese) "\u4f60\u6536\u85cf\u7684\u7f51\u6613\u4e91\u6b4c\u66f2\u4f1a\u51fa\u73b0\u5728\u8fd9\u91cc" else "Liked NetEase tracks appear here",
                            onClick = onRefresh,
                            textColor = textColor,
                            mutedColor = mutedColor
                        )
                    }
                } else {
                    items(likedSongs, key = { it.stableId }) { track ->
                        MeiStyleTrackRow(
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
                }
            }

            1 -> {
                if (playlists.isEmpty() && !loading) {
                    item(key = "mei-style-library-playlists-empty") {
                        MeiStyleEmptyState(
                            icon = Icons.Default.LibraryMusic,
                            title = if (isChinese) "\u8fd8\u6ca1\u6709\u6b4c\u5355" else "No playlists",
                            detail = if (isChinese) "\u521b\u5efa\u548c\u6536\u85cf\u7684\u6b4c\u5355\u4f1a\u540c\u6b65\u5230\u8fd9\u91cc" else "Created and collected playlists appear here",
                            onClick = onRefresh,
                            textColor = textColor,
                            mutedColor = mutedColor
                        )
                    }
                } else {
                    if (created.isNotEmpty()) {
                        item(key = "mei-style-library-created-heading") {
                            MeiStyleSectionHeading(
                                title = if (isChinese) "\u6211\u521b\u5efa\u7684\u6b4c\u5355" else "Created playlists",
                                trailing = created.size.toString(),
                                textColor = textColor,
                                mutedColor = mutedColor
                            )
                        }
                        items(created, key = { it.id }) { playlist ->
                            MeiStyleLibraryPlaylistRow(playlist, onPlaylist, textColor, mutedColor, isLightTheme, isChinese)
                        }
                    }
                    if (collected.isNotEmpty()) {
                        item(key = "mei-style-library-collected-heading") {
                            MeiStyleSectionHeading(
                                title = if (isChinese) "\u6536\u85cf\u7684\u6b4c\u5355" else "Collected playlists",
                                trailing = collected.size.toString(),
                                textColor = textColor,
                                mutedColor = mutedColor
                            )
                        }
                        items(collected, key = { it.id }) { playlist ->
                            MeiStyleLibraryPlaylistRow(playlist, onPlaylist, textColor, mutedColor, isLightTheme, isChinese)
                        }
                    }
                }
            }

            2 -> item(key = "mei-style-library-podcasts") {
                MeiStyleEmptyState(
                    icon = Icons.Default.Podcasts,
                    title = if (isChinese) "\u64ad\u5ba2\u6b63\u5728\u51c6\u5907\u4e2d" else "Podcasts are coming",
                    detail = if (isChinese) "Muse \u6682\u672a\u63a5\u5165\u7f51\u6613\u4e91\u64ad\u5ba2\u6570\u636e" else "Muse has not connected NetEase podcasts yet",
                    onClick = null,
                    textColor = textColor,
                    mutedColor = mutedColor
                )
            }

            3 -> item(key = "mei-style-library-downloads") {
                MeiStyleEmptyState(
                    icon = Icons.Default.Download,
                    title = if (isChinese) "\u6ca1\u6709\u79bb\u7ebf\u4e0b\u8f7d" else "No downloads",
                    detail = if (isChinese) "\u5728\u7ebf\u4e0b\u8f7d\u63a5\u5165\u540e\u4f1a\u663e\u793a\u5728\u8fd9\u91cc" else "Online downloads will appear here",
                    onClick = null,
                    textColor = textColor,
                    mutedColor = mutedColor
                )
            }

            4 -> item(key = "mei-style-library-cloud") {
                MeiStyleEmptyState(
                    icon = Icons.Default.Cloud,
                    title = if (isChinese) "WebDAV \u4e91\u76d8" else "WebDAV library",
                    detail = if (isChinese) "\u8fde\u63a5\u5e76\u5bfc\u5165\u4f60\u7684\u8fdc\u7a0b\u97f3\u4e50" else "Connect and import your remote music",
                    onClick = onOpenWebdav,
                    textColor = textColor,
                    mutedColor = mutedColor
                )
            }

            else -> {
                if (recentSongs.isEmpty() && !loading) {
                    item(key = "mei-style-library-history-empty") {
                        MeiStyleEmptyState(
                            icon = Icons.Default.History,
                            title = if (isChinese) "\u6682\u65e0\u6700\u8fd1\u64ad\u653e" else "Nothing played recently",
                            detail = if (isChinese) "\u4f60\u7684\u7f51\u6613\u4e91\u64ad\u653e\u8bb0\u5f55\u4f1a\u51fa\u73b0\u5728\u8fd9\u91cc" else "Your NetEase history appears here",
                            onClick = onRefresh,
                            textColor = textColor,
                            mutedColor = mutedColor
                        )
                    }
                } else {
                    items(recentSongs, key = { it.stableId }) { track ->
                        MeiStyleTrackRow(
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
private fun MeiStyleHomeHeader(
    session: NeteaseSession?,
    onSearch: () -> Unit,
    onAccount: () -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    isLightTheme: Boolean,
    isChinese: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (isChinese) "\u9996\u9875" else "Home",
                color = textColor,
                fontSize = 34.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.7).sp
            )
            Text(
                text = meiStyleGreeting(isChinese),
                color = mutedColor,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = if (isChinese) "\u641c\u7d22" else "Search",
                tint = textColor,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        MeiStyleAvatar(
            avatarUrl = session?.avatarUrl,
            onClick = onAccount,
            accentColor = accentColor,
            isLightTheme = isLightTheme,
            description = if (isChinese) "\u6253\u5f00\u4e2a\u4eba\u9875" else "Open profile"
        )
    }
}

@Composable
private fun MeiStyleRecommendationCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    fallback: Color,
    onClick: () -> Unit,
    textColor: Color,
    mutedColor: Color
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .pressScale()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(fallback.copy(alpha = 0.28f))
        ) {
            MeiStyleArtwork(artworkUrl, title, Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(9.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.34f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            text = title,
            color = textColor,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = subtitle,
            color = mutedColor,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun MeiStylePlaylistCard(
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
        MeiStyleArtwork(
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
private fun MeiStyleTrackRow(
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
            MeiStyleArtwork(
                url = track.artworkUrl,
                description = track.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isLightTheme) Color(0xFFE8E8ED) else Color(0xFF1B1B1E))
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
                    text = listOf(track.artist, track.album).filter(String::isNotBlank).joinToString(" \u00b7 "),
                    color = mutedColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            if (track.durationMs > 0L) {
                Text(track.formattedDuration, color = mutedColor, fontSize = 12.sp, maxLines = 1)
            }
            IconButton(onClick = { onOpenComments(track) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = if (isChinese) "\u67e5\u770b\u8bc4\u8bba" else "View comments",
                    tint = mutedColor,
                    modifier = Modifier.size(19.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = if (isChinese) "\u64ad\u653e ${track.title}" else "Play ${track.title}",
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = mutedColor.copy(alpha = 0.17f))
    }
}

@Composable
private fun MeiStyleLibraryTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    textColor: Color,
    mutedColor: Color,
    isLightTheme: Boolean
) {
    val trackColor = if (isLightTheme) Color(0xFFE5E5EA).copy(alpha = 0.84f) else Color.White.copy(alpha = 0.10f)
    val lensColor = if (isLightTheme) Color.White.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.16f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val selectionColor by animateColorAsState(
                targetValue = if (index == selectedIndex) lensColor else Color.Transparent,
                label = "mei-style-library-tab"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(selectionColor)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (index == selectedIndex) textColor else mutedColor,
                    fontSize = 10.sp,
                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MeiStyleLibraryActions(
    playAllEnabled: Boolean,
    shuffleEnabled: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    isLightTheme: Boolean,
    isChinese: Boolean
) {
    val surface = if (isLightTheme) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(surface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        MeiStyleLibraryActionRow(
            icon = Icons.Default.PlayArrow,
            title = if (isChinese) "\u64ad\u653e\u5168\u90e8" else "Play all",
            enabled = playAllEnabled,
            onClick = onPlayAll,
            textColor = textColor,
            mutedColor = mutedColor,
            accentColor = accentColor
        )
        HorizontalDivider(color = mutedColor.copy(alpha = 0.20f))
        MeiStyleLibraryActionRow(
            icon = Icons.Default.Shuffle,
            title = if (isChinese) "\u968f\u673a\u64ad\u653e" else "Shuffle",
            enabled = shuffleEnabled,
            onClick = onShuffle,
            textColor = textColor,
            mutedColor = mutedColor,
            accentColor = accentColor
        )
    }
}

@Composable
private fun MeiStyleLibraryActionRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) accentColor else mutedColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            color = if (enabled) textColor else mutedColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 14.dp)
        )
    }
}

@Composable
private fun MeiStyleLibraryPlaylistRow(
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
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MeiStyleArtwork(
                url = playlist.coverUrl,
                description = playlist.name,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isLightTheme) Color(0xFFE8E8ED) else Color(0xFF1B1B1E))
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = playlist.name,
                    color = textColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val metadata = buildString {
                    append(if (isChinese) "${playlist.trackCount}\u9996\u6b4c\u66f2" else "${playlist.trackCount} songs")
                    playlist.creatorName.takeIf(String::isNotBlank)?.let { append(" \u00b7 $it") }
                }
                Text(
                    text = metadata,
                    color = mutedColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = mutedColor, modifier = Modifier.size(20.dp))
        }
        HorizontalDivider(modifier = Modifier.padding(start = 66.dp), color = mutedColor.copy(alpha = 0.17f))
    }
}

@Composable
private fun MeiStyleSectionHeading(
    title: String,
    textColor: Color,
    trailing: String? = null,
    mutedColor: Color = textColor.copy(alpha = 0.58f)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            color = textColor,
            fontSize = 20.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.weight(1f)
        )
        trailing?.let { Text(it, color = mutedColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun MeiStyleAvatar(
    avatarUrl: String?,
    onClick: () -> Unit,
    accentColor: Color,
    isLightTheme: Boolean,
    description: String
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (isLightTheme) Color(0xFFE5E5EA) else Color(0xFF202023))
            .pressScale()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        MeiStyleArtwork(avatarUrl, description, Modifier.fillMaxSize().clip(CircleShape))
        if (avatarUrl.isNullOrBlank()) {
            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = accentColor, modifier = Modifier.size(27.dp))
        }
    }
}

@Composable
private fun MeiStyleArtwork(url: String?, description: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_apple_music),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
            modifier = Modifier.size(24.dp)
        )
        val imageUrl = neteaseImageRequestUrl(url, 480)
        if (imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true).build(),
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun MeiStyleLoginStrip(
    onLogin: () -> Unit,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    isLightTheme: Boolean,
    isChinese: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isLightTheme) Color(0xFFF2F2F7) else Color(0xFF1C1C1E))
            .pressScale()
            .clickable(onClick = onLogin)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = if (isChinese) "\u767b\u5f55\u7f51\u6613\u4e91\u97f3\u4e50" else "Sign in to NetEase Music",
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isChinese) "\u540c\u6b65\u63a8\u8350\u3001\u6b4c\u5355\u548c\u5386\u53f2\u8bb0\u5f55" else "Sync recommendations, playlists and history",
                color = mutedColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = if (isChinese) "\u767b\u5f55" else "Sign in",
            color = accentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MeiStyleStatusRow(
    message: String,
    onClick: () -> Unit,
    textColor: Color,
    mutedColor: Color,
    isChinese: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(mutedColor.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(message, color = textColor, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (isChinese) "\u70b9\u51fb\u91cd\u8bd5" else "Tap to retry",
                color = mutedColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Icon(Icons.Default.Cloud, contentDescription = null, tint = mutedColor, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MeiStyleEmptyState(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: (() -> Unit)?,
    textColor: Color,
    mutedColor: Color
) {
    val interaction = if (onClick == null) Modifier else Modifier
        .pressScale()
        .clickable(onClick = onClick)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(interaction)
            .padding(horizontal = 20.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(icon, contentDescription = null, tint = mutedColor, modifier = Modifier.size(28.dp))
        Text(title, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = mutedColor, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun meiStyleGreeting(isChinese: Boolean): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return if (isChinese) {
        when (hour) {
            in 5..10 -> "\u65e9\u4e0a\u597d"
            in 11..17 -> "\u4e0b\u5348\u597d"
            else -> "\u665a\u4e0a\u597d"
        }
    } else {
        when (hour) {
            in 5..10 -> "Good morning"
            in 11..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
