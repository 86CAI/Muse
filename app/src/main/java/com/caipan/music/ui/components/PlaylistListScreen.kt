package com.caipan.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.caipan.music.data.Playlist
import com.caipan.music.plugin.BlurLocation
import com.kyant.backdrop.Backdrop

private val coverGradients = listOf(
    listOf(Color(0xFFFA2D48), Color(0xFFFF765F)),
    listOf(Color(0xFF0A84FF), Color(0xFF5AC8FA)),
    listOf(Color(0xFFAF52DE), Color(0xFFFF6482)),
    listOf(Color(0xFFFF9500), Color(0xFFFFCC00)),
    listOf(Color(0xFFFC3C44), Color(0xFFFF6482)),
    listOf(Color(0xFF5856D6), Color(0xFF0A84FF))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistListScreen(
    playlists: List<Playlist>,
    accentColor: Color = Color(0xFFFA2D48),
    isLightTheme: Boolean = false,
    onPlaylistTap: (Playlist) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onWebdavImport: () -> Unit,
    onDismiss: () -> Unit,
    backdrop: Backdrop? = null
) {
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBg = MaterialTheme.colorScheme.surfaceContainerHigh

    var pendingDelete by remember { mutableStateOf<Playlist?>(null) }

    FullScreenGlassRoute(backdrop = backdrop, isLightTheme = isLightTheme) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ── Top bar ──
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, "返回", tint = textPrimary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.weight(1f))
                // WebDAV import pill button
                Surface(onClick = onWebdavImport, shape = CircleShape,
                    color = accentColor.copy(alpha = 0.15f)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, null, tint = accentColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("导入", color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── Large title ──
            Text("我的歌单", color = textPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp))
            Text(playlists.size.toString() + " 个歌单", color = textSecondary, fontSize = 15.sp,
                modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))

            if (playlists.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 80.dp)) {
                        Box(Modifier.size(96.dp).clip(CircleShape).background(cardBg),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.QueueMusic, null, tint = textSecondary,
                                modifier = Modifier.size(46.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("还没有歌单", color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("在歌曲列表中多选歌曲，添加到歌单\n或点击右上角从 WebDAV 导入",
                            color = textSecondary, fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 20.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    itemsWithIndex(playlists) { index, pl ->
                        val grad = coverGradients[index % coverGradients.size]
                        Row(
                            Modifier.fillMaxWidth()
                                .museGlass(
                                    backdrop, RoundedCornerShape(16.dp),
                                    MaterialTheme.colorScheme.surface.copy(alpha = .28f),
                                    location = BlurLocation.CARDS
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPlaylistTap(pl) }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Gradient cover or custom image
                            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                                .background(Brush.linearGradient(grad)),
                                contentAlignment = Alignment.Center) {
                                if (pl.coverUri != null) {
                                    AsyncImage(model = pl.coverUri, contentDescription = null,
                                        modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Default.MusicNote, null, tint = Color.White,
                                        modifier = Modifier.size(26.dp))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pl.name, color = textPrimary, fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(3.dp))
                                Text(pl.songIds.size.toString() + " 首歌曲", color = textSecondary, fontSize = 13.sp)
                            }
                            IconButton(onClick = { pendingDelete = pl }) {
                                Icon(Icons.Default.Delete, "删除歌单",
                                    tint = textSecondary, modifier = Modifier.size(20.dp))
                            }
                        }
                        if (index < playlists.size - 1) {
                            HorizontalDivider(
                                color = textSecondary.copy(alpha = 0.12f),
                                modifier = Modifier.padding(start = 86.dp))
                        }
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
    }

    pendingDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除歌单？") },
            text = { Text("“${playlist.name}”将被删除，歌曲文件不会受到影响。") },
            confirmButton = {
                TextButton(onClick = { onDeletePlaylist(playlist.id); pendingDelete = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

private inline fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsWithIndex(
    items: List<T>,
    crossinline itemContent: @androidx.compose.runtime.Composable (index: Int, item: T) -> Unit
) {
    items(items.size) { index -> itemContent(index, items[index]) }
}
