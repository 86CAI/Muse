/*
 * Mineradio 风格歌词舞台。
 *
 * Ported from XxHuberrr/Mineradio (GPL-3.0) 的 `public/desktop-lyrics.html`：
 * :root 调色板（--lyric-primary / --lyric-highlight / --lyric-glow / --lyric-feather）、
 * --lyric-size 58px / --lyric-weight 900 / --lyric-line-height 1 排版参数、
 * lyr-in 入场动画（820ms cubic-bezier(.16,.84,.32,1.02)）、
 * fitLyricText() 自适应字号、applyStageMotion() 舞台位移与
 * --lyric-mask-edge-width 边缘遮罩，全部由 CSS/JS 改写为 Compose 实现。
 *
 * Upstream: https://github.com/XxHuberrr/Mineradio
 * License: GNU General Public License v3.0 —— 见 licenses/GPL-3.0.txt
 */
package com.caipan.music.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caipan.music.model.Song
import com.caipan.music.player.LyricLine
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// 忠实还原 Mineradio desktop-lyrics.html 的调色板 (:root CSS variables)
private val LyricPrimary = Color(0xFFF6FDFF)   // --lyric-primary
private val LyricHighlight = Color(0xFFFFF0B8) // --lyric-highlight
private val LyricGlow = Color(0xFF9CFFDF)      // --lyric-glow

// --lyric-feather:5.5%
private const val LyricFeather = 0.055f

// --lyric-size:58px, --lyric-weight:900, --lyric-line-height:1
private const val BaseLyricSizePx = 58f

// lyr-in 进场动画 (820ms cubic-bezier(.16,.84,.32,1.02))
private val LyricInEasing = CubicBezierEasing(0.16f, 0.84f, 0.32f, 1.02f)

/** 歌词行自适应/滚动布局结果,对应 desktop-lyrics.html 的 fitLyricText() 计算。 */
private data class LyricFitResult(
    val fontSizePx: Float,
    val fitScaleX: Float,
    val limitPx: Float,
    val needed: Boolean,
    val maskEdgePx: Float
)

/**
 * 复刻 desktop-lyrics.html 的 fitLyricText():
 * 58px 起迭代缩小,超宽时 scaleX 压缩,仍溢出则横向往复滚动。
 */
private fun computeLyricFit(
    text: String,
    measurer: TextMeasurer,
    baseStyle: TextStyle,
    baseFontSizePx: Float,
    maxWidthPx: Float,
    maxHeightPx: Float,
    density: Float
): LyricFitResult {
    val edgePx = (maxWidthPx * 0.085f).coerceIn(54f * density, 116f * density)
    val clearWidthPx = max(160f * density, maxWidthPx - edgePx * 2f)
    val maxScrollableWidth = clearWidthPx * 1.76f
    val minSizePx = max(24f * density, min(32f * density, baseFontSizePx * 0.55f))

    fun measureWidth(sizePx: Float): Float {
        val layout = measurer.measure(
            AnnotatedString(text),
            style = baseStyle.copy(
                fontSize = (sizePx / density).sp,
                lineHeight = (sizePx / density).sp
            )
        )
        return layout.size.width.toFloat()
    }

    var size = baseFontSizePx
    for (i in 0 until 24) {
        val w = measureWidth(size)
        if (w <= maxScrollableWidth && size <= maxHeightPx) break
        if (size <= minSizePx) break
        size = max(minSizePx, size - max(1.25f * density, size * 0.062f))
    }
    val measured = measureWidth(size)
    val maxRenderedWidth = clearWidthPx * 1.82f
    val fitScaleX = if (measured > maxRenderedWidth) (maxRenderedWidth / measured).coerceIn(0.72f, 1f) else 1f
    val scaledWidth = measured * fitScaleX
    val travelWidth = max(0f, scaledWidth - clearWidthPx)
    val clearTailMargin = max(58f * density, min(edgePx * 1.18f, size * 1.08f))
    val centeredTailLimit = if (scaledWidth > clearWidthPx * 1.28f) {
        max(0f, scaledWidth / 2f - clearWidthPx * 0.18f)
    } else 0f
    val limit = if (travelWidth > 0f) max(travelWidth / 2f + clearTailMargin, centeredTailLimit) else 0f
    val needed = travelWidth > max(16f * density, size * 0.18f)
    val maskEdge = if (needed) {
        (edgePx * 0.44f).coerceIn(26f * density, 58f * density)
    } else edgePx
    return LyricFitResult(size, fitScaleX, limit, needed, maskEdge)
}

