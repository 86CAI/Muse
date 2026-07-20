package com.caipan.music.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.caipan.music.model.Song
import com.caipan.music.plugin.BlurLocation
import com.kyant.backdrop.Backdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistName: String,
    songs: List<Song>,
    coverUri: String? = null,
    accentColor: Color = Color(0xFFFA2D48),
    isLightTheme: Boolean = false,
    onSongTap: (Int) -> Unit,
    onRemoveSong: (Long) -> Unit,
    onSongMore: (Song) -> Unit = {},
    onChangeCover: () -> Unit = {},
    onDismiss: () -> Unit,
    backdrop: Backdrop? = null
) {
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(songs) { isLoading = false }

    FullScreenGlassRoute(backdrop = backdrop, isLightTheme = isLightTheme) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ── Top bar ──
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, "返回", tint = textPrimary, modifier = Modifier.size(24.dp))
                }
            }

            if (isLoading && songs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    // ── Header: cover + title + play all ──
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(132.dp).clip(RoundedCornerShape(22.dp))
                                    .background(Brush.linearGradient(
                                        listOf(accentColor, accentColor.copy(alpha = 0.6f))))
                                    .clickable { onChangeCover() },
                                    contentAlignment = Alignment.Center) {
                                    if (coverUri != null) {
                                        AsyncImage(model = coverUri, contentDescription = null,
                                            modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                                    } else {
                                        Icon(Icons.Default.QueueMusic, null, tint = Color.White,
                                            modifier = Modifier.size(44.dp))
                                    }
                                    // small edit badge
                                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomEnd) {
                                        Box(Modifier.padding(4.dp).size(24.dp).clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                            contentAlignment = Alignment.Center) {
                                            Text("✎", color = Color.White, fontSize = 13.sp)
                                        }
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(playlistName, color = textPrimary, fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    Text(songs.size.toString() + " 首歌曲", color = textSecondary, fontSize = 14.sp)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            if (songs.isNotEmpty()) {
                                Button(onClick = { onSongTap(0) },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                    MusicPlayIcon(Modifier.size(22.dp), Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text("播放全部", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    if (songs.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.MusicOff, null, tint = textSecondary, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text("歌单为空", color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(4.dp))
                                    Text("通过批量选择添加歌曲", color = textSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        itemsIndexed(songs) { index, song ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
                                    .museGlass(
                                        backdrop, RoundedCornerShape(14.dp),
                                        MaterialTheme.colorScheme.surface.copy(alpha = .24f),
                                        location = BlurLocation.CARDS
                                    )
                                    .clickable { onSongTap(index) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                                    .background(Color.Gray.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center) {
                                    AlbumArtwork(song, Modifier.matchParentSize())
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(song.title, color = textPrimary,
                                        fontWeight = FontWeight.Medium, fontSize = 15.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(2.dp))
                                    Text(song.artist, color = textSecondary, fontSize = 13.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(song.formattedDuration, color = textSecondary, fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 8.dp))
                                IconButton(onClick = { onSongMore(song) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.MoreVert, "更多", tint = textSecondary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
    }
}
