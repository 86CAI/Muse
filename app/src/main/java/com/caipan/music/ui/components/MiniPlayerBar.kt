// Direct structural port of Symphony NowPlayingBottomBar.kt (AGPL-3.0-only), adapted to Muse callbacks.
// Source: https://github.com/zyrouge/symphony @ dd04b872b8b4e6dd56172c053a5776c4d56ad080
// License text: licenses/AGPL-3.0.txt - full attribution list: THIRD_PARTY_NOTICES.md
package com.caipan.music.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import android.net.Uri
import coil.compose.AsyncImage
import com.caipan.music.R
import com.caipan.music.model.Song
import com.caipan.music.plugin.BlurLocation
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun MiniPlayerBar(
    song: Song, isPlaying: Boolean, progressMs: Long, durationMs: Long,
    onPlayPause: () -> Unit, onTap: () -> Unit, onSwipeUp: () -> Unit,
    backdrop: Backdrop? = null, modifier: Modifier = Modifier,
    externalArtUri: Uri? = null,
    onArtworkBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    miniPlayerStyle: com.caipan.music.skin.MiniPlayerStyle = com.caipan.music.skin.MiniPlayerStyle.RECORD,
    showMiniArtwork: Boolean = true,
    onNext: () -> Unit = {}
) {
    if (miniPlayerStyle == com.caipan.music.skin.MiniPlayerStyle.CLASSIC) {
        ClassicMiniPlayer(
            song, isPlaying, progressMs, durationMs, onPlayPause, onTap, onNext,
            backdrop, modifier, externalArtUri, showMiniArtwork
        )
    } else {
        FloatingRecordPlayer(
            song, isPlaying, progressMs, durationMs, onPlayPause, onTap, onSwipeUp,
            backdrop, MaterialTheme.colorScheme.primary, modifier, externalArtUri, onArtworkBoundsChanged
        )
    }
}

/**
 * 传统底部迷你播放器（安卓原生风）：长条卡片 = 封面 + 歌名/歌手 + 播放/下一首 + 进度细线。
 * 皮肤 miniPlayerStyle = "classic" 时启用，替代悬浮黑胶。
 */
@Composable
private fun ClassicMiniPlayer(
    song: Song, isPlaying: Boolean, progressMs: Long, durationMs: Long,
    onPlayPause: () -> Unit, onTap: () -> Unit, onNext: () -> Unit,
    backdrop: Backdrop? = null, modifier: Modifier = Modifier,
    externalArtUri: Uri? = null, showMiniArtwork: Boolean = true
) {
    val progress = if (durationMs > 0) (progressMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // modifier 来自调用方（matchParentSize + 动画 alpha），只能作为全屏容器，
    // 内容必须固定在底部自适应高度，否则会被撑满整个屏幕。
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
            .clickable(onClick = onTap)
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showMiniArtwork) {
                val art = externalArtUri ?: song.albumArtUri
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))) {
                    if (art != null) {
                        AsyncImage(model = art, contentDescription = song.title,
                            modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_apple_music), null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MuseIconButton(onClick = onPlayPause) {
                val pausePainter = painterResource(R.drawable.ic_apple_pause_simple)
                val playPainter = painterResource(R.drawable.ic_apple_play_arrow)
                Icon(if (isPlaying) pausePainter else playPainter,
                    if (isPlaying) "暂停" else "播放", tint = MaterialTheme.colorScheme.primary)
            }
            MuseIconButton(onClick = onNext) {
                MusicSkipNextIcon(Modifier.size(22.dp), MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(6.dp))
        // 进度细线
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val fillColor = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 4.dp)) {
            val stroke = 3.dp.toPx()
            drawRoundRect(
                color = trackColor,
                cornerRadius = CornerRadius(stroke / 2)
            )
            drawRoundRect(
                color = fillColor,
                size = Size(size.width * progress, size.height),
                cornerRadius = CornerRadius(stroke / 2)
            )
        }
    }
    } // Box(modifier) 容器
}

