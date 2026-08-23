/*
 * Muse 液态玻璃基础控件（开关 / 滑块 / 分段选择）。
 *
 * Adapted from Kyant0/AndroidLiquidGlass (Apache-2.0) 的 catalog 示例
 * `LiquidToggle.kt` 与 `LiquidSlider.kt`：
 * MuseGlassSwitch 沿用其 didDrag/fraction 双状态、20dp 拖拽行程、
 * 64×28dp 轨道 / 40×24dp 滑块尺寸、#787880 轨道色，
 * 以及依据速度做水平剪切的 layerBlock（scaleX 随速度反向缩放）。
 *
 * Upstream: https://github.com/Kyant0/AndroidLiquidGlass
 * License: Apache License 2.0 —— 见 licenses/APACHE-2.0.txt
 */
package com.caipan.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest

private val LiquidCapsule = RoundedCornerShape(50)

@Composable
fun MuseGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    backdrop: Backdrop? = null,
    enabled: Boolean = true
) {
    if (LocalMuseAppleStyle.current || !LocalMuseLiquidGlass.current || backdrop == null ||
        !LocalMuseBlurPolicy.current.enabledAt(LocalMuseControlBlurLocation.current)) {
        Switch(checked, onCheckedChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = accentColor))
        return
    }
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(androidx.compose.ui.platform.LocalDensity.current) { 20.dp.toPx() }
    val scope = rememberCoroutineScope()
    val glassConfig by (androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.caipan.music.MuseApplication)
        ?.glassConfigStore?.state?.collectAsState() ?: remember { mutableStateOf(MuseGlassConfig()) }
    val latestChecked by rememberUpdatedState(checked)
    val latestOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val latestEnabled by rememberUpdatedState(enabled)
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { androidx.compose.runtime.mutableFloatStateOf(if (checked) 1f else 0f) }
    val motion = remember(scope) {
        LiquidControlMotion(scope, fraction, 0f..1f, 0.001f, glassConfig.elasticity, glassConfig.pressScale,
            onDragStopped = {
                val selected = if (didDrag) targetValue >= 0.5f else !latestChecked
                fraction = if (selected) 1f else 0f
                latestOnCheckedChange(selected)
                didDrag = false
            },
            onDrag = { _, amount ->
                if (latestEnabled) {
                    didDrag = didDrag || amount.x != 0f
                    val delta = amount.x / dragWidth
                    fraction = (fraction + if (isLtr) delta else -delta).fastCoerceIn(0f, 1f)
                }
            })
    }
    LaunchedEffect(motion) { snapshotFlow { fraction }.collectLatest(motion::updateValue) }
    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (target != fraction) { fraction = target; motion.animateToValue(target) }
    }
    val trackBackdrop = rememberLayerBackdrop()
    val trackColor = Color(0xFF787880).copy(alpha = if (checked) .20f else .32f)
    Box(Modifier.size(64.dp, 30.dp).graphicsLayer { alpha = if (enabled) 1f else .45f }, Alignment.CenterStart) {
        Box(Modifier.layerBackdrop(trackBackdrop).clip(LiquidCapsule)
            .background(androidx.compose.ui.graphics.lerp(trackColor, accentColor, motion.currentValue))
            .size(64.dp, 28.dp))
        Box(Modifier.graphicsLayer {
            val padding = 2.dp.toPx()
            translationX = (if (isLtr) lerp(padding, padding + 20.dp.toPx(), motion.currentValue)
                else lerp(-padding, -(padding + 20.dp.toPx()), motion.currentValue))
        }.semantics {
            role = Role.Switch
            stateDescription = if (checked) "开" else "关"
            if (!enabled) disabled()
        }.then(if (enabled) motion.modifier else Modifier).drawBackdrop(
            backdrop = backdrop,
            shape = { LiquidCapsule },
            effects = {
                val p = motion.pressProgress
                blur(8.dp.toPx() * (1f - p))
                lens(5.dp.toPx() * p, 10.dp.toPx() * p, chromaticAberration = true)
            },
            highlight = { Highlight.Ambient.copy(alpha = motion.pressProgress) },
            shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = .05f)) },
            innerShadow = { InnerShadow(4.dp * motion.pressProgress, alpha = motion.pressProgress) },
            layerBlock = {
                scaleX = motion.scaleX
                scaleY = motion.scaleY
                val velocity = motion.velocity / 50f
                scaleX /= 1f - (velocity * .75f).fastCoerceIn(-.2f, .2f)
                scaleY *= 1f - (velocity * .25f).fastCoerceIn(-.2f, .2f)
            },
            onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - motion.pressProgress)) }
        ).size(40.dp, 24.dp))
    }
}

