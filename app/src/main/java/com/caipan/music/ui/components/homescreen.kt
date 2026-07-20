// Muse non-player home UI. Library-first: all content comes from the device or configured WebDAV.
package com.caipan.music.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.caipan.music.model.Song
import com.caipan.music.ui.theme.MuseDesign
import com.kyant.backdrop.Backdrop

private val tabs = listOf(
    MuseLiquidTab("现在听", Icons.Default.PlayCircle),
    MuseLiquidTab("资料库", Icons.Default.LibraryMusic),
    MuseLiquidTab("个人", Icons.Default.Person)
)

@Composable
fun HomeScreen(
    accentColor: Color = MuseDesign.Red, isLightTheme: Boolean = false,
    isMonetStyle: Boolean = false,
    onPlaylistsTap: () -> Unit, onLocalMusicTap: () -> Unit, onSettingsTap: () -> Unit,
    onWebdavTap: () -> Unit = {}, onPickAvatar: () -> Unit = {}, onProfileNameChange: (String) -> Unit = {},
    recentSongs: List<Song> = emptyList(), allSongs: List<Song> = recentSongs,
    profileName: String = "Muse 用户", profileAvatar: Uri? = null,
    listeningTimeMs: Long = 0, completedPlays: Int = 0, repeatCount: Int = 0,
    onRecentSongTap: (Song) -> Unit = {},
    currentSong: Song? = null, isPlaying: Boolean = false, onPlayPause: () -> Unit = {},
    onTapPlayer: () -> Unit = {}, backdrop: Backdrop? = null
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val surface = MaterialTheme.colorScheme.surface
    val glassTint = surface
    val text = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Scaffold(containerColor = Color.Transparent, bottomBar = {
        if (isMonetStyle) {
            MuseLiquidBottomTabs(
                selectedIndex = selectedTab,
                onSelected = { selectedTab = it },
                tabs = tabs,
                backdrop = backdrop,
                accentColor = accentColor,
                contentColor = text,
                isLightTheme = isLightTheme,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .height(64.dp)
            )
        } else {
            val shape = RoundedCornerShape(30.dp)
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .height(64.dp)
                    .museGlass(backdrop, shape, surface.copy(alpha = .38f), 18.dp, liquidGlass = false)
                    .clip(shape),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(item.icon, item.title, Modifier.size(23.dp)) },
                        label = { Text(item.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = accentColor,
                            selectedTextColor = accentColor,
                            indicatorColor = accentColor.copy(alpha = .12f),
                            unselectedIconColor = muted,
                            unselectedTextColor = muted
                        )
                    )
                }
            }
        }
    }) { inset ->
        CompositionLocalProvider(LocalMuseLiquidGlass provides isMonetStyle) {
        Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "homeTab"
        ) { tab ->
        when (tab) {
            0 -> NowListening(inset, recentSongs, currentSong, isPlaying, onRecentSongTap, onPlayPause, onTapPlayer, onLocalMusicTap, onPlaylistsTap, text, muted, glassTint, accentColor, backdrop)
            1 -> UnifiedLibrary(inset, allSongs, onRecentSongTap, onLocalMusicTap, onPlaylistsTap, onWebdavTap, text, muted, glassTint, accentColor, backdrop)
            else -> ProfileTab(inset, profileName, profileAvatar, listeningTimeMs, completedPlays, repeatCount, allSongs.size, onPickAvatar, onProfileNameChange, onSettingsTap, text, muted, glassTint, accentColor, backdrop)
        }
        }
        }
        }
    }
}

