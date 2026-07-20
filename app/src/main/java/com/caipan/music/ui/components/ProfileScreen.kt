package com.caipan.music.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RepeatOne
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
import com.kyant.backdrop.Backdrop

@Composable
fun ProfileScreen(
    name: String, avatar: Uri?, listeningTimeMs: Long, completedPlays: Int, repeatCount: Int,
    songCount: Int, repeatSongs: List<Pair<Song, Int>>, accent: Color, isLightTheme: Boolean,
    backdrop: Backdrop?, onNameChange: (String) -> Unit, onPickAvatar: () -> Unit, onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(name) { mutableStateOf(name) }
    val hours = listeningTimeMs / 3_600_000
    val minutes = listeningTimeMs / 60_000 % 60
    val glassTint = if (isLightTheme) Color.White.copy(.62f)
        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(.54f)

    FullScreenGlassRoute(backdrop, isLightTheme) {
        Box(Modifier.fillMaxSize()) {
            AmbientGlow(Modifier.size(360.dp).offset(x = 120.dp, y = (-110).dp), accent.copy(.30f))
            AmbientGlow(Modifier.size(280.dp).offset(x = (-120).dp, y = 430.dp), Color(0xFF7657FF).copy(.18f))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .statusBarsPadding().padding(bottom = 40.dp)
            ) {
                Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "返回") }
                    Spacer(Modifier.weight(1f))
                    Text("MUSE PROFILE", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.8.sp, modifier = Modifier.padding(end = 18.dp))
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("你的音乐，\n正在成为你。", fontSize = 38.sp, lineHeight = 42.sp,
                        fontWeight = FontWeight.Black, letterSpacing = (-1.2).sp)
                    Text("不是统计表，是一段只属于你的聆听轨迹。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 24.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
                            .museGlass(backdrop, RoundedCornerShape(32.dp), glassTint)
                    ) {
                        Box(Modifier.matchParentSize().background(Brush.linearGradient(
                            listOf(accent.copy(.24f), Color.Transparent, Color(0xFF7657FF).copy(.12f)))))
                        Column(Modifier.padding(22.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(92.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable(onClick = onPickAvatar), contentAlignment = Alignment.Center
                                ) {
                                    if (avatar != null) AsyncImage(avatar, "头像", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                                    else Icon(Icons.Default.Person, null, Modifier.size(48.dp), tint = accent)
                                    Box(
                                        Modifier.align(Alignment.BottomEnd).size(29.dp).clip(CircleShape).background(accent),
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Default.Edit, "更换头像", Modifier.size(15.dp), tint = Color.White) }
                                }
                                Column(Modifier.padding(start = 18.dp).weight(1f)) {
                                    Text(name, fontSize = 27.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { editing = true })
                                    Text("LOCAL LISTENER · MUSE", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            HorizontalDivider(Modifier.padding(top = 22.dp, bottom = 16.dp), color = Color.White.copy(.12f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("聆听速写", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("把这段时间留给音乐", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                                Icon(Icons.Default.Headphones, null, tint = accent.copy(.78f), modifier = Modifier.size(30.dp))
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ProfileStat("${hours}h ${minutes}m", "累计聆听", Modifier.weight(1f), accent)
                                ProfileStat(completedPlays.toString(), "完整播放", Modifier.weight(1f), accent)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ProfileStat(repeatCount.toString(), "循环时刻", Modifier.weight(1f), accent)
                                ProfileStat(songCount.toString(), "私人曲库", Modifier.weight(1f), accent)
                            }
                        }
                    }
                    if (repeatSongs.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text("反复爱上的歌", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("循环不是重复，是舍不得结束", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.RepeatOne, null, tint = accent)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeatSongs.take(5).forEachIndexed { index, (song, count) ->
                                RepeatSongCard(index, song, count, accent, glassTint, backdrop)
                            }
                        }
                    }
                }
            }
        }
    }
    if (editing) AlertDialog(
        onDismissRequest = { editing = false },
        title = { DialogBlurEffect(); Text("给这份档案一个名字") },
        text = { OutlinedTextField(draft, { draft = it.take(24) }, singleLine = true) },
        confirmButton = { TextButton({ onNameChange(draft); editing = false }) { Text("保存") } },
        dismissButton = { TextButton({ editing = false }) { Text("取消") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .94f),
        tonalElevation = 8.dp, shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun AmbientGlow(modifier: Modifier, color: Color) {
    Box(modifier.background(Brush.radialGradient(listOf(color, Color.Transparent)), CircleShape))
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier, accent: Color) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .055f))
            .padding(vertical = 13.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(value, color = accent, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
            maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun RepeatSongCard(index: Int, song: Song, count: Int, accent: Color, tint: Color, backdrop: Backdrop?) {
    Row(
        Modifier.fillMaxWidth().museGlass(backdrop, RoundedCornerShape(20.dp), tint).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AlbumArtwork(song, Modifier.size(62.dp).clip(RoundedCornerShape(15.dp)))
            Box(
                Modifier.align(Alignment.TopStart).offset((-5).dp, (-5).dp).size(24.dp)
                    .clip(CircleShape).background(accent), contentAlignment = Alignment.Center
            ) { Text("${index + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$count", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("次循环", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}