@Composable
fun FloatingRecordPlayer(
    song: Song,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onTap: () -> Unit,
    onSwipeUp: () -> Unit,
    backdrop: Backdrop?,
    accentColor: Color,
    modifier: Modifier = Modifier,
    externalArtUri: Uri? = null,
    onArtworkBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit = {}
) {
    val density = LocalDensity.current
    val liquidGlass = LocalMuseLiquidGlass.current
    val monet = LocalMuseMonet.current
    val blurEnabled = LocalMuseBlurPolicy.current.enabledAt(BlurLocation.MINI_PLAYER)
    val discSize = 96.dp
    val haloSize = 108.dp
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // 播放按钮缩放动画
    val playButtonScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "playButtonScale"
    )
    // 进度弧平滑动画，避免 progressMs 跳变导致视觉突兀
    val progressFraction by animateFloatAsState(
        targetValue = if (durationMs > 0) (progressMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "progressArc"
    )
    LaunchedEffect(isPlaying) {
        while (isPlaying && isActive) {
            rotation.animateTo(rotation.value + 360f, tween(9000, easing = LinearEasing))
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxX = with(density) { (maxWidth - haloSize).toPx().coerceAtLeast(0f) }
        val maxY = with(density) { (maxHeight - haloSize).toPx().coerceAtLeast(0f) }
        var position by remember { mutableStateOf(Offset(Float.NaN, Float.NaN)) }
        var dragStart by remember { mutableStateOf(Offset.Zero) }
        var draggedSinceDown by remember { mutableStateOf(false) }
        var flingJob by remember { mutableStateOf<Job?>(null) }
        val velocityTracker = remember { VelocityTracker() }
        if (!position.x.isFinite()) {
            position = Offset(maxX - with(density) { 16.dp.toPx() }, (maxY - with(density) { 132.dp.toPx() }).coerceAtLeast(0f))
        } else {
            position = Offset(position.x.coerceIn(0f, maxX), position.y.coerceIn(0f, maxY))
        }

        val glass = if (monet) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
        else if (backdrop != null && blurEnabled) Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                blur((if (liquidGlass) 2.dp else 6.dp).toPx())
                vibrancy()
                if (liquidGlass) {
                    lens(
                        refractionHeight = 12.dp.toPx(),
                        refractionAmount = 24.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true
                    )
                }
            },
            highlight = if (liquidGlass) ({ Highlight.Default }) else null,
            shadow = if (liquidGlass) ({ Shadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.25f)) }) else null,
            innerShadow = if (liquidGlass) ({ InnerShadow(radius = 4.dp, alpha = 0.55f) }) else null,
            onDrawSurface = { drawCircle(Color.White.copy(alpha = 0.10f)) }
        ) else Modifier.background(
            if (liquidGlass && !blurEnabled) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
            CircleShape
        )

        Box(
            Modifier
                .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                .size(haloSize)
                .then(glass)
                .clip(CircleShape)
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragStart = {
                            flingJob?.cancel()
                            velocityTracker.resetTracking()
                            dragStart = position
                            draggedSinceDown = false
                        },
                        onDragEnd = {
                            val delta = dragStart - position
                            if (delta.y > 160.dp.toPx() && abs(delta.y) > abs(delta.x) * 1.4f) onSwipeUp()
                            val velocity = velocityTracker.calculateVelocity()
                            flingJob = scope.launch {
                                var vx = velocity.x.coerceIn(-5000f, 5000f)
                                var vy = velocity.y.coerceIn(-5000f, 5000f)
                                var lastFrame = 0L
                                while (abs(vx) > 12f || abs(vy) > 12f) {
                                    withFrameNanos { frame ->
                                        if (lastFrame != 0L) {
                                            val dt = ((frame - lastFrame) / 1_000_000_000f).coerceAtMost(0.032f)
                                            val next = Offset(
                                                (position.x + vx * dt).coerceIn(0f, maxX),
                                                (position.y + vy * dt).coerceIn(0f, maxY)
                                            )
                                            if (next.x == 0f || next.x == maxX) vx = 0f
                                            if (next.y == 0f || next.y == maxY) vy = 0f
                                            position = next
                                            val friction = 0.90f
                                            vx *= friction
                                            vy *= friction
                                        }
                                        lastFrame = frame
                                    }
                                }
                            }
                            draggedSinceDown = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            position = Offset(
                                (position.x + dragAmount.x).coerceIn(0f, maxX),
                                (position.y + dragAmount.y).coerceIn(0f, maxY)
                            )
                            if ((dragStart - position).getDistance() > 12.dp.toPx()) draggedSinceDown = true
                        }
                    )
                }
                .clickable {
                    if (draggedSinceDown) draggedSinceDown = false else onTap()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(discSize).graphicsLayer { rotationZ = rotation.value % 360f }
                    .onGloballyPositioned { onArtworkBoundsChanged(it.boundsInRoot()) }
                    .clip(CircleShape).background(Color(0xFF111113)),
                contentAlignment = Alignment.Center
            ) {
                val displayArtUri = externalArtUri ?: song.albumArtUri
                if (displayArtUri != null) {
                    if (externalArtUri != null) {
                        AsyncImage(model = externalArtUri, contentDescription = song.title,
                            modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    } else {
                        AlbumArtwork(song, Modifier.matchParentSize(), song.title)
                    }
                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.2f)))
                }
                Canvas(Modifier.matchParentSize()) {
                    listOf(0.44f, 0.34f, 0.24f).forEach { ratio ->
                        drawCircle(Color.White.copy(alpha = 0.13f), radius = size.minDimension * ratio, style = Stroke(1.dp.toPx()))
                    }
                    drawCircle(Color.Black.copy(alpha = 0.72f), radius = size.minDimension * 0.16f)
                    drawCircle(accentColor, radius = size.minDimension * 0.055f)
                }
                if (durationMs > 0) {
                    Canvas(Modifier.matchParentSize()) {
                        drawArc(
                            color = accentColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progressFraction,
                            useCenter = false,
                            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onPlayPause)
                    .graphicsLayer { scaleX = playButtonScale; scaleY = playButtonScale },
                contentAlignment = Alignment.Center
            ) {
                val pausePainter = painterResource(R.drawable.ic_apple_pause_simple)
                val playPainter = painterResource(R.drawable.ic_apple_play_arrow)
                Icon(if (isPlaying) pausePainter else playPainter,
                    if (isPlaying) "暂停" else "播放", tint = Color.White,
                    modifier = Modifier.size(22.dp))
            }
        }
    }
}