@Composable private fun Header(title: String, subtitle: String? = null, action: (() -> Unit)? = null, text: Color, muted: Color) { Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) { Column(Modifier.weight(1f)) { Text(title, color = text, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold); subtitle?.let { Text(it, color = muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) } }; action?.let { IconButton(it) { Icon(Icons.Default.AccountCircle, "个人资料", tint = muted, modifier = Modifier.size(29.dp)) } } } }
@Composable private fun Page(modifier: Modifier, content: LazyListScope.() -> Unit) { LazyColumn(modifier, contentPadding = PaddingValues(bottom = 112.dp), content = content) }
@Composable private fun NowListening(inset: PaddingValues, songs: List<Song>, current: Song?, playing: Boolean, onSong: (Song) -> Unit, playPause: () -> Unit, tapPlayer: () -> Unit, local: () -> Unit, playlists: () -> Unit, text: Color, muted: Color, surface: Color, accent: Color, backdrop: Backdrop?) { Page(Modifier.fillMaxSize().padding(inset)) { item { Header("现在听", "你的音乐，按你的方式播放", null, text, muted) }; if (current != null) item { Section("正在播放", text, accent); NowPlaying(current, playing, playPause, tapPlayer, text, muted, surface, accent, backdrop) }; item { Section("最近添加", text, accent, "查看全部", local) }; if (songs.isEmpty()) item { Empty(local, text, muted, accent) } else item { AlbumRow(songs.take(12), onSong, text, muted) }; item { Section("快速访问", text, accent) }; item { Row(Modifier.padding(horizontal = 20.dp)) { AccessCard("本地歌曲", Icons.Default.MusicNote, local, surface, text, accent, Modifier.fillMaxWidth(), backdrop) } } } }
@Composable private fun UnifiedLibrary(inset: PaddingValues, songs: List<Song>, onSong: (Song) -> Unit, local: () -> Unit, playlists: () -> Unit, webdav: () -> Unit, text: Color, muted: Color, surface: Color, accent: Color, backdrop: Backdrop?) {
    var query by rememberSaveable { mutableStateOf("") }
    val uniqueSongs = remember(songs) { songs.distinctBy { it.id } }
    val results = remember(query, uniqueSongs) { if (query.isBlank()) emptyList() else uniqueSongs.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) } }
    val albums = remember(uniqueSongs) { uniqueSongs.distinctBy { it.album.trim().lowercase() } }
    val artists = remember(uniqueSongs) { uniqueSongs.map { it.artist.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sorted() }
    Page(Modifier.fillMaxSize().padding(inset)) {
        item { Header("资料库", "浏览、广播与音乐内容集中在这里", null, text, muted) }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp).museGlass(backdrop, RoundedCornerShape(14.dp), surface.copy(alpha = .42f)), placeholder = { Text("搜索歌曲、艺术家或专辑") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Cancel, "清除") } }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = muted.copy(alpha = .22f), focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, cursorColor = accent)) }
        if (query.isNotBlank()) {
            if (results.isEmpty()) item { Text("没有找到“$query”", color = muted, modifier = Modifier.padding(20.dp)) }
            else items(results, key = { it.id }) { SongResult(it, { onSong(it) }, text, muted, accent) }
        } else {
            item { Section("快速访问", text, accent) }
            item {
                Column(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                        .museGlass(backdrop, RoundedCornerShape(22.dp), surface.copy(alpha = .38f))
                        .clip(RoundedCornerShape(22.dp)).padding(vertical = 4.dp)
                ) {
                    QuickAccessItem(Icons.Default.MusicNote, "歌曲 · ${uniqueSongs.size}", local, text, muted, accent, true)
                    QuickAccessItem(Icons.Default.QueueMusic, "播放列表", playlists, text, muted, accent, true)
                    QuickAccessItem(Icons.Default.CloudDownload, "WebDAV 音乐源", webdav, text, muted, accent, false)
                }
            }
            if (albums.isNotEmpty()) { item { Section("专辑 · ${albums.size}", text, accent, "全部歌曲", local) }; item { AlbumRow(albums, onSong, text, muted, 164.dp) } }
            if (artists.isNotEmpty()) { item { Section("艺术家 · ${artists.size}", text, accent) }; item { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(artists) { artist -> AssistChip(onClick = { query = artist }, label = { Text(artist, maxLines = 1) }, leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(18.dp), tint = accent) }) } } } }
            item { Section("广播", text, accent) }
            item { RadioCard("本地电台", "从设备歌曲生成连续播放队列", Icons.Default.Radio, local, surface, text, muted, accent, backdrop) }
        }
    }
}

