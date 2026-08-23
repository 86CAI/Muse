package com.caipan.music.ui.components

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.caipan.music.R
import com.caipan.music.model.Song
import com.caipan.music.plugin.FrameRateScene
import com.caipan.music.ui.effects.rememberSceneFrameThrottle
import com.kyant.backdrop.Backdrop
import kotlin.math.ceil
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val ProfileInk = Color(0xFFF7F7F3)
private val ProfileMuted = Color(0xFFAAA9A3)

@Composable
fun ProfileVisuals(
    inset: PaddingValues,
    name: String,
    avatar: Uri?,
    listeningTimeMs: Long,
    completedPlays: Int,
    repeatCount: Int,
    songs: List<Song>,
    onSong: (Song) -> Unit,
    pickAvatar: () -> Unit,
    changeName: (String) -> Unit,
    settings: () -> Unit,
    accent: Color,
    isLightTheme: Boolean,
    backdrop: Backdrop?
) {
    ProfileContent(
        inset = inset,
        name = name,
        avatar = avatar,
        listeningTimeMs = listeningTimeMs,
        completedPlays = completedPlays,
        repeatCount = repeatCount,
        songs = songs,
        onSong = onSong,
        pickAvatar = pickAvatar,
        changeName = changeName,
        settings = settings,
        accent = accent,
        isLightTheme = isLightTheme,
        backdrop = backdrop
    )
}