@Composable
fun MusicPlayIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val strokeW = w * 0.14f
        val left = w * 0.28f; val right = w * 0.80f
        val top = h * 0.18f; val bottom = h * 0.82f
        drawPath(
            path = Path().apply {
                moveTo(left, top); lineTo(right, h / 2); lineTo(left, bottom); close()
            },
            color = tint,
            style = Stroke(width = strokeW, join = StrokeJoin.Round, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun MusicPauseIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val barW = w * 0.19f; val barH = h * 0.48f
        val gap = w * 0.14f; val cx = w / 2; val cy = h / 2
        val r = barW * 0.35f
        drawRoundRect(tint, Offset(cx - gap / 2 - barW, cy - barH / 2), Size(barW, barH), CornerRadius(r))
        drawRoundRect(tint, Offset(cx + gap / 2, cy - barH / 2), Size(barW, barH), CornerRadius(r))
    }
}

@Composable
fun MusicSkipNextIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val strokeW = w * 0.10f
        val barW = w * 0.07f; val barH = h * 0.38f
        val r = barW * 0.5f
        // Triangle on left
        drawPath(
            path = Path().apply {
                moveTo(w * 0.05f, h * 0.26f)
                lineTo(w * 0.46f, h / 2)
                lineTo(w * 0.05f, h * 0.74f)
                close()
            },
            color = tint,
            style = Stroke(width = strokeW, join = StrokeJoin.Round, cap = StrokeCap.Round)
        )
        // Vertical bar on right
        drawRoundRect(tint, Offset(w * 0.58f, h * 0.28f), Size(barW, barH), CornerRadius(r))
    }
}

