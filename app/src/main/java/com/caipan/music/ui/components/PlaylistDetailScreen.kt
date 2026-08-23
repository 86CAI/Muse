package com.caipan.music.ui.components

import android.graphics.ImageDecoder
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.caipan.music.R
import com.caipan.music.model.Song
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 采样图片平均亮度（0-1），失败返回 null。 */
private suspend fun computeImageLuminance(contentResolver: android.content.ContentResolver, uri: android.net.Uri): Float? =
    withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val maxDim = maxOf(info.size.width, info.size.height)
                if (maxDim > 64) decoder.setTargetSampleSize(maxDim / 64)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            var sum = 0.0
            for (p in px) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                sum += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            }
            if (!bmp.isRecycled) bmp.recycle()
            (sum / px.size.coerceAtLeast(1)).toFloat()
        }.getOrNull()
    }

/**
 * 新版歌单详情页：
 * - 封面模糊全页背景 + 上下羽化遮罩（Apple Music 式融合）
 * - 文字/图标按背景亮度动态切换黑/白
 * - 悬浮胶囊搜索框（上下羽化），搜索时隐藏歌单头仅显结果
 * - 右上角单按钮（分享/更换封面），控制行 随机 + 播放
 */
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
    /** 顺序播放全部；默认退化为 onSongTap(0)。 */
    onPlayAll: () -> Unit = { if (songs.isNotEmpty()) onSongTap(0) },
    onShufflePlay: () -> Unit = {},
    onShare: () -> Unit = {},
    onDismiss: () -> Unit,
    backdrop: Backdrop? = null
) {
    val context = LocalContext.current
    // 封面亮度采样：决定文字黑/白与遮罩方向
    var coverLuminance by remember(coverUri) { mutableStateOf<Float?>(null) }
    LaunchedEffect(coverUri) {
        coverLuminance = coverUri?.let { computeImageLuminance(context.contentResolver, Uri.parse(it)) }
    }
    // 背景偏暗：有封面按封面亮度，无封面按主题
    val dark = if (coverUri != null) (coverLuminance ?: 0.5f) < 0.5f else !isLightTheme
    val scrim = if (dark) Color.Black else Color.White
    val textPrimary = if (dark) Color.White else Color(0xFF111111)
    val textSecondary = if (dark) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.62f)
    val controlBg = if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
    val controlTint = textPrimary

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filtered = remember(searchQuery, songs) {
        if (searchQuery.isBlank()) songs
        else songs.filter {
            it.title.contains(searchQuery, true) ||
                it.artist.contains(searchQuery, true) ||
                it.album.contains(searchQuery, true)
        }
    }
    var moreMenuExpanded by remember { mutableStateOf(false) }

    FullScreenGlassRoute(backdrop = backdrop, isLightTheme = isLightTheme) {
        Box(Modifier.fillMaxSize()) {
            // ── 背景：封面模糊铺满全页（融合）；无封面时黑/白纯色 ──
            if (coverUri != null && !LocalMuseMonet.current) {
                AsyncImage(
                    model = coverUri, contentDescription = null,
                    modifier = Modifier.fillMaxSize().scale(1.25f).blur(60.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize().background(
                    if (LocalMuseMonet.current) MaterialTheme.colorScheme.background
                    else if (dark) Color(0xFF101014) else Color(0xFFF2F2F4)
                ))
            }
            // 顶部遮罩：保证返回键/搜索区可读
            Box(
                Modifier.fillMaxWidth().height(170.dp)
                    .background(Brush.verticalGradient(listOf(scrim.copy(alpha = 0.5f), Color.Transparent)))
            )
            // 底部遮罩：保证列表底部可读（越往下越实）
            Box(
                Modifier.fillMaxWidth().height(280.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, scrim.copy(alpha = 0.55f), scrim.copy(alpha = 0.8f))))
            )

            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                // ── 顶部导航行：返回 + 右上单按钮（更多）──
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MuseIconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_apple_arrow_left), "返回", tint = textPrimary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    // 单胶囊按钮：更多（分享 / 更换封面）
                    Box(Modifier.clip(RoundedCornerShape(50)).background(controlBg)) {
                        MuseIconButton(onClick = { moreMenuExpanded = true }) {
                            Icon(painterResource(R.drawable.ic_apple_more), "更多", tint = accentColor, modifier = Modifier.size(21.dp))
                        }
                        DropdownMenu(expanded = moreMenuExpanded, onDismissRequest = { moreMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("分享歌单") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_apple_share), null) },
                                onClick = { moreMenuExpanded = false; onShare() }
                            )
                            DropdownMenuItem(
                                text = { Text("更换封面") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_apple_image), null) },
                                onClick = { moreMenuExpanded = false; onChangeCover() }
                            )
                        }
                    }
                }

                // ── 搜索区：悬浮胶囊 ──
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    // 悬浮胶囊搜索框
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        placeholder = { Text("在歌单中搜索", color = textSecondary) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_apple_search), null, tint = textSecondary, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(painterResource(R.drawable.ic_apple_x), "清除", tint = textSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = textPrimary, fontSize = 14.sp),
                        shape = RoundedCornerShape(50),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = scrim.copy(alpha = 0.30f),
                            unfocusedContainerColor = scrim.copy(alpha = 0.30f),
                            cursorColor = accentColor
                        )
                    )
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    // ── 歌单头（搜索时隐藏，仅显示结果）──
                    if (searchQuery.isBlank()) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 大封面（居中，点击换封面；MeloX Hero 风格：0.68×宽自适应 + 12dp 圆角 + 深阴影）
                                Box(
                                    Modifier.fillMaxWidth(0.68f).aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .shadow(18.dp, RoundedCornerShape(12.dp))
                                        .background(Brush.linearGradient(listOf(accentColor, accentColor.copy(alpha = 0.6f))))
                                        .clickable { onChangeCover() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (coverUri != null) {
                                        AsyncImage(
                                            model = coverUri, contentDescription = null,
                                            modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            painterResource(R.drawable.ic_apple_queue), null, tint = Color.White,
                                            modifier = Modifier.size(72.dp)
                                        )
                                    }
                                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomEnd) {
                                        Box(
                                            Modifier.padding(10.dp).size(30.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.45f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("✎", color = Color.White, fontSize = 15.sp)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    playlistName, color = textPrimary,
                                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(7.dp))
                                Text("${songs.size} 首歌曲", color = textSecondary, fontSize = 15.sp)
                                Spacer(Modifier.height(20.dp))
                                // 控制行：随机 + 播放（MeloX Hero 风格：54dp 圆按钮 + 140×50 胶囊播放）
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(54.dp).clip(CircleShape).background(controlBg)
                                            .clickable { onShufflePlay() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(painterResource(R.drawable.ic_apple_shuffle), "随机播放", tint = controlTint, modifier = Modifier.size(26.dp))
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Box(
                                        Modifier.width(140.dp).height(50.dp).clip(RoundedCornerShape(25.dp))
                                            .background(accentColor)
                                            .clickable { onPlayAll() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            MusicPlayIcon(Modifier.size(20.dp), Color.White)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "播放", color = Color.White,
                                                fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val emptyStatePainter = if (searchQuery.isNotBlank()) {
                                        painterResource(R.drawable.ic_apple_search_off)
                                    } else {
                                        painterResource(R.drawable.ic_apple_music_off)
                                    }
                                    Icon(
                                        emptyStatePainter,
                                        null, tint = textSecondary, modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        if (searchQuery.isNotBlank()) "没有找到匹配的歌曲" else "歌单为空",
                                        color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        if (searchQuery.isNotBlank()) "换个关键词试试" else "通过批量选择添加歌曲",
                                        color = textSecondary, fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    } else {
                        // ── 数字编号歌曲列表 ──
                        itemsIndexed(filtered, key = { _, song -> song.id }) { _, song ->
                            val originalIndex = remember(song.id, songs) {
                                songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                            }
                            Row(
                                Modifier.fillMaxWidth()
                                    .pressScale()
                                    .padding(start = 20.dp, end = 8.dp)
                                    .clickable { onSongTap(originalIndex) }
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${originalIndex + 1}", color = textSecondary,
                                    fontSize = 15.sp, modifier = Modifier.width(30.dp)
                                )
                                AlbumArtwork(song, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        song.title, color = textPrimary,
                                        fontWeight = FontWeight.Medium, fontSize = 15.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onSongMore(song) }) {
                                    Icon(
                                        painterResource(R.drawable.ic_apple_more), "更多",
                                        tint = textSecondary, modifier = Modifier.size(20.dp)
                                    )
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
