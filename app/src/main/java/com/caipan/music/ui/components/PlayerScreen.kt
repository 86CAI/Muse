/*
 * Muse 全屏播放器。
 *
 * 控件层级参考 zyrouge/Symphony (AGPL-3.0-only) 的
 * `ui/view/nowPlaying/BodyContent.kt` 与 `ui/view/nowPlaying/BottomBar.kt`：
 * 封面 / 元信息 / 进度 / 主控件 / 次级动作的纵向层级顺序。
 * 具体实现、玻璃材质、歌词、雨滴与背景系统均为 Muse 自有。
 *
 * Upstream: https://github.com/zyrouge/symphony @ dd04b872b8b4e6dd56172c053a5776c4d56ad080
 * License: GNU Affero General Public License v3.0 only —— 见 licenses/AGPL-3.0.txt
 */
package com.caipan.music.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.caipan.music.R
import com.caipan.music.model.Song
import com.caipan.music.player.LyricLine
import com.caipan.music.player.LyricsManager
import com.caipan.music.player.PlayerBgMode
import com.caipan.music.player.RepeatMode
import com.caipan.music.ui.theme.MuseDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.caipan.music.plugin.BlurLocation
import com.kyant.backdrop.Backdrop

@Composable
fun rememberAlbumColors(albumArtUri: Any?, context: android.content.Context): List<Color> {
    var colors by remember(albumArtUri) { mutableStateOf(listOf(MuseDesign.Red, Color(0xFF312B38))) }
    LaunchedEffect(albumArtUri) {
        if (albumArtUri == null) return@LaunchedEffect
        runCatching {
            val drawable = withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context).data(albumArtUri).size(180).allowHardware(false).build()
                (context.imageLoader.execute(request) as? SuccessResult)?.drawable
            } ?: return@runCatching
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, bitmap.width, bitmap.height); drawable.draw(Canvas(bitmap))
            val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).maximumColorCount(10).generate() }
            colors = listOf(Color(palette.vibrantSwatch?.rgb ?: MuseDesign.Red.value.toInt()), Color(palette.darkMutedSwatch?.rgb ?: 0xFF312B38.toInt()))
        }
    }
    return colors
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    song: Song, isPlaying: Boolean, progressMs: Long, durationMs: Long,
    repeatMode: RepeatMode, isShuffled: Boolean,
    onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit,
    onSeek: (Long) -> Unit, onRepeatToggle: () -> Unit, onShuffleToggle: () -> Unit,
    onTransferUp: () -> Unit = {},
    onDismiss: () -> Unit, onLandscapeToggle: () -> Unit = {}, customBgColor: Color? = null, wallpaperUri: Uri? = null,
    isLightTheme: Boolean = false, bgMode: PlayerBgMode = PlayerBgMode.ALBUM_EXTEND,
    lyricsLoader: (suspend (Song) -> List<LyricLine>)? = null, backdrop: Backdrop? = null, modifier: Modifier = Modifier,
    externalArtUri: Uri? = null,
    onOpenMineradioLyrics: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    onOpenPlaybackSettings: () -> Unit = {},
    isChinese: Boolean = true,
    onCycleQuality: () -> Unit = {},
    quality: String? = null,
    showPrevNext: Boolean = false,
    skin: com.caipan.music.skin.MuseSkin? = null
) {
    val context = LocalContext.current
    val playerView = LocalView.current
    // ── 皮肤参数 ──
    val skinLayout = skin?.layout
    val artShape = skinLayout?.albumArtShape ?: com.caipan.music.skin.AlbumArtShape.CIRCLE
    val progressStyle = skinLayout?.progressStyle ?: com.caipan.music.skin.ProgressStyle.THIN
    val showTimeLabels = skinLayout?.showTimeLabels ?: true
    val wallpaperDim = skin?.wallpaper?.dim ?: 0f
    val fontScale = skin?.font?.scale ?: 1f
    val showPrevNextEffective = showPrevNext || skinLayout?.showPrevNext == true
    // 横屏播放器沉浸式：隐藏状态栏，下滑可临时唤出
    val isLandscapeConfig = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    DisposableEffect(isLandscapeConfig) {
        val window = (playerView.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowInsetsControllerCompat(it, playerView) }
        if (controller != null && isLandscapeConfig) {
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            if (isLandscapeConfig) controller?.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
    }
    val artUri = externalArtUri ?: song.albumArtUri
    val albumColors = rememberAlbumColors(artUri, context)
    val accent = customBgColor ?: albumColors.first()
    val primary = if (isLightTheme) Color(0xFF18181B) else Color.White
    val secondary = primary.copy(alpha = .58f)
    val playbackSettings by (context.applicationContext as? com.caipan.music.MuseApplication)
        ?.playbackSettingsStore?.state?.collectAsState() ?: remember { mutableStateOf(com.caipan.music.player.PlaybackSettings()) }
    val lyricsFontSize = playbackSettings.lyricsFontSize
    val glassConfig by (context.applicationContext as? com.caipan.music.MuseApplication)
        ?.glassConfigStore?.state?.collectAsState() ?: remember { mutableStateOf(MuseGlassConfig()) }
    var lyrics by remember(song.id) { mutableStateOf<List<LyricLine>>(emptyList()) }
    // 不随 song 重置：歌词模式下切歌保持歌词模式
    var showLyrics by remember { mutableStateOf(false) }
    // 持久封面：封面与歌词切换时封面不销毁，而是从居中大图移动到顶部小图（复刻 MeloX 的
    // SharedArtworkDestination，只是把共享元素简化为同一 composition 内的尺寸/位置 lerp）。
    val artworkPageProgress by animateFloatAsState(
        targetValue = if (showLyrics && lyrics.isNotEmpty()) 1f else 0f,
        animationSpec = tween(480, easing = FastOutSlowInEasing),
        label = "artworkPageProgress"
    )
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }
    var seekingTo by remember(song.id) { mutableStateOf<Long?>(null) }
    val currentLine = remember(lyrics, progressMs) { LyricsManager.currentLineIndex(lyrics, progressMs) }
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else .92f,
        animationSpec = tween(MuseDesign.DurationSlow, easing = FastOutSlowInEasing),
        label = "artScale"
    )
    // 呼吸动画
    val infiniteTransition = rememberInfiniteTransition()
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "breathing"
    )
    val finalArtScale = artScale * if (isPlaying) breathingScale else 1f
    LaunchedEffect(song.id) { lyrics = lyricsLoader?.invoke(song).orEmpty() }

    val inputBlocker = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier.fillMaxSize().clickable(
            interactionSource = inputBlocker,
            indication = null
        ) {}
    ) {
        Box(Modifier.matchParentSize()) {
            PlayerBackdrop(bgMode, artUri, wallpaperUri, albumColors, isLightTheme)
            val dimBoost = 1f + wallpaperDim * 2.2f
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                if (isLightTheme) Color.White.copy(.30f / dimBoost) else Color.Black.copy(.18f * dimBoost),
                if (isLightTheme) Color.White.copy(.55f / dimBoost) else Color.Black.copy(.62f * dimBoost)
            ))))
        }

        CompositionLocalProvider(LocalMuseBackdrop provides backdrop) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight
            if (isLandscape) {
                LandscapePlayerContent(song, isPlaying, progressMs, durationMs, repeatMode, isShuffled,
                    onPlayPause, onNext, onPrevious, onSeek, onRepeatToggle, onShuffleToggle,
                    onDismiss, accent, primary, secondary, artScale, lyrics, currentLine,
                    artUri = artUri,
                    onOpenMineradioLyrics = onOpenMineradioLyrics,
                    onFavoriteToggle = onFavoriteToggle,
                    fontSize = (lyricsFontSize * fontScale).toInt())
            } else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().height(60.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MuseIconButton(onClick = onDismiss, modifier = Modifier.size(MuseDesign.MinTouch)) {
                    Icon(painterResource(R.drawable.ic_apple_chevron_down), "收起播放器", tint = primary, modifier = Modifier.size(30.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (lyrics.isNotEmpty()) {
                        TextButton(
                            onClick = { showLyrics = !showLyrics },
                            modifier = Modifier.height(MuseDesign.MinTouch)
                        ) {
                            Text(if (showLyrics) "封面" else "歌词", color = if (showLyrics) accent else primary, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text("正在播放", color = secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    }
                }
                IconButton(onClick = onLandscapeToggle, modifier = Modifier.size(MuseDesign.MinTouch)) {
                    Icon(painterResource(R.drawable.ic_apple_rotate_cw), "横屏大播放器", tint = secondary, modifier = Modifier.size(22.dp))
                }
            }

            // The player menu owns the whole stage above the progress bar.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .playerUpwardGesture(
                        gestureKey = song.id,
                        enabled = artworkPageProgress < 0.5f,
                        onSwipeUp = onOpenPlaybackSettings
                    )
            ) {
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                val smallArtSize = 52.dp
                val fullArtSize = maxWidth
                val artSize = lerpDp(fullArtSize, smallArtSize, artworkPageProgress)
                // 封面略微下移（原 0.5f 居中），让底部那行歌词更贴近封面
                val artTopFull = ((maxHeight - fullArtSize) * 0.58f).coerceAtLeast(0.dp)
                val artTop = lerpDp(artTopFull, 0.dp, artworkPageProgress)

                // 歌词驻留层（底层，淡入；切回封面时淡出但不销毁）
                if (lyrics.isNotEmpty()) {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer { alpha = artworkPageProgress }
                    ) {
                        LyricsDetailsPager(
                            song, lyrics, currentLine, accent, primary, secondary,
                            onTapLine = onSeek, onCloseLyrics = { showLyrics = false },
                            fontSize = (lyricsFontSize * fontScale).toInt(),
                            progressMs = progressMs
                        )
                    }
                }

                // 歌曲信息行（歌词模式淡入，封面右侧显示标题+歌手；左右滑动切歌）
                if (lyrics.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(smallArtSize)
                            .align(Alignment.TopStart)
                            .padding(start = smallArtSize + 16.dp, end = 8.dp)
                            .graphicsLayer { alpha = artworkPageProgress }
                            .pointerInput(song.id) {
                                var accumulatedX = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { accumulatedX = 0f },
                                    onDragEnd = {
                                        val threshold = 48.dp.toPx()
                                        if (accumulatedX <= -threshold) onNext()
                                        else if (accumulatedX >= threshold) onPrevious()
                                        accumulatedX = 0f
                                    },
                                    onDragCancel = { accumulatedX = 0f }
                                ) { change, dragAmount ->
                                    change.consume()
                                    accumulatedX += dragAmount
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            AnimatedContent(
                                targetState = song,
                                transitionSpec = { fadeIn(tween(MuseDesign.DurationFast)) togetherWith fadeOut(tween(MuseDesign.DurationFast)) },
                                label = "songHeaderContent"
                            ) { s ->
                                Column {
                                    Text(s.title, color = primary, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(s.artist, color = secondary, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                // 持久封面层：同一实例在「居中大图」与「左上角小图」之间移动；
                // 歌词模式下点击小封面 = 暂停/播放
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = artTop)
                        .size(artSize)
                        .clickable(
                            enabled = artworkPageProgress >= 0.5f,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { onPlayPause() }
                ) {
                    SwipeableAlbumArt(
                        song, finalArtScale, primary, secondary, accent,
                        onPlayPause, onNext, onPrevious,
                        artUri, onOpenMineradioLyrics, onDoubleTap = onFavoriteToggle,
                        shape = artShape,
                        compactFraction = artworkPageProgress,
                        gesturesEnabled = artworkPageProgress < 0.5f
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
            // 标题+歌手：封面模式显示；歌词模式收缩隐藏，让歌词区域向下延伸
            AnimatedVisibility(
                visible = artworkPageProgress < 0.5f,
                enter = fadeIn(tween(MuseDesign.DurationFast)) + expandVertically(tween(MuseDesign.DurationFast)),
                exit = fadeOut(tween(MuseDesign.DurationFast)) + shrinkVertically(tween(MuseDesign.DurationFast)),
                label = "songMeta"
            ) {
                Column(
                    Modifier.fillMaxWidth()
                ) {
                    // 封面模式：当前歌词放在封面和标题之间，点击打开歌词
                    if (lyrics.isNotEmpty() && currentLine in lyrics.indices) {
                        Text(
                            text = lyrics[currentLine].text,
                            color = secondary,
                            fontSize = 20.sp,
                            lineHeight = 24.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { showLyrics = true }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    AnimatedContent(
                        targetState = song,
                        transitionSpec = { fadeIn(tween(MuseDesign.DurationFast)) togetherWith fadeOut(tween(MuseDesign.DurationFast)) },
                        label = "songMetaContent"
                    ) { s ->
                        Column(Modifier.fillMaxWidth().padding(end = 8.dp)) {
                            Text(s.title, color = primary, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(5.dp))
                            Text(s.artist, color = secondary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            }

            Spacer(Modifier.height(18.dp))
            val progress = if (durationMs > 0) (progressMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            LaunchedEffect(progress, isSeeking) {
                if (!isSeeking) {
                    val target = seekingTo
                    if (target == null) {
                        seekProgress = progress
                    } else if (kotlin.math.abs(progressMs - target) <= 300L) {
                        seekingTo = null
                        seekProgress = progress
                    }
                }
            }
            if (progressStyle != com.caipan.music.skin.ProgressStyle.NONE) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.weight(1f).height(if (progressStyle == com.caipan.music.skin.ProgressStyle.THICK) 64.dp else 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // MeloX 细条进度：无 thumb，拖动时 track 变粗
                    val baseTrackHeight = if (progressStyle == com.caipan.music.skin.ProgressStyle.THICK) 8.dp else 4.dp
                    val progressTrackHeight by animateDpAsState(
                        targetValue = if (isSeeking) baseTrackHeight + 2.dp else baseTrackHeight,
                        animationSpec = tween(120),
                        label = "progressTrackHeight"
                    )
                    Slider(
                        value = if (isSeeking) seekProgress else progress,
                        onValueChange = { isSeeking = true; seekProgress = it },
                        onValueChangeFinished = {
                            seekingTo = (seekProgress * durationMs).toLong()
                            onSeek(seekingTo!!)
                            isSeeking = false
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(20.dp),
                        thumb = { Spacer(Modifier.size(0.dp)) },
                        track = {
                            Box(
                                Modifier.fillMaxWidth().height(progressTrackHeight).clip(CircleShape)
                                    .background(secondary.copy(alpha = 0.20f))
                            ) {
                                Box(
                                    Modifier.fillMaxWidth(if (isSeeking) seekProgress else progress)
                                        .fillMaxHeight()
                                        .background(primary)
                                )
                            }
                        }
                    )
                }
                // 顺序播放控制器（循环/随机/单曲循环），右移
                IconButton(
                    onClick = {
                        when {
                            isShuffled -> onShuffleToggle()
                            repeatMode == RepeatMode.ONE -> {
                                onRepeatToggle()
                                onShuffleToggle()
                            }
                            else -> onRepeatToggle()
                        }
                    },
                    modifier = Modifier.size(MuseDesign.MinTouch).semantics {
                        contentDescription = when {
                            isShuffled -> "随机播放"
                            repeatMode == RepeatMode.ONE -> "单曲循环"
                            else -> "循环播放"
                        }
                    }
                ) {
                    when {
                        isShuffled -> MusicShuffleIcon(Modifier.size(21.dp), accent)
                        repeatMode == RepeatMode.NONE -> MusicRepeatIcon(Modifier.size(21.dp), secondary)
                        else -> Box(contentAlignment = Alignment.Center) {
                            MusicRepeatIcon(Modifier.size(21.dp), accent)
                            if (repeatMode == RepeatMode.ONE) {
                                Text("1", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            }
            // Comments own the dedicated touch region below the progress controls.
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .playerUpwardGesture(
                        gestureKey = song.id,
                        enabled = true,
                        onSwipeUp = onTransferUp
                    )
            ) {
            if (progressStyle != com.caipan.music.skin.ProgressStyle.NONE) {
                if (showTimeLabels) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatPlayerTime(progressMs), color = secondary, fontSize = 12.sp)
                        Text("-${formatPlayerTime((durationMs - progressMs).coerceAtLeast(0))}", color = secondary, fontSize = 12.sp)
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                }
            }
            // 音质显示：Waveform 图标 + 码率/格式（放在进度条下方）
            // 在线音乐显示实际可用音质（可点击循环切换）；本地音乐显示格式·码率
            val qualityLabel = if (song.isOnline) {
                qualityText(quality)
            } else {
                buildString {
                    val fmt = song.formatLabel
                    if (fmt.isNotBlank() && fmt != "未知" && fmt != "在线") append(fmt)
                    if (song.bitrate > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${song.bitrate / 1000} kbps")
                    }
                }.ifBlank { "标准" }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        enabled = song.isOnline
                    ) { onCycleQuality() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.size(12.dp)) {
                    val xs = listOf(0.16f, 0.38f, 0.60f, 0.82f)
                    val heights = listOf(0.40f, 0.78f, 0.58f, 0.32f)
                    val stroke = 1.4f * density
                    xs.zip(heights).forEach { (x, f) ->
                        val half = size.height * f * 0.5f
                        drawLine(
                            color = secondary,
                            start = Offset(size.width * x, size.height * 0.5f - half),
                            end = Offset(size.width * x, size.height * 0.5f + half),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(qualityLabel, color = secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(18.dp))
            // 皮肤布局开关：显示上一首/下一首按钮（默认手势控制，皮肤可开启）
            if (showPrevNextEffective) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                        MusicSkipPreviousIcon(Modifier.size(30.dp), primary)
                    }
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).clickable(onClick = onPlayPause),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlaying) MusicPauseIcon(Modifier.size(30.dp), primary)
                        else MusicPlayIcon(Modifier.size(30.dp), primary)
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                        MusicSkipNextIcon(Modifier.size(30.dp), primary)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            }
        }
        }
        }
    }
}

@Composable
private fun LandscapePlayerContent(
    song: Song, isPlaying: Boolean, progressMs: Long, durationMs: Long, repeatMode: RepeatMode,
    isShuffled: Boolean, onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit,
    onSeek: (Long) -> Unit, onRepeatToggle: () -> Unit, onShuffleToggle: () -> Unit,
    onDismiss: () -> Unit, accent: Color, primary: Color,
    secondary: Color, artScale: Float, lyrics: List<LyricLine>, currentLine: Int, artUri: Uri?,
    onOpenMineradioLyrics: () -> Unit,
    onFavoriteToggle: () -> Unit,
    fontSize: Int = 24
) {
    Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        .padding(horizontal = 28.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.fillMaxHeight().weight(.9f), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxHeight(.68f).aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
            SwipeableAlbumArt(song, artScale, primary, secondary, accent, onPlayPause, onNext, onPrevious, artUri, onOpenMineradioLyrics, onDoubleTap = onFavoriteToggle)
            }
            Spacer(Modifier.height(12.dp))
            Text(song.title, color = primary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "未知艺人" },
                color = secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(36.dp))
        Column(Modifier.fillMaxHeight().weight(1.1f)) {
            Box(Modifier.fillMaxSize()) {
                if (lyrics.isNotEmpty()) {
                    LyricsDetailsPager(
                        song, lyrics, currentLine, accent, primary, secondary,
                        onTapLine = onSeek, onCloseLyrics = {}, showCloseButton = false,
                        fontSize = fontSize,
                        progressMs = progressMs
                    )
                } else {
                    LandscapeSongDetails(song, primary, secondary)
                }
            }
        }
    }
}

@Composable
private fun LyricsDetailsPager(
    song: Song, lyrics: List<LyricLine>, currentLine: Int, accent: Color, primary: Color, secondary: Color,
    onTapLine: (Long) -> Unit, onCloseLyrics: () -> Unit, showCloseButton: Boolean = true,
    fontSize: Int = 24,
    progressMs: Long = 0L
) {
    var showDetails by remember(song.id) { mutableStateOf(false) }
    var horizontalDrag by remember { mutableFloatStateOf(0f) }

    AnimatedContent(
        targetState = showDetails,
        modifier = Modifier.fillMaxSize().pointerInput(song.id, showDetails) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    horizontalDrag += amount
                },
                onDragEnd = {
                    if (kotlin.math.abs(horizontalDrag) >= 72f) showDetails = !showDetails
                    horizontalDrag = 0f
                },
                onDragCancel = { horizontalDrag = 0f }
            )
        },
        transitionSpec = { fadeIn(tween(MuseDesign.DurationFast)) togetherWith fadeOut(tween(MuseDesign.DurationFast)) },
        label = "lyricsDetails"
    ) { detailsVisible ->
        if (detailsVisible) LandscapeSongDetails(song, primary, secondary)
        else LyricsView(lyrics, currentLine, accent, primary, secondary, onTapLine, onCloseLyrics, showCloseButton, fontSize, progressMs)
    }
}

@Composable
private fun LandscapeSongDetails(song: Song, primary: Color, secondary: Color) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp).verticalScroll(rememberScrollState())) {
        Text("歌曲详细信息", color = primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        LandscapeDetailLine("文件", song.fileName.ifBlank { "未知" }, primary, secondary)
        LandscapeDetailLine("编码", song.mimeType.ifBlank { song.formatLabel }, primary, secondary)
        LandscapeDetailLine("时长", song.formattedDuration, primary, secondary)
        LandscapeDetailLine("采样率", if (song.sampleRate > 0) "${song.sampleRate / 1000f} kHz" else "未知", primary, secondary)
        LandscapeDetailLine("码率", if (song.bitrate > 0) "${song.bitrate / 1000} kbps" else "未知", primary, secondary)
        LandscapeDetailLine("专辑", song.album.ifBlank { "未知专辑" }, primary, secondary)
        LandscapeDetailLine("艺人", song.artist.ifBlank { "未知艺人" }, primary, secondary)
        LandscapeDetailLine("路径", song.filePath.ifBlank { song.folderPath }, primary, secondary)
    }
}

@Composable
private fun LandscapeDetailLine(label: String, value: String, primary: Color, secondary: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, color = secondary, fontSize = 11.sp)
        Text(value, color = primary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun lerpDp(start: Dp, end: Dp, fraction: Float): Dp =
    start + (end - start) * fraction.coerceIn(0f, 1f)

/** 把解析返回的实际音质字符串映射成中文标签。 */
private fun qualityText(q: String?): String = when (q?.lowercase()?.trim()) {
    "flac24bit", "hires", "hi-res", "hi_res" -> "Hi-Res"
    "flac", "lossless", "ape", "wav" -> "无损"
    "320k", "320", "high", "exhigh" -> "高品"
    "128k", "128", "standard" -> "标准"
    null, "" -> "标准"
    else -> q
}

/**
 * Observes a dedicated player region for an upward gesture.
 *
 * This is intentionally an observer: it listens in [PointerEventPass.Initial] and never consumes
 * the pointer stream. The cover can therefore keep its own horizontal previous/next, double-tap,
 * and long-press behavior. Direction is locked after touch slop, so a horizontal cover swipe can
 * never become an upward action later in the same drag.
 */
@Composable
private fun Modifier.playerUpwardGesture(
    gestureKey: Any,
    enabled: Boolean,
    onSwipeUp: () -> Unit
): Modifier {
    val latestOnSwipeUp by rememberUpdatedState(onSwipeUp)
    if (!enabled) return this

    return pointerInput(gestureKey) {
        val upwardThreshold = 48.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            var totalX = 0f
            var totalY = 0f
            var hasMoved = false
            var wasLongPress = false
            var directionLocked = false
            var isUpwardGesture = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                totalX += change.position.x - change.previousPosition.x
                totalY += change.position.y - change.previousPosition.y

                // A press held before it starts moving belongs to a long-press action and must
                // not later become an upward action.
                if (!hasMoved && change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) {
                    wasLongPress = true
                }
                if (!hasMoved && kotlin.math.hypot(totalX.toDouble(), totalY.toDouble()) > viewConfiguration.touchSlop) {
                    hasMoved = true
                }
                if (!directionLocked && hasMoved) {
                    directionLocked = true
                    isUpwardGesture = totalY < 0f && kotlin.math.abs(totalY) > kotlin.math.abs(totalX)
                }

                if (!change.pressed) {
                    if (
                        !wasLongPress &&
                        isUpwardGesture &&
                        totalY <= -upwardThreshold
                    ) {
                        latestOnSwipeUp()
                    }
                    break
                }
            }
        }
    }
}

@Composable
private fun SwipeableAlbumArt(
    song: Song, artScale: Float, primary: Color, secondary: Color, accent: Color,
    onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit,
    artUri: Uri?, onLongPress: () -> Unit = {}, onDoubleTap: () -> Unit = {},
    shape: com.caipan.music.skin.AlbumArtShape = com.caipan.music.skin.AlbumArtShape.CIRCLE,
    compactFraction: Float = 0f,
    gesturesEnabled: Boolean = true
) {
    // 封面形状：circle=黑胶圆角 | rounded=圆角方形 | square=纯方形（安卓原生感）
    val cornerDp = when (shape) {
        com.caipan.music.skin.AlbumArtShape.CIRCLE -> 26.dp
        com.caipan.music.skin.AlbumArtShape.ROUNDED -> 18.dp
        com.caipan.music.skin.AlbumArtShape.SQUARE -> 0.dp
    }
    // 持久封面缩小后内边距与阴影同步收窄，避免 44dp 小图上残留 18dp 大间距
    val artPad = lerpDp(18.dp, 3.dp, compactFraction)
    val dragX = remember(song.id) { Animatable(0f) }
    // 滑动范围限制 + 羽化：封面最多拖出封面宽度的 55%，越接近边界越透明
    var artWidthPx by remember(song.id) { mutableFloatStateOf(0f) }
    val maxDragPx = if (artWidthPx > 0f) artWidthPx * 0.55f else with(LocalDensity.current) { 160.dp.toPx() }
    val scope = rememberCoroutineScope()
    val latestNext by rememberUpdatedState(onNext)
    val latestPrevious by rememberUpdatedState(onPrevious)
    val latestPlayPause by rememberUpdatedState(onPlayPause)
    val latestLongPress by rememberUpdatedState(onLongPress)
    val latestDoubleTap by rememberUpdatedState(onDoubleTap)
    var lastTapMs by remember(song.id) { mutableLongStateOf(0L) }
    var pendingTapJob by remember(song.id) { mutableStateOf<Job?>(null) }
    val artworkModifier = Modifier.fillMaxSize().padding(artPad).scale(artScale)
        .graphicsLayer {
            translationX = dragX.value
            val fade = 1f - (kotlin.math.abs(dragX.value) / maxDragPx.coerceAtLeast(1f)).coerceIn(0f, 1f) * 0.5f
            alpha = fade
        }
        .background(primary.copy(.10f), RoundedCornerShape(cornerDp))
        .pointerInput(song.id, gesturesEnabled) {
            if (!gesturesEnabled) return@pointerInput
            val swipeThresholdX = 48.dp.toPx()
            awaitEachGesture {
                awaitFirstDown()
                pendingTapJob?.cancel()
                var totalX = 0f
                var totalY = 0f
                var moved = false
                var longPressed = false
                val timeout = viewConfiguration.longPressTimeoutMillis
                val completed = withTimeoutOrNull(timeout.toLong()) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (!change.pressed) return@withTimeoutOrNull true
                        totalX += change.position.x - change.previousPosition.x
                        totalY += change.position.y - change.previousPosition.y
                        if (kotlin.math.hypot(totalX.toDouble(), totalY.toDouble()) > viewConfiguration.touchSlop) {
                            moved = true
                            return@withTimeoutOrNull false
                        }
                    }
                }
                if (completed == null && !moved) {
                    longPressed = true
                    latestLongPress()
                }
                if (completed != true) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (!change.pressed) break
                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y
                        totalX += dx
                        totalY += dy
                        if (kotlin.math.abs(totalX) > viewConfiguration.touchSlop || kotlin.math.abs(totalY) > viewConfiguration.touchSlop) moved = true
                        if (moved && !longPressed) {
                            change.consume()
                            scope.launch { dragX.snapTo(totalX.coerceIn(-maxDragPx, maxDragPx)) }
                        }
                    }
                }
                if (!longPressed && !moved) {
                    val now = System.currentTimeMillis()
                    if (lastTapMs > 0L && now - lastTapMs <= DOUBLE_TAP_TIMEOUT_MS) {
                        lastTapMs = 0L
                        pendingTapJob?.cancel()
                        latestDoubleTap()
                    } else {
                        lastTapMs = now
                        pendingTapJob?.cancel()
                        pendingTapJob = scope.launch {
                            delay(DOUBLE_TAP_TIMEOUT_MS)
                            latestPlayPause()
                        }
                    }
                }
                else if (!longPressed && kotlin.math.abs(totalX) >= swipeThresholdX && kotlin.math.abs(totalX) > kotlin.math.abs(totalY)) {
                    if (totalX > 0) latestPrevious() else latestNext()
                }
                scope.launch {
                    dragX.animateTo(0f, tween(MuseDesign.DurationFast))
                }
            }
        }
    Box(Modifier.fillMaxWidth().aspectRatio(1f).onSizeChanged { artWidthPx = it.width.toFloat() }, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().padding(artPad).scale(artScale)
                .graphicsLayer {
                    translationX = dragX.value
                    val fade = 1f - (kotlin.math.abs(dragX.value) / maxDragPx.coerceAtLeast(1f)).coerceIn(0f, 1f) * 0.5f
                    alpha = fade
                }
        ) {
            val corner = cornerDp.toPx()
            val inset = 3.dp.toPx()
            val lift = 7.dp.toPx()
            drawRoundRect(
                color = Color.Black.copy(alpha = .10f),
                topLeft = Offset(-inset, 1.dp.toPx()),
                size = Size(size.width + inset * 2, size.height - 1.dp.toPx()),
                cornerRadius = CornerRadius(corner + inset)
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = .16f),
                topLeft = Offset(2.dp.toPx(), lift),
                size = Size(size.width - 4.dp.toPx(), size.height - lift),
                cornerRadius = CornerRadius(corner)
            )
        }
        Box(artworkModifier, contentAlignment = Alignment.Center) {
            AlbumArtwork(
                song,
                Modifier.fillMaxSize().clip(RoundedCornerShape(cornerDp)),
                "${song.title}专辑封面",
                artUriOverride = artUri
            )
        }
    }
}