@Composable private fun ProfileTab(inset: PaddingValues, name: String, avatar: Uri?, listeningTimeMs: Long, completedPlays: Int, repeatCount: Int, songCount: Int, pickAvatar: () -> Unit, changeName: (String) -> Unit, settings: () -> Unit, text: Color, muted: Color, surface: Color, accent: Color, backdrop: Backdrop?) {
    val hours = listeningTimeMs / 3_600_000
    val minutes = listeningTimeMs / 60_000 % 60
    var editingName by rememberSaveable { mutableStateOf(false) }
    var draftName by remember(name) { mutableStateOf(name) }
    Page(Modifier.fillMaxSize().padding(inset)) {
        item { Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("个人", color = text, fontSize = 34.sp, fontWeight = FontWeight.Bold); Text("你的聆听轨迹", color = muted, fontSize = 13.sp) }; IconButton(settings) { Icon(Icons.Default.Settings, "设置", tint = accent, modifier = Modifier.size(27.dp)) } } }
        item {
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                    .museGlass(backdrop, RoundedCornerShape(28.dp), surface.copy(.65f))
                    .clip(RoundedCornerShape(28.dp)).padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(76.dp).clip(androidx.compose.foundation.shape.CircleShape).background(accent.copy(.15f)).clickable(onClick = pickAvatar), contentAlignment = Alignment.Center) {
                        if (avatar != null) AsyncImage(avatar, "头像", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.Person, null, tint = accent, modifier = Modifier.size(42.dp))
                    }
                    Column(Modifier.padding(start = 16.dp).weight(1f).clickable { editingName = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, color = text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Default.Edit, "修改名字", tint = accent, modifier = Modifier.size(20.dp))
                        }
                        Text("点击名字修改 · 点击头像更换", color = muted, fontSize = 12.sp)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 18.dp), color = muted.copy(alpha = .16f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("聆听速写", color = text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("把这段时间留给音乐", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Icon(Icons.Default.Headphones, null, tint = accent, modifier = Modifier.size(27.dp))
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileStat("${hours}h ${minutes}m", "聆听时间", Modifier.weight(1f), muted, accent)
                    ProfileStat(completedPlays.toString(), "完整播放", Modifier.weight(1f), muted, accent)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileStat(repeatCount.toString(), "循环时刻", Modifier.weight(1f), muted, accent)
                    ProfileStat(songCount.toString(), "私人曲库", Modifier.weight(1f), muted, accent)
                }
            }
        }
    }
    if (editingName) AlertDialog(onDismissRequest = { editingName = false }, title = { Text("修改个人名字") }, text = { OutlinedTextField(draftName, { draftName = it.take(24) }, singleLine = true, label = { Text("名字") }) }, confirmButton = { TextButton({ changeName(draftName); editingName = false }) { Text("保存", color = accent) } }, dismissButton = { TextButton({ editingName = false }) { Text("取消") } }, shape = RoundedCornerShape(24.dp))
}