@Composable
private fun ProfileContent(
    inset: PaddingValues,
    name: String,
    avatar: Uri?,
    listeningTimeMs: Long,
    completedPlays: Int,
    repeatCount: Int,
    songs: List<Song>,
    onSong: (Song) -> Unit,
    pickAvatar: () -> Unit,
    changeName: (String) -> Unit,
    settings: () -> Unit,
    accent: Color,
    isLightTheme: Boolean,
    backdrop: Backdrop?
) {
    var editingName by rememberSaveable { mutableStateOf(false) }
    var draftName by remember(name) { mutableStateOf(name) }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val bottomInset = inset.calculateBottomPadding()
    val availableHeight = (screenHeight - bottomInset)
        .coerceAtLeast(320.dp)
    val initialGalleryHeight = availableHeight / 4
    val pageScrim = if (isLightTheme) Color.White.copy(alpha = .14f) else Color.Black.copy(alpha = .14f)
    val scrollState = rememberScrollState()
    val galleryHeight = remember(initialGalleryHeight, availableHeight) {
        mutableStateOf(initialGalleryHeight)
    }
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val pullGestureActive = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val latestOnRandomSong by rememberUpdatedState {
        songs.randomOrNull()?.let(onSong)
    }
    val pullRange = (availableHeight - initialGalleryHeight).coerceAtLeast(1.dp)
    val pullProgress = ((galleryHeight.value - initialGalleryHeight) / pullRange).coerceIn(0f, 1f)

    val settleGallery: () -> Unit = {
        settleJob.value?.cancel()
        settleJob.value = scope.launch {
            animate(
                initialValue = galleryHeight.value.value,
                targetValue = initialGalleryHeight.value,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { value, _ ->
                galleryHeight.value = value.dp
            }
        }
    }
    val finishPull: () -> Unit = {
        val finalProgress = ((galleryHeight.value - initialGalleryHeight) / pullRange)
            .coerceIn(0f, 1f)
        if (finalProgress >= .92f) latestOnRandomSong()
        pullGestureActive.value = false
        settleGallery()
    }
    val pullConnection = remember(initialGalleryHeight, availableHeight, density, scrollState) {
        object : NestedScrollConnection {
            private fun applyGalleryDrag(deltaPx: Float): Float {
                val minHeightPx = with(density) { initialGalleryHeight.toPx() }
                val maxHeightPx = with(density) { availableHeight.toPx() }
                val currentHeightPx = with(density) { galleryHeight.value.toPx() }

                if (deltaPx > 0f) {
                    settleJob.value?.cancel()
                    pullGestureActive.value = true
                    val currentProgress = ((galleryHeight.value - initialGalleryHeight) / pullRange)
                        .coerceIn(0f, 1f)
                    val resistance = if (currentProgress > .84f) .58f else .88f
                    val nextHeightPx = (currentHeightPx + deltaPx * resistance)
                        .coerceAtMost(maxHeightPx)
                    galleryHeight.value = with(density) { nextHeightPx.toDp() }
                    return deltaPx
                }

                if (deltaPx < 0f && currentHeightPx > minHeightPx) {
                    settleJob.value?.cancel()
                    pullGestureActive.value = true
                    val nextHeightPx = (currentHeightPx + deltaPx).coerceAtLeast(minHeightPx)
                    galleryHeight.value = with(density) { nextHeightPx.toDp() }
                    return nextHeightPx - currentHeightPx
                }

                return 0f
            }

            override fun onPreScroll(
                available: Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): Offset {
                if (available.y < 0f && galleryHeight.value > initialGalleryHeight) {
                    return Offset(0f, applyGalleryDrag(available.y))
                }
                if (available.y > 0f && scrollState.value == 0) {
                    return Offset(0f, applyGalleryDrag(available.y))
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): Offset {
                if (available.y > 0f && scrollState.value == 0) {
                    return Offset(0f, applyGalleryDrag(available.y))
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullGestureActive.value || galleryHeight.value > initialGalleryHeight) {
                    finishPull()
                    return Velocity(0f, available.y)
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (pullGestureActive.value) finishPull()
                return Velocity.Zero
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(pageScrim)
            .padding(bottom = bottomInset)
            .nestedScroll(pullConnection)
            .verticalScroll(scrollState)
    ) {
        ProfileGallery(
            songs = songs,
            accent = accent,
            height = galleryHeight.value,
            pullProgress = pullProgress,
            settings = settings
        )

        Column(Modifier.offset(y = (-72).dp)) {
            ProfileIdentity(
                avatar = avatar,
                name = name,
                pickAvatar = pickAvatar,
                onEditName = { draftName = name; editingName = true },
                foreground = if (isLightTheme) Color(0xFF171717) else ProfileInk,
                muted = if (isLightTheme) Color(0xFF6F6E69) else ProfileMuted,
                accent = accent
            )
            ProfileListeningCard(
                listeningTimeMs = listeningTimeMs,
                completedPlays = completedPlays,
                repeatCount = repeatCount,
                songCount = songs.size,
                accent = accent,
                foreground = if (isLightTheme) Color(0xFF171717) else ProfileInk,
                muted = if (isLightTheme) Color(0xFF6F6E69) else ProfileMuted
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (editingName) {
        MuseAlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("修改昵称") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    singleLine = true,
                    label = { Text("昵称") }
                )
            },
            confirmButton = {
                MuseTextButton(onClick = {
                    val updatedName = draftName.trim()
                    if (updatedName.isNotEmpty()) changeName(updatedName)
                    editingName = false
                }) { Text("保存") }
            },
            dismissButton = {
                MuseTextButton(onClick = { editingName = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ProfileGallery(
    songs: List<Song>,
    accent: Color,
    height: Dp,
    pullProgress: Float,
    settings: () -> Unit
) {
    val uniqueAlbums = remember(songs) {
        songs.distinctBy { song ->
            if (song.albumId > 0L) {
                "album:${song.albumId}"
            } else {
                "${song.album.trim().lowercase()}|${song.artist.trim().lowercase()}"
            }
        }
    }
    val cards: List<Song?> = if (uniqueAlbums.isEmpty()) List(16) { null } else uniqueAlbums
    val density = LocalDensity.current
    val statusBarSafeHeight = with(density) {
        WindowInsets.statusBars.getTop(density).toDp() + 12.dp
    }
    val isArmed = pullProgress >= .92f

    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Box(
            Modifier
                .matchParentSize()
        ) {
            ProfileDiagonalGrid(cards, statusBarSafeHeight)

            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Black.copy(alpha = .64f),
                                .30f to Color.Black.copy(alpha = .48f),
                                .56f to Color.Black.copy(alpha = .24f),
                                .78f to Color.Black.copy(alpha = .08f),
                                1f to Color.Transparent
                            )
                        )
                    )
            )
        }

        MuseIconButton(
            onClick = settings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 14.dp, end = 14.dp)
                .size(48.dp)
                .background(Color.Black.copy(alpha = .58f), CircleShape)
        ) {
            Icon(painterResource(R.drawable.ic_apple_settings), "设置", tint = ProfileInk, modifier = Modifier.size(24.dp))
        }

        if (pullProgress > .05f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = .58f + pullProgress * .16f),
                tonalElevation = 0.dp
            ) {
                Text(
                    text = if (isArmed) "松手随机播放" else "继续下拉 · 随机播放",
                    color = if (isArmed) accent else ProfileInk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileDiagonalGrid(songs: List<Song?>, statusBarSafeHeight: Dp) {
    val tileSize = 104.dp
    val gap = 10.dp
    val step = tileSize + gap
    // 动画进度只在 offset/graphicsLayer 的布局/绘制阶段 lambda 中读取，
    // 写入只触发重布局/重绘，不会导致整格 tile 的组合级重组。
    var travelSteps by remember { mutableFloatStateOf(0f) }

    // 帧率节流：画廊漂移是慢速循环动画，按「画廊」场景的帧率上限更新，
    // 跳过帧时 travelSteps 不写、该帧不重绘，降低静止/滚动时的 GPU 占用。
    val throttle = rememberSceneFrameThrottle(FrameRateScene.GALLERY)
    LaunchedEffect(throttle) {
        val startedAt = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameTime ->
                if (throttle.shouldUpdate(frameTime)) {
                    travelSteps = ((frameTime - startedAt) / 7_600_000_000.0).toFloat()
                }
            }
        }
    }

    val density = LocalDensity.current
    val stepPx = with(density) { step.toPx() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val marginCells = 3
        val columns = ceil(constraints.maxWidth / stepPx).toInt() + marginCells * 2 + 1
        // 用 gallery 实际高度而非整屏高度计算行数，避免渲染大量屏幕外的 tile（首帧卡顿主因）。
        val rows = ceil(constraints.maxHeight / stepPx).toInt() + marginCells * 2 + 1
        val minX = -marginCells * stepPx
        val minY = -marginCells * stepPx
        val spanX = columns * stepPx
        val spanY = rows * stepPx
        val fadeStart = constraints.maxHeight * .34f
        val fadeEnd = constraints.maxHeight * .92f
        val tileCenterOffset = with(density) { tileSize.toPx() / 2f }
        val topSafeHeightPx = with(density) { statusBarSafeHeight.toPx() }
        val topFadeLength = stepPx * .72f

        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = 8f
                    alpha = .78f
                }
        ) {
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    val baseX = (column - marginCells) * stepPx
                    val baseY = (row - marginCells) * stepPx
                    val songIndex = Math.floorMod(row * columns + column, songs.size)

                    key(row, column) {
                        ProfileGalleryTile(
                            song = songs[songIndex],
                            modifier = Modifier
                                .offset {
                                    val travelPx = (travelSteps * stepPx).toFloat()
                                    IntOffset(
                                        (minX + positiveModulo(baseX + travelPx - minX, spanX)).toInt(),
                                        (minY + positiveModulo(baseY + travelPx - minY, spanY)).toInt()
                                    )
                                }
                                .graphicsLayer {
                                    // 在绘制阶段读取动画进度：每帧只重绘，不重组。
                                    val travelPx = (travelSteps * stepPx).toFloat()
                                    val currentY = minY + positiveModulo(baseY + travelPx - minY, spanY)
                                    val bottomFade = ((fadeEnd - (currentY + tileCenterOffset)) / (fadeEnd - fadeStart))
                                        .coerceIn(0f, 1f)
                                    val topFade = ((currentY + tileCenterOffset - topSafeHeightPx) / topFadeLength)
                                        .coerceIn(0f, 1f)
                                    alpha = topFade * bottomFade
                                },
                            size = tileSize
                        )
                    }
                }
            }
        }
    }
}

private fun positiveModulo(value: Float, modulus: Float): Float {
    val remainder = value % modulus
    return if (remainder < 0f) remainder + modulus else remainder
}
@Composable
private fun ProfileGalleryTile(song: Song?, modifier: Modifier, size: Dp) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Color(0xFF202020)),
        contentAlignment = Alignment.Center
    ) {
        if (song != null) {
            AlbumArtwork(song, Modifier.matchParentSize(), song.title)
        } else {
            ProfileRecordPlaceholder(Modifier.matchParentSize(), Color(0xFF252525))
        }
    }
}
@Composable
private fun ProfileRecordPlaceholder(modifier: Modifier, color: Color) {
    Box(modifier.background(color), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize(.78f)
                .border(1.dp, Color.White.copy(alpha = .16f), CircleShape)
        )
        Box(
            Modifier
                .fillMaxSize(.58f)
                .border(1.dp, Color.White.copy(alpha = .16f), CircleShape)
        )
        Box(Modifier.size(55.dp).background(Color(0xFF1687F2), CircleShape))
        Box(Modifier.size(16.dp).background(Color(0xFF181818), CircleShape))
    }
}

