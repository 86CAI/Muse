/*
 * MeloX 通用控件（开关 / 滑块 / 分段 / 卡片）
 *
 * Ported from NEORUAA/Mei_MeloX_Android
 * (ui/glass/GlassControls.kt)：GlassToggle 64x28dp 轨道 + 40x24dp 弹性滑块，
 * GlassSlider 6dp 轨道 + 同尺寸滑块，GlassCard 在分组列表内退化为内缩分隔线。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule

/** 64 × 28 dp 轨道与 40 × 24 dp 弹性液态滑块。 */
@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop = LocalGlassBackdrop.current,
) {
    val colors = LocalGlassColors.current
    val isDark = colors.isDark
    val accent = if (isDark) Color(0xFF30D158) else Color(0xFF34C759)
    val trackColor = if (isDark) {
        Color(0xFF787880).copy(alpha = 0.36f)
    } else {
        Color(0xFF787878).copy(alpha = 0.20f)
    }
    val scope = rememberCoroutineScope()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val density = LocalDensity.current
    val travelPx = with(density) { 20.dp.toPx() }
    val trackBackdrop = rememberLayerBackdrop()
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    var didDrag by remember { mutableFloatStateOf(0f) }

    val animation = remember(scope, enabled) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = if (checked) 1f else 0f,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = { didDrag = 0f },
            onDragStopped = {
                val target = if (didDrag > 0f) {
                    if (targetValue >= 0.5f) 1f else 0f
                } else {
                    if (checked) 0f else 1f
                }
                animateToValue(target)
                onCheckedChange(target == 1f)
            },
            onDrag = { _, dragAmount ->
                didDrag = 1f
                updateValue((targetValue + dragAmount.x / travelPx).fastCoerceIn(0f, 1f))
            },
        )
    }
    LaunchedEffect(animation) {
        snapshotFlow { fraction }.collect { animation.updateValue(it) }
    }
    LaunchedEffect(checked) {
        animation.animateToValue(if (checked) 1f else 0f)
    }

    val progress = animation.value.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .size(width = 64.dp, height = 28.dp)
            .semantics { role = Role.Switch }
            .then(if (enabled) animation.modifier else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(trackBackdrop)
                .background(colorLerp(trackColor, accent, progress), Capsule()),
        )
        Box(
            Modifier
                .padding(2.dp)
                .size(width = 40.dp, height = 24.dp)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, trackBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val press = animation.pressProgress
                        blur(8.dp.toPx() * (1f - press))
                        lens(5.dp.toPx() * press, 10.dp.toPx() * press, chromaticAberration = true)
                    },
                    highlight = {
                        Highlight.Ambient.copy(alpha = animation.pressProgress)
                    },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    innerShadow = {
                        InnerShadow(
                            radius = 4.dp * animation.pressProgress,
                            alpha = animation.pressProgress,
                        )
                    },
                    layerBlock = {
                        scaleX = animation.scaleX
                        scaleY = animation.scaleY
                        val velocity = animation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        translationX = lerp(0f, travelPx, progress) * if (isLtr) 1f else -1f
                        alpha = if (enabled) 1f else 0.45f
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 1f - animation.pressProgress))
                    },
                ),
        )
    }
}

/** 6dp 轨道与 40 × 24 dp 液态滑块。 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    backdrop: Backdrop = LocalGlassBackdrop.current,
) {
    require(valueRange.start < valueRange.endInclusive)
    val colors = LocalGlassColors.current
    val isDark = colors.isDark
    val trackColor = if (isDark) {
        Color(0xFF787880).copy(alpha = 0.36f)
    } else {
        Color(0xFF787878).copy(alpha = 0.20f)
    }
    val scope = rememberCoroutineScope()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier = modifier.height(44.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidthPx = constraints.maxWidth.toFloat()
        val animation = remember(scope, valueRange, enabled) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = value.coerceIn(valueRange),
                valueRange = valueRange,
                visibilityThreshold = (valueRange.endInclusive - valueRange.start) / 1000f,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = { onValueChange(targetValue) },
                onDrag = { _, dragAmount ->
                    val span = valueRange.endInclusive - valueRange.start
                    val next = (targetValue + span * dragAmount.x / trackWidthPx)
                        .coerceIn(valueRange)
                    updateValue(next)
                    onValueChange(next)
                },
            )
        }
        LaunchedEffect(value) {
            if (value.coerceIn(valueRange) != animation.targetValue) {
                animation.animateToValue(value.coerceIn(valueRange))
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(trackBackdrop)
                .pointerInput(valueRange, enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val span = valueRange.endInclusive - valueRange.start
                        val fraction = (offset.x / size.width.coerceAtLeast(1)).fastCoerceIn(0f, 1f)
                        val next = valueRange.start + span * fraction
                        animation.animateToValue(next)
                        onValueChange(next)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .height(6.dp)
                    .clip(Capsule())
                    .background(trackColor),
            )
            Box(
                Modifier
                    .height(6.dp)
                    .clip(Capsule())
                    .background(colors.accent)
                    .layout { measurable, constraints ->
                        val width = (constraints.maxWidth * animation.progress).fastRoundToInt()
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = width, maxWidth = width),
                        )
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
            )
        }

        Box(
            Modifier
                .size(width = 40.dp, height = 24.dp)
                .then(if (enabled) animation.modifier else Modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, trackBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val press = animation.pressProgress
                        blur(8.dp.toPx() * (1f - press))
                        lens(10.dp.toPx() * press, 14.dp.toPx() * press, chromaticAberration = true)
                    },
                    highlight = { Highlight.Ambient.copy(alpha = animation.pressProgress) },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    innerShadow = {
                        InnerShadow(
                            radius = 4.dp * animation.pressProgress,
                            alpha = animation.pressProgress,
                        )
                    },
                    layerBlock = {
                        scaleX = animation.scaleX
                        scaleY = animation.scaleY
                        translationX = (
                            -size.width / 2f + trackWidthPx * animation.progress
                            ).fastCoerceIn(-size.width / 4f, trackWidthPx - size.width * 3f / 4f) *
                            if (isLtr) 1f else -1f
                        alpha = if (enabled) 1f else 0.45f
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 1f - animation.pressProgress))
                    },
                ),
        )
    }
}
