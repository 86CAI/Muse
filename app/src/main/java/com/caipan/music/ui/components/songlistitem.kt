// UI structure adapted from Symphony SongCard.kt (AGPL-3.0-only).
// Source: https://github.com/zyrouge/symphony @ dd04b872b8b4e6dd56172c053a5776c4d56ad080
package com.caipan.music.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.caipan.music.model.Song
import com.kyant.backdrop.Backdrop

@Composable
fun DefaultRecordArtwork(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(modifier.background(Color(0xFF171719)), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            drawCircle(Color(0xFF242428))
            listOf(0.42f, 0.32f, 0.22f).forEach { ratio ->
                drawCircle(Color.White.copy(alpha = 0.12f), radius = size.minDimension * ratio,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
            drawCircle(tint.copy(alpha = 0.9f), radius = size.minDimension * 0.13f)
            drawCircle(Color(0xFF171719), radius = size.minDimension * 0.045f)
        }
    }
}

@Composable
fun AlbumArtwork(song: Song, modifier: Modifier = Modifier, contentDescription: String? = null, artUriOverride: Uri? = null) {
    val art = artUriOverride ?: song.albumArtUri
    if (art == null) {
        DefaultRecordArtwork(modifier)
    } else {
        SubcomposeAsyncImage(model = art, contentDescription = contentDescription, modifier = modifier,
            contentScale = ContentScale.Crop) {
            when (painter.state) {
                is coil.compose.AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                else -> DefaultRecordArtwork(Modifier.matchParentSize())
            }
        }
    }
}

@Composable
fun SongListItem(
    song: Song, isCurrentSong: Boolean, isPlaying: Boolean = false, onClick: () -> Unit,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    subTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    batchMode: Boolean = false, isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {}, onMore: () -> Unit = {}, modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        onClick = if (batchMode) onToggleSelect else onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (batchMode) {
                Checkbox(isSelected, { onToggleSelect() }, colors = CheckboxDefaults.colors(checkedColor = accentColor))
                Spacer(Modifier.width(8.dp))
            }
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AlbumArtwork(song, Modifier.matchParentSize())
                if (isCurrentSong) Box(Modifier.matchParentSize().background(accentColor.copy(alpha = .72f)), contentAlignment = Alignment.Center) {
                    if (isPlaying) PlayingIndicator(Color.White) else Icon(Icons.Default.MusicNote, null, tint = Color.White)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = if (isCurrentSong) accentColor else textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · "), color = subTextColor, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "更多", tint = subTextColor) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    song: Song,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    backdrop: Backdrop? = null
) {
    var showDetails by remember { mutableStateOf(false) }
    if (!showDetails) ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = .18f),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            Modifier.fillMaxWidth()
                .museGlass(backdrop, sheetShape, MaterialTheme.colorScheme.surface.copy(alpha = .34f))
                .navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)
        ) {
            DialogBlurEffect()
            Row(verticalAlignment = Alignment.CenterVertically) {
                AlbumArtwork(song, Modifier.size(58.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(song.title, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val transparentItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ListItem(headlineContent = { Text("添加到播放列表") }, leadingContent = { Icon(Icons.Default.PlaylistAdd, null) }, colors = transparentItemColors, modifier = Modifier.clickable(onClick = onAddToPlaylist))
            ListItem(headlineContent = { Text("歌曲详细信息") }, leadingContent = { Icon(Icons.Default.Info, null) }, colors = transparentItemColors, modifier = Modifier.clickable { showDetails = true })
            onRemoveFromPlaylist?.let { remove ->
                ListItem(headlineContent = { Text("从此歌单移除", color = MaterialTheme.colorScheme.error) }, leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, colors = transparentItemColors, modifier = Modifier.clickable(onClick = remove))
            }
        }
    }
    if (showDetails) SongDetailsDialog(song, backdrop) { showDetails = false }
}

@Composable
private fun SongDetailsDialog(song: Song, backdrop: Backdrop?, onDismiss: () -> Unit) {
    val size = if (song.sizeBytes > 0) "%.1f MB".format(song.sizeBytes / 1024f / 1024f) else "未知"
    val bitrate = if (song.bitrate > 0) "${song.bitrate / 1000}" else "—"
    val sampleRate = if (song.sampleRate > 0) "${song.sampleRate / 1000f}".removeSuffix(".0") else "—"
    val liquidGlass = LocalMuseLiquidGlass.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        DialogBlurEffect(if (liquidGlass) 16 else 36)
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(.22f)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).statusBarsPadding().navigationBarsPadding()
                    .museGlass(
                        backdrop,
                        RoundedCornerShape(34.dp),
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(.42f)
                    )
                    .clip(RoundedCornerShape(34.dp))
                    .clickable(enabled = false) {}
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Box(Modifier.fillMaxWidth().height(290.dp)) {
                    AlbumArtwork(song, Modifier.matchParentSize())
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                        Color.Black.copy(.06f),
                        Color.Black.copy(.22f),
                        if (liquidGlass) Color.Black.copy(.18f) else MaterialTheme.colorScheme.surfaceContainerHigh
                    ))))
                    Surface(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(38.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = Color.Black.copy(.38f)
                    ) { Box(contentAlignment = Alignment.Center) { Text("×", color = Color.White, fontSize = 25.sp, lineHeight = 25.sp) } }
                    Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 22.dp, vertical = 18.dp)) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)) {
                            Text("${song.formatLabel} · LOCAL MASTER", color = Color.White, fontSize = 10.sp,
                                fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                        Text(song.title, color = Color.White, fontSize = 29.sp, lineHeight = 32.sp,
                            fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 10.dp))
                        Text(listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · "),
                            color = Color.White.copy(.72f), fontSize = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MasterMetric(song.formattedDuration, "DURATION", Modifier.weight(1f))
                    MasterMetric(sampleRate, "kHz", Modifier.weight(1f))
                    MasterMetric(bitrate, "kbps", Modifier.weight(1f))
                    MasterMetric(size, "SIZE", Modifier.weight(1f))
                }

                Text("关于这份声音", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 10.dp))
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                        .museGlass(
                            backdrop,
                            RoundedCornerShape(22.dp),
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(.42f)
                        )
                        .clip(RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp)
                ) {
                    DetailLine("文件", song.fileName.ifBlank { "未知" })
                    DetailLine("编码", song.mimeType.ifBlank { song.formatLabel })
                    DetailLine("专辑", song.album.ifBlank { "未知专辑" })
                    DetailLine("艺人", song.artist.ifBlank { "未知艺人" }, divider = false)
                }

                Text("收藏于", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 22.dp, top = 22.dp, bottom = 7.dp))
                Text(song.filePath.ifBlank { song.folderPath }, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 22.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(14.dp)) {
                    Text("完成", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MasterMetric(value: String, unit: String, modifier: Modifier) {
    val liquidGlass = LocalMuseLiquidGlass.current
    Column(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(
                if (liquidGlass) MaterialTheme.colorScheme.onSurface.copy(alpha = .055f)
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(.76f)
            )
            .padding(vertical = 13.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.Black,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = .4.sp, maxLines = 1)
    }
}

@Composable
private fun DetailLine(label: String, value: String, divider: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.width(54.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(.08f))
}

@Composable private fun PlayingIndicator(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(16.dp)) {
        listOf(9, 15, 11).forEach { Box(Modifier.width(3.dp).height(it.dp).clip(RoundedCornerShape(2.dp)).background(color)) }
    }
}