@Composable
private fun ProfileIdentity(
    avatar: Uri?,
    name: String,
    pickAvatar: () -> Unit,
    onEditName: () -> Unit,
    foreground: Color,
    muted: Color,
    accent: Color
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF1C1C1C))
                .border(1.dp, Color.White.copy(alpha = .12f), CircleShape)
                .clickable(onClick = pickAvatar),
            contentAlignment = Alignment.Center
        ) {
            if (avatar != null) {
                AsyncImage(avatar, "头像", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(painterResource(R.drawable.ic_apple_user), "头像", tint = accent, modifier = Modifier.size(42.dp))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp)
                .clickable(onClick = onEditName)
        ) {
            Text(
                text = name,
                color = foreground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "本地音乐 · 你的聆听轨迹",
                color = muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 5.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileListeningCard(
    listeningTimeMs: Long,
    completedPlays: Int,
    repeatCount: Int,
    songCount: Int,
    accent: Color,
    foreground: Color,
    muted: Color
) {
    val totalMinutes = listeningTimeMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val listeningValue = when {
        hours > 0 -> "${hours} 小时 ${minutes} 分"
        minutes > 0 -> "${minutes} 分钟"
        else -> "刚刚开始"
    }
    val playArrowPainter = painterResource(R.drawable.ic_apple_play_arrow)
    val repeatPainter = painterResource(R.drawable.ic_apple_repeat)
    val libraryPainter = painterResource(R.drawable.ic_apple_library)
    val secondaryMetrics = listOf(
        ProfileMetricData(playArrowPainter, completedPlays.toString(), "完整播放"),
        ProfileMetricData(repeatPainter, repeatCount.toString(), "循环时刻"),
        ProfileMetricData(libraryPainter, songCount.toString(), "私人曲库")
    )

    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(
            Modifier.padding(horizontal = 24.dp),
            color = foreground.copy(alpha = .13f)
        )

        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "聆听速写",
                    color = foreground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "最近的音乐足迹",
                    color = muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = .14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_apple_headphones),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
            Text(
                text = "累计聆听",
                color = muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = listeningValue,
                color = foreground,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-.8).sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Box(
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth(.22f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(accent.copy(alpha = .45f), accent)))
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            secondaryMetrics.forEachIndexed { index, metric ->
                if (index > 0) {
                    VerticalDivider(
                        Modifier.height(38.dp),
                        color = foreground.copy(alpha = .12f)
                    )
                }
                ProfileMetric(
                    metric = metric,
                    modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                    accent = accent,
                    foreground = foreground,
                    muted = muted
                )
            }
        }
    }
}

private data class ProfileMetricData(
    val icon: Painter,
    val value: String,
    val label: String
)

@Composable
private fun ProfileMetric(
    metric: ProfileMetricData,
    modifier: Modifier,
    accent: Color,
    foreground: Color,
    muted: Color
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = metric.icon,
            contentDescription = null,
            tint = accent.copy(alpha = .88f),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = metric.value,
            color = foreground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = metric.label,
            color = muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