@Composable
private fun PlayerBackdrop(mode: PlayerBgMode, album: Uri?, wallpaper: Uri?, colors: List<Color>, light: Boolean) {
    // 纯莫奈：背景封面不做高斯模糊，保持 Material You 的干净取色
    val blurEnabled = !LocalMuseMonet.current && LocalMuseBlurPolicy.current.enabledAt(BlurLocation.PLAYER)
    Box(Modifier.fillMaxSize()) {
        when (mode) {
            PlayerBgMode.CUSTOM -> {
                // 渐变垫底：无封面、封面加载失败、无壁纸时都保持不透明
                DefaultBlurredFallback(colors)
                if (wallpaper != null || album != null) AsyncImage(
                    wallpaper ?: album, null,
                    Modifier.matchParentSize().scale(1.15f).then(if (blurEnabled) Modifier.blur(30.dp) else Modifier),
                    contentScale = ContentScale.Crop
                )
            }
            PlayerBgMode.ALBUM_EXTEND -> {
                DefaultBlurredFallback(colors)
                if (album != null) AsyncImage(
                    album, null,
                    Modifier.matchParentSize().scale(1.25f).then(if (blurEnabled) Modifier.blur(55.dp) else Modifier),
                    contentScale = ContentScale.Crop
                )
            }
            PlayerBgMode.DYNAMIC_COLOR -> Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(colors[0], colors.getOrElse(1) { colors[0] }))))
        }
        Box(Modifier.matchParentSize().background(if (light) Color.White.copy(.22f) else Color.Black.copy(.26f)))
    }
}

/** 无封面/无壁纸时的默认背景：柔和渐变 + 光斑，避免大播放器背景透明。 */
@Composable
private fun DefaultBlurredFallback(colors: List<Color>) {
    val c0 = colors.firstOrNull() ?: MuseDesign.Red
    val c1 = colors.getOrElse(1) { c0 }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c0, c1))))
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(c0.copy(alpha = 0.30f), Color.Transparent),
                center = Offset(0.72f, 0.28f),
                radius = 1400f
            )
        )
    )
}

@Composable
internal fun AnimatedGlowHalos(albumColors: List<Color>, isPlaying: Boolean) {
    // Compatibility layer for the legacy AppleMusicScreen; intentionally static for performance.
    val alpha = if (isPlaying) .18f else .10f
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(albumColors.first().copy(alpha), Color.Transparent))))
}

private const val DOUBLE_TAP_TIMEOUT_MS = 300L

private fun formatPlayerTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