@Composable
fun MusicSkipPreviousIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val strokeW = w * 0.10f
        val barW = w * 0.07f; val barH = h * 0.38f
        val r = barW * 0.5f
        // Vertical bar on left
        drawRoundRect(tint, Offset(w * 0.35f, h * 0.28f), Size(barW, barH), CornerRadius(r))
        // Triangle on right (mirrored: points left)
        drawPath(
            path = Path().apply {
                moveTo(w * 0.95f, h * 0.26f)
                lineTo(w * 0.54f, h / 2)
                lineTo(w * 0.95f, h * 0.74f)
                close()
            },
            color = tint,
            style = Stroke(width = strokeW, join = StrokeJoin.Round, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun MusicShuffleIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val strokeW = w * 0.10f
        val cx = w / 2f; val cy = h / 2f
        val arrowLen = w * 0.24f
        val arrowW = w * 0.12f
        val sw = strokeW
        // Top arrow: left → right, angled down
        drawPath(
            path = Path().apply {
                moveTo(cx - arrowLen, cy - arrowLen)
                lineTo(cx, cy)
                lineTo(cx + arrowLen, cy + arrowLen)
            },
            color = tint,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Top arrowhead
        drawPath(
            path = Path().apply {
                moveTo(cx + arrowLen, cy + arrowLen)
                lineTo(cx + arrowLen - arrowW, cy + arrowLen - arrowW * 0.4f)
                moveTo(cx + arrowLen, cy + arrowLen)
                lineTo(cx + arrowLen - arrowW * 0.4f, cy + arrowLen - arrowW)
            },
            color = tint,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Bottom arrow: left → right, angled up
        drawPath(
            path = Path().apply {
                moveTo(cx - arrowLen, cy + arrowLen)
                lineTo(cx, cy)
                lineTo(cx + arrowLen, cy - arrowLen)
            },
            color = tint,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Bottom arrowhead
        drawPath(
            path = Path().apply {
                moveTo(cx + arrowLen, cy - arrowLen)
                lineTo(cx + arrowLen - arrowW, cy - arrowLen + arrowW * 0.4f)
                moveTo(cx + arrowLen, cy - arrowLen)
                lineTo(cx + arrowLen - arrowW * 0.4f, cy - arrowLen + arrowW)
            },
            color = tint,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun MusicRepeatIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val strokeW = w * 0.10f
        val cx = w / 2f; val cy = h / 2f
        val r = w * 0.30f
        val arrowW = w * 0.12f
        val sw = strokeW
        // Arc: top-left to bottom-left going counter-clockwise
        drawArc(
            color = tint,
            startAngle = -30f,
            sweepAngle = -210f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
        // Arrowhead at bottom-left end
        val ax = cx - r * 0.85f; val ay = cy + r * 0.85f
        drawPath(
            path = Path().apply {
                moveTo(ax, ay)
                lineTo(ax - arrowW, ay - arrowW * 0.5f)
                moveTo(ax, ay)
                lineTo(ax + arrowW * 0.4f, ay - arrowW)
            },
            color = tint,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun MusicRepeatOneIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val strokeW = w * 0.10f
        val cx = w / 2f; val cy = h / 2f
        val r = w * 0.30f
        val arrowW = w * 0.12f
        val sw = strokeW
        // Arc: top-left to bottom-left going counter-clockwise
        drawArc(
            color = tint,
            startAngle = -30f,
            sweepAngle = -210f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
        // Arrowhead at bottom-left end
        val ax = cx - r * 0.85f; val ay = cy + r * 0.85f
        drawPath(
            path = Path().apply {
                moveTo(ax, ay)
                lineTo(ax - arrowW, ay - arrowW * 0.5f)
                moveTo(ax, ay)
                lineTo(ax + arrowW * 0.4f, ay - arrowW)
            },
            color = tint,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // "1" in center
        drawPath(
            path = Path().apply {
                moveTo(cx, cy - r * 0.4f)
                lineTo(cx, cy + r * 0.4f)
            },
            color = tint,
            style = Stroke(width = sw * 1.2f, cap = StrokeCap.Round)
        )
    }
}