@Composable
fun MineradioLyricsScreen(
    song: Song,
    lyrics: List<LyricLine>,
    progressMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (previousOrientation != null) activity.requestedOrientation = previousOrientation
        }
    }

    // 当前歌词行索引 + 该行内进度 (0..1),用于卡拉OK擦除渐变
    val sorted = remember(lyrics) { lyrics.sortedBy { it.timeMs } }
    val currentIndex = remember(sorted, progressMs) {
        if (sorted.isEmpty()) -1
        else {
            var idx = -1
            for (i in sorted.indices) {
                if (sorted[i].timeMs <= progressMs) idx = i else break
            }
            idx
        }
    }
    val lineProgress = remember(sorted, currentIndex, progressMs) {
        if (currentIndex < 0 || currentIndex >= sorted.size) 0f
        else {
            val start = sorted[currentIndex].timeMs
            val end = if (currentIndex + 1 < sorted.size) sorted[currentIndex + 1].timeMs
            else if (durationMs > start) durationMs else start + 4000L
            val span = (end - start).coerceAtLeast(1L)
            ((progressMs - start).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        }
    }

    // 全局无限时钟(秒),驱动舞台浮动与呼吸节拍(桌面端由真实节拍分析驱动,这里为节奏模拟)
    val transition = rememberInfiniteTransition(label = "stage")
    val now by transition.animateFloat(
        initialValue = 0f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 120_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "now"
    )

    // applyStageMotion(): 舞台漂浮 / 轻微 3D 倾斜
    val floatY = sin(now * 1.08f) * -9.8f + sin(now * 2.10f + 0.7f) * 3.1f
    val floatX = sin(now * 0.70f + 0.4f) * 6.2f + sin(now * 1.18f + 1.1f) * 2.6f
    val rotX = sin(now * 0.86f + 0.2f) * 3.25f
    val rotY = sin(now * 0.74f + 1.3f) * -2.75f
    // localBeat = pow(max(0,sin(now*2.35)),8)*.44 (仅播放时),只驱动辉光强度
    val localBeat = if (isPlaying) max(0f, sin(now * 2.35f)).pow(8) * 0.44f else 0f

    val currentText = if (currentIndex >= 0 && currentIndex < sorted.size) {
        sorted[currentIndex].text
    } else {
        song.title
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF04060A))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // 背景:近黑基底 + 中心处专辑主色柔光晕 + 上下渐暗
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF04060A))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.22f),
                            accentColor.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        radius = 1400f
                    )
                )
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF04060A).copy(alpha = 0.30f),
                            Color.Transparent,
                            Color(0xFF04060A).copy(alpha = 0.55f)
                        )
                    )
                )
        )

        // The desktop stage uses a deep five-row 3D track. Mobile keeps that
        // hierarchy in stable slots so lyrics stay readable around cutouts and on
        // short landscape displays.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .graphicsLayer {
                    translationX = floatX
                    translationY = floatY
                    rotationX = rotX
                    rotationY = rotY
                }
        ) {
            val compactLandscape = maxHeight < 380.dp || maxWidth <= maxHeight
            val slots = if (compactLandscape) CompactLyricSlots else CinemaLyricSlots

            if (currentIndex >= 0) {
                slots.forEach { slot ->
                    val contextLine = sorted.getOrNull(currentIndex + slot.lineOffset)
                    if (contextLine != null) {
                        ContextLyricLine(
                            text = contextLine.text,
                            slot = slot,
                            modifier = Modifier
                                .align(slot.alignment)
                                .fillMaxWidth(slot.widthFraction)
                                .padding(top = slot.topPadding, bottom = slot.bottomPadding)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(if (compactLandscape) 0.88f else 0.78f)
                    .fillMaxHeight(if (compactLandscape) 0.56f else 0.48f),
                contentAlignment = Alignment.Center
            ) {
                StageLyricLine(
                    text = currentText,
                    progress = lineProgress,
                    beat = localBeat,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private data class MobileLyricSlot(
    val lineOffset: Int,
    val alignment: Alignment,
    val textAlign: TextAlign,
    val widthFraction: Float,
    val fontSizeSp: Int,
    val alpha: Float,
    val scale: Float,
    val topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    val bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
)

private val CompactLyricSlots = listOf(
    MobileLyricSlot(-1, Alignment.TopStart, TextAlign.Start, 0.54f, 20, 0.52f, 0.92f, topPadding = 6.dp),
    MobileLyricSlot(1, Alignment.BottomEnd, TextAlign.End, 0.54f, 20, 0.52f, 0.92f, bottomPadding = 6.dp)
)

private val CinemaLyricSlots = listOf(
    MobileLyricSlot(-2, Alignment.TopEnd, TextAlign.End, 0.34f, 17, 0.28f, 0.82f, topPadding = 2.dp),
    MobileLyricSlot(-1, Alignment.TopStart, TextAlign.Start, 0.46f, 23, 0.54f, 0.90f, topPadding = 38.dp),
    MobileLyricSlot(1, Alignment.BottomEnd, TextAlign.End, 0.46f, 23, 0.54f, 0.90f, bottomPadding = 38.dp),
    MobileLyricSlot(2, Alignment.BottomStart, TextAlign.Start, 0.34f, 17, 0.28f, 0.82f, bottomPadding = 2.dp)
)

@Composable
private fun ContextLyricLine(
    text: String,
    slot: MobileLyricSlot,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = text,
        modifier = modifier.graphicsLayer {
            scaleX = slot.scale
            scaleY = slot.scale
        },
        transitionSpec = {
            (fadeIn(tween(260)) + scaleIn(initialScale = 0.96f, animationSpec = tween(260))) togetherWith
                fadeOut(tween(160))
        },
        label = "contextLyric${slot.lineOffset}"
    ) { line ->
        Text(
            text = line,
            color = LyricPrimary.copy(alpha = slot.alpha),
            fontSize = slot.fontSizeSp.sp,
            lineHeight = (slot.fontSizeSp + 4).sp,
            fontWeight = if (kotlin.math.abs(slot.lineOffset) == 1) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = slot.textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 单行歌词,忠实还原 desktop-lyrics.html 的 .line:
 * - 58px 基础字号(超宽自适应缩小),font-weight:900,居中
 * - body.highlight .line 的卡拉OK线性渐变擦除
 * - -webkit-text-stroke 细白描边 (paint-order:stroke fill)
 * - text-shadow 辉光 (shadow-soft / shadow-glow),随节拍增强
 * - 超长行横向往复滚动 + 左右 mask 渐隐
 * - lyr-in 进场动画,暂停时整体 opacity:.84
 */
@Composable
private fun StageLyricLine(
    text: String,
    progress: Float,
    beat: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val textMeasurer = rememberTextMeasurer()
    val baseStyle = remember {
        TextStyle(
            fontWeight = FontWeight.W900,
            textAlign = TextAlign.Center
        )
    }
    val p = progress.coerceIn(0f, 1f)

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val fit = remember(text, constraints.maxWidth, constraints.maxHeight, density) {
            computeLyricFit(
                text = text,
                measurer = textMeasurer,
                baseStyle = baseStyle,
                baseFontSizePx = BaseLyricSizePx * density,
                maxWidthPx = constraints.maxWidth.toFloat(),
                maxHeightPx = constraints.maxHeight.toFloat(),
                density = density
            )
        }
        val fontSizeSp = fit.fontSizePx / density
        val fontSizeUnit = fontSizeSp.sp
        val annotated = remember(text) { AnnotatedString(text) }
        val lineStyle = remember(text, fontSizeUnit) {
            baseStyle.copy(fontSize = fontSizeUnit, lineHeight = fontSizeUnit)
        }
        val layout = remember(text, fontSizeUnit) { textMeasurer.measure(annotated, lineStyle) }
        val stops = remember(p) { buildKaraokeStops(p) }
        val brush = remember(stops) { Brush.horizontalGradient(colorStops = stops) }
        val fillStyle = remember(brush, fontSizeUnit) { lineStyle.copy(brush = brush) }

        // 辉光强度随呼吸节拍增强 (--lyric-css-beat-glow)
        val glowColor = LyricGlow.copy(alpha = 0.34f + beat * 0.22f)
        // 极细白描边:.18px 在移动端太细,取 0.6dp 等效
        val strokePx = 0.6f * density

        // 超长行横向往复滚动 (方向/停留对齐 desktop-lyrics.html)
        val scroll = remember(text, fit.limitPx) { Animatable(0f) }
        LaunchedEffect(text, fit.limitPx) {
            scroll.snapTo(0f)
            if (fit.limitPx <= 0f) return@LaunchedEffect
            val holdMs = 520L
            val travelMs = (2200f + fit.limitPx * 9f).toInt().coerceIn(1600, 5600)
            val ease = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)
            delay(holdMs)
            while (isActive) {
                scroll.animateTo(-fit.limitPx, tween(travelMs, easing = ease))
                delay(holdMs)
                scroll.animateTo(fit.limitPx, tween(travelMs, easing = ease))
                delay(holdMs)
            }
        }

        // lyr-in 进场动画:translate3d(0,32px,-120px) rotateX(24) rotateY(-18) scale(.78) blur(12px) → 归零
        val enter = remember(text) { Animatable(0f) }
        LaunchedEffect(text) {
            enter.snapTo(0f)
            enter.animateTo(1f, tween(820, easing = LyricInEasing))
        }
        val e = enter.value
        val enterAlpha = min(1f, e / 0.58f)
        // body.paused .line { opacity:.84 }
        val pausedAlpha = if (isPlaying) 1f else 0.84f

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // 横向滚动容器 (translate 在外,scaleX 压缩在内,与 CSS transform 顺序一致)
            Box(
                modifier = Modifier.graphicsLayer {
                    translationX = scroll.value
                    scaleX = fit.fitScaleX
                },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = enterAlpha * pausedAlpha
                            translationY = 32f * density * (1f - e)
                            rotationX = 24f * (1f - e)
                            rotationY = -18f * (1f - e)
                            scaleX = 0.78f + 0.22f * e
                            scaleY = 0.78f + 0.22f * e
                        }
                        .blur(12.dp * (1f - e)),
                    contentAlignment = Alignment.Center
                ) {
                    // 底层模糊辉光副本 (替代 text-shadow bloom)
                    Text(
                        text = text,
                        color = glowColor,
                        fontSize = fontSizeUnit,
                        fontWeight = FontWeight.W900,
                        textAlign = TextAlign.Center,
                        lineHeight = fontSizeUnit,
                        modifier = Modifier
                            .blur(22.dp)
                            .graphicsLayer {
                                scaleX = 1.02f + beat * 0.03f
                                scaleY = 1.02f + beat * 0.03f
                            }
                    )
                    // 主歌词:细白描边 + 卡拉OK渐变填充 (paint-order:stroke fill)
                    Canvas(
                        modifier = Modifier.size(
                            with(LocalDensity.current) { layout.size.width.toDp() },
                            with(LocalDensity.current) { layout.size.height.toDp() }
                        )
                    ) {
                        drawText(
                            textLayoutResult = layout,
                            color = Color.White.copy(alpha = 0.72f),
                            topLeft = Offset.Zero,
                            drawStyle = Stroke(width = strokePx)
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = annotated,
                            topLeft = Offset.Zero,
                            style = fillStyle
                        )
                    }
                }
            }
            // 左右 mask 渐隐,溶入背景 (对应 --lyric-mask-edge-width)
            if (fit.needed) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(fit.maskEdgePx.dp)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF04060A), Color.Transparent)))
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(fit.maskEdgePx.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF04060A))))
                )
            }
        }
    }
}

/**
 * 构造卡拉OK渐变的 colorStops,保证 offset 严格递增且落在 [0,1],
 * 避免 Brush.horizontalGradient 因 offset 非单调而抛出异常(旧版闪退根因)。
 */
private fun buildKaraokeStops(progress: Float): Array<Pair<Float, Color>> {
    val eps = 0.0008f
    val p = progress.coerceIn(0f, 1f)

    val o0 = 0f
    var o1 = max(0f, p - LyricFeather)
    var o2 = min(1f, p + 0.012f)
    var o3 = min(1f, p + LyricFeather)
    val o4 = 1f

    // 强制严格递增
    o1 = o1.coerceIn(o0 + eps, o4 - 4 * eps)
    o2 = o2.coerceIn(o1 + eps, o4 - 3 * eps)
    o3 = o3.coerceIn(o2 + eps, o4 - eps)

    return arrayOf(
        o0 to LyricHighlight,
        o1 to LyricHighlight,
        o2 to LyricGlow,
        o3 to LyricPrimary,
        o4 to LyricPrimary
    )
}
