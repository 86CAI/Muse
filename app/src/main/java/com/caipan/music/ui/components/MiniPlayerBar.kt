// Direct structural port of Symphony NowPlayingBottomBar.kt (AGPL-3.0-only), adapted to Muse callbacks.
// Source: https://github.com/zyrouge/symphony @ dd04b872b8b4e6dd56172c053a5776c4d56ad080
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import android.net.Uri
import coil.compose.AsyncImage
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
    externalArtUri: Uri? = null
) {
    FloatingRecordPlayer(
        song, isPlaying, progressMs, durationMs, onPlayPause, onTap, onSwipeUp,
        backdrop, MaterialTheme.colorScheme.primary, modifier, externalArtUri
    )
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
    externalArtUri: Uri? = null
) {
    val density = LocalDensity.current
    val liquidGlass = LocalMuseLiquidGlass.current
    val blurEnabled = LocalMuseBlurPolicy.current.enabledAt(BlurLocation.MINI_PLAYER)
    val discSize = 96.dp
    val haloSize = 108.dp
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
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

        val glass = if (backdrop != null && blurEnabled) Modifier.drawBackdrop(
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
            shadow = if (liquidGlass) ({ Shadow.Default }) else null,
            innerShadow = if (liquidGlass) ({ InnerShadow(radius = 8.dp, alpha = .72f) }) else null,
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
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if ((dragStart - position).getDistance() > 12.dp.toPx()) draggedSinceDown = true
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            position = Offset(
                                (position.x + dragAmount.x).coerceIn(0f, maxX),
                                (position.y + dragAmount.y).coerceIn(0f, maxY)
                            )
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
                            sweepAngle = 360f * (progressMs.toFloat() / durationMs).coerceIn(0f, 1f),
                            useCenter = false,
                            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).size(36.dp).clip(CircleShape)
                    .background(accentColor).clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) "暂停" else "播放", tint = Color.White,
                    modifier = Modifier.size(21.dp))
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