@Composable
fun MuseGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null
) {
    if (!LocalMuseLiquidGlass.current || backdrop == null ||
        !LocalMuseBlurPolicy.current.enabledAt(LocalMuseControlBlurLocation.current)) {
        Slider(value, onValueChange, modifier, enabled, onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange, colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor))
        return
    }
    val trackBackdrop = rememberLayerBackdrop()
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val latestEnabled by rememberUpdatedState(enabled)
    val glassConfig by (androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.caipan.music.MuseApplication)
        ?.glassConfigStore?.state?.collectAsState() ?: remember { mutableStateOf(MuseGlassConfig()) }
    BoxWithConstraints(modifier.fillMaxWidth().height(40.dp).graphicsLayer { alpha = if (enabled) 1f else .45f }, Alignment.CenterStart) {
        val trackWidth = constraints.maxWidth
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val scope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        val motion = remember(scope) {
            LiquidControlMotion(scope, value, valueRange, (valueRange.endInclusive - valueRange.start) / 1000f,
                glassConfig.elasticity, glassConfig.pressScale,
                onDragStopped = { if (didDrag) latestOnValueChangeFinished?.invoke(); didDrag = false },
                onDrag = { _, amount ->
                    if (latestEnabled && trackWidth > 0) {
                        didDrag = didDrag || amount.x != 0f
                        val delta = (valueRange.endInclusive - valueRange.start) * amount.x / trackWidth
                        latestOnValueChange((targetValue + if (isLtr) delta else -delta).coerceIn(valueRange))
                    }
                })
        }
        LaunchedEffect(motion, value) { if (motion.targetValue != value) motion.updateValue(value) }
        Canvas(Modifier.layerBackdrop(trackBackdrop).clip(LiquidCapsule)
            .pointerInput(enabled, scope, trackWidth) { if (enabled && trackWidth > 0) detectTapGestures { position ->
                    val fraction = (position.x / trackWidth).coerceIn(0f, 1f)
                    val target = (if (isLtr) valueRange.start + (valueRange.endInclusive - valueRange.start) * fraction
                        else valueRange.endInclusive - (valueRange.endInclusive - valueRange.start) * fraction).coerceIn(valueRange)
                    motion.animateToValue(target, showPress = false); latestOnValueChange(target); latestOnValueChangeFinished?.invoke()
                } }.height(6.dp).fillMaxWidth()) {
            val radius = size.height / 2f
            drawRoundRect(Color(0xFF787880).copy(.30f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius))
            val activeWidth = size.width * motion.progress.coerceIn(0f, 1f)
            if (activeWidth >= 1f) {
                drawRoundRect(
                    color = accentColor,
                    size = androidx.compose.ui.geometry.Size(activeWidth, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
                )
            }
        }
        Box(Modifier.graphicsLayer {
            translationX = (-size.width / 2f + trackWidth * motion.progress)
                .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
        }.then(if (enabled) motion.modifier else Modifier).drawBackdrop(
            backdrop = backdrop, shape = { LiquidCapsule }, effects = {
                val p = motion.pressProgress
                blur(8.dp.toPx() * (1f - p))
                if (p > 0.001f) lens(10.dp.toPx() * p, 14.dp.toPx() * p, chromaticAberration = true)
            }, highlight = { Highlight.Ambient.copy(alpha = motion.pressProgress) },
            shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(.05f)) },
            innerShadow = { InnerShadow(4.dp * motion.pressProgress, alpha = motion.pressProgress) },
            layerBlock = {
                scaleX = motion.scaleX; scaleY = motion.scaleY
                val velocity = motion.velocity / 10f
                scaleX /= 1f - (velocity * .75f).fastCoerceIn(-.2f, .2f)
                scaleY *= 1f - (velocity * .25f).fastCoerceIn(-.2f, .2f)
            }, onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - motion.pressProgress)) }
        ).size(40.dp, 24.dp))
    }
}

@Composable
fun MuseGlassRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    backdrop: Backdrop? = LocalMuseBackdrop.current,
    enabled: Boolean = true
) {
    if (!LocalMuseLiquidGlass.current || backdrop == null ||
        !LocalMuseBlurPolicy.current.enabledAt(LocalMuseControlBlurLocation.current)) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick, enabled = enabled,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = accentColor))
        return
    }
    Box(
        Modifier.size(30.dp).graphicsLayer { alpha = if (enabled) 1f else .42f }.drawBackdrop(
            backdrop = backdrop,
            shape = { androidx.compose.foundation.shape.CircleShape },
            effects = { blur(4.dp.toPx()); lens(8.dp.toPx(), 16.dp.toPx(), chromaticAberration = true) },
            highlight = { Highlight.Ambient },
            shadow = { Shadow(radius = 5.dp, color = Color.Black.copy(.10f)) },
            innerShadow = { InnerShadow(radius = 3.dp, alpha = .48f) },
            onDrawSurface = { drawRect(Color.White.copy(alpha = .08f)) }
        ).semantics { role = Role.RadioButton; stateDescription = if (selected) "已选择" else "未选择" }
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedVisibility(selected) {
            Box(Modifier.size(14.dp).clip(androidx.compose.foundation.shape.CircleShape).background(accentColor))
        }
    }
}