@Composable private fun ProfileStat(value: String, label: String, modifier: Modifier, muted: Color, accent: Color) { Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .055f)).padding(vertical = 14.dp, horizontal = 10.dp), horizontalAlignment = Alignment.Start) { Text(value, color = accent, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1); Text(label, color = muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1) } }
@Composable private fun Section(title: String, text: Color, accent: Color, action: String? = null, onAction: (() -> Unit)? = null) { Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, color = text, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); if (action != null && onAction != null) Text(action, color = accent, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onAction).padding(6.dp)) } }
@Composable private fun DividerLine(muted: Color) { HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = muted.copy(alpha = .18f)) }
@Composable private fun AlbumArt(song: Song, modifier: Modifier, muted: Color, contentDescription: String? = null) { Box(modifier.clip(RoundedCornerShape(10.dp)).background(muted.copy(alpha = .16f)), contentAlignment = Alignment.Center) { AlbumArtwork(song, Modifier.matchParentSize(), contentDescription) } }
@Composable private fun AlbumRow(songs: List<Song>, onSong: (Song) -> Unit, text: Color, muted: Color, width: Dp = 150.dp) { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { items(songs, key = { it.id }) { song -> Column(Modifier.width(width).clickable { onSong(song) }) { AlbumArt(song, Modifier.fillMaxWidth().aspectRatio(1f), muted, song.title); Text(song.title, color = text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp)); Text(song.artist, color = muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } } } }
@Composable private fun NowPlaying(song: Song, playing: Boolean, playPause: () -> Unit, tap: () -> Unit, text: Color, muted: Color, surface: Color, accent: Color, backdrop: Backdrop?) { val shape = RoundedCornerShape(18.dp); Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().museGlass(backdrop, shape, surface.copy(alpha = .18f), 20.dp).clip(shape).clickable(onClick = tap).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { AlbumArt(song, Modifier.size(64.dp), muted, song.title); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(song.title, color = text, fontWeight = FontWeight.SemiBold, maxLines = 1); Text(song.artist, color = muted, fontSize = 13.sp, maxLines = 1) }; IconButton(playPause) { Icon(if (playing) Icons.Default.PauseCircle else Icons.Default.PlayCircle, "播放/暂停", tint = accent, modifier = Modifier.size(32.dp)) } } }
@Composable private fun AccessCard(title: String, icon: ImageVector, onClick: () -> Unit, surface: Color, text: Color, accent: Color, modifier: Modifier, backdrop: Backdrop?) { val shape = RoundedCornerShape(18.dp); Column(modifier.height(104.dp).museGlass(backdrop, shape, surface.copy(alpha = .16f), 20.dp).clip(shape).clickable(onClick = onClick).padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) { Icon(icon, null, tint = accent); Text(title, color = text, fontWeight = FontWeight.SemiBold) } }
@Composable private fun BrowseCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, surface: Color, text: Color, muted: Color, tint: Color, backdrop: Backdrop?) { val shape = RoundedCornerShape(14.dp); Row(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth().height(90.dp).museGlass(backdrop, shape, surface.copy(alpha = .16f), 20.dp).clip(shape).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = tint, modifier = Modifier.size(32.dp)); Column(Modifier.padding(start = 15.dp)) { Text(title, color = text, fontWeight = FontWeight.Bold); Text(subtitle, color = muted, fontSize = 13.sp) } } }
@Composable private fun RadioCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, surface: Color, text: Color, muted: Color, tint: Color, backdrop: Backdrop?) { val shape = RoundedCornerShape(16.dp); Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().museGlass(backdrop, shape, tint.copy(alpha = .20f), 22.dp).clip(shape).clickable(onClick = onClick).padding(19.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = tint, modifier = Modifier.size(38.dp)); Column(Modifier.weight(1f).padding(horizontal = 16.dp)) { Text(title, color = text, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = muted, fontSize = 12.sp) }; Icon(Icons.Default.PlayArrow, null, tint = tint) } }
@Composable private fun LibraryItem(icon: ImageVector, title: String, onClick: () -> Unit, text: Color, muted: Color, accent: Color) { Column(Modifier.padding(horizontal = 20.dp)) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp)); Text(title, color = text, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(start = 15.dp)); Icon(Icons.Default.ChevronRight, null, tint = muted) }; DividerLine(muted) } }
@Composable private fun QuickAccessItem(icon: ImageVector, title: String, onClick: () -> Unit, text: Color, muted: Color, accent: Color, divider: Boolean) { Column(Modifier.padding(horizontal = 16.dp)) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp)); Text(title, color = text, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(start = 15.dp)); Icon(Icons.Default.ChevronRight, null, tint = muted) }; if (divider) HorizontalDivider(color = muted.copy(alpha = .18f)) } }
@Composable private fun SongResult(song: Song, onClick: () -> Unit, text: Color, muted: Color, accent: Color) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { AlbumArt(song, Modifier.size(52.dp), muted, song.title); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(song.title, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${song.artist} · ${song.album}", color = muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Icon(Icons.Default.ChevronRight, null, tint = muted) } }
@Composable private fun Empty(open: () -> Unit, text: Color, muted: Color, accent: Color) { Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.LibraryMusic, null, tint = accent, modifier = Modifier.size(56.dp)); Text("这里还没有音乐", color = text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)); Text("扫描设备音频后会显示在这里", color = muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp)); TextButton(open) { Text("打开媒体库", color = accent) } } }
