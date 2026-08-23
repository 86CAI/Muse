/*
 * Muse 液态玻璃动作按钮组。
 *
 * 其中的按压径向高光 adapted from Kyant0/AndroidLiquidGlass (Apache-2.0)
 * 的 `InteractiveHighlight.kt`，并将上游的 RuntimeShader 实现替换为
 * Brush.radialGradient，以便在不支持 AGSL 的设备上退化可用。
 *
 * Upstream: https://github.com/Kyant0/AndroidLiquidGlass
 * License: Apache License 2.0 —— 见 licenses/APACHE-2.0.txt
 */
package com.caipan.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.DialogProperties
import com.caipan.music.plugin.BlurLocation
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

private enum class LiquidActionStyle { FILLED, OUTLINED, TEXT }

private class LiquidPress(
    private val scope: kotlinx.coroutines.CoroutineScope,
    elasticity: Float,
    private val pressScale: Float
) {
    private val damping = 1f - elasticity.coerceIn(0f, 1f) * .65f
    private val stiffness = 1200f - elasticity.coerceIn(0f, 1f) * 1000f
    private val press = Animatable(0f, 0.001f)
    private val position = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var origin = Offset.Zero
    val progress get() = press.value
    val offset get() = position.value - origin

    /** 按压径向高光（移植 AndroidLiquidGlass 的 InteractiveHighlight）：按下出现、拖动跟随。 */
    val highlightModifier: Modifier = Modifier.drawWithContent {
        val p = press.value
        if (p > 0f) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f * p),
                        Color.White.copy(alpha = 0.06f * p),
                        Color.Transparent
                    ),
                    center = position.value,
                    radius = size.minDimension * 1.5f
                ),
                blendMode = BlendMode.Plus
            )
        }
        drawContent()
    }

    fun modifier(role: Role, onClick: () -> Unit): Modifier = Modifier
        .clickable(interactionSource = null, indication = null, role = role, onClick = onClick)
        .pointerInput(scope) {
            inspectLiquidDragGestures(
                onStart = { down ->
                    origin = down.position
                    scope.launch {
                        launch { press.animateTo(1f, spring(damping, stiffness, 0.001f)) }
                        launch { position.snapTo(origin) }
                    }
                },
                onEnd = { release() },
                onCancel = { release() },
                onDrag = { change, _ -> scope.launch { position.snapTo(change.position) } }
            )
        }

    fun release() {
        scope.launch {
            launch { press.animateTo(0f, spring(damping, stiffness, 0.001f)) }
            launch { position.animateTo(origin, spring(damping, stiffness, Offset.VisibilityThreshold)) }
        }
    }
}

@Composable
private fun LiquidAction(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    style: LiquidActionStyle,
    shape: Shape,
    contentPadding: PaddingValues,
    content: @Composable RowScope.() -> Unit
) {
    val backdrop = LocalMuseBackdrop.current
    val blurEnabled = LocalMuseBlurPolicy.current.enabledAt(LocalMuseControlBlurLocation.current)
    if (LocalMuseAppleStyle.current) {
        val appleShape = when (style) {
            LiquidActionStyle.TEXT -> RoundedCornerShape(10.dp)
            else -> RoundedCornerShape(14.dp)
        }
        val appleModifier = modifier.defaultMinSize(minHeight = 44.dp)
        when (style) {
            LiquidActionStyle.FILLED -> androidx.compose.material3.Button(
                onClick = onClick,
                modifier = appleModifier,
                enabled = enabled,
                shape = appleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = contentPadding,
                content = content
            )
            LiquidActionStyle.OUTLINED -> androidx.compose.material3.OutlinedButton(
                onClick = onClick,
                modifier = appleModifier,
                enabled = enabled,
                shape = appleShape,
                contentPadding = contentPadding,
                content = content
            )
            LiquidActionStyle.TEXT -> androidx.compose.material3.TextButton(
                onClick = onClick,
                modifier = appleModifier,
                enabled = enabled,
                shape = appleShape,
                contentPadding = contentPadding,
                content = content
            )
        }
        return
    }
    if (!LocalMuseLiquidGlass.current || backdrop == null || !blurEnabled) {
        when (style) {
            LiquidActionStyle.FILLED -> androidx.compose.material3.Button(onClick, modifier, enabled, shape = shape, contentPadding = contentPadding, content = content)
            LiquidActionStyle.OUTLINED -> androidx.compose.material3.OutlinedButton(onClick, modifier, enabled, shape = shape, contentPadding = contentPadding, content = content)
            LiquidActionStyle.TEXT -> androidx.compose.material3.TextButton(onClick, modifier, enabled, shape = shape, contentPadding = contentPadding, content = content)
        }
        return
    }
    val scope = rememberCoroutineScope()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.caipan.music.MuseApplication
    val glassConfig by app?.glassConfigStore?.state?.collectAsState() ?: remember { mutableStateOf(MuseGlassConfig()) }
    val latestClick by rememberUpdatedState(onClick)
    val press = remember(scope, glassConfig.elasticity, glassConfig.pressScale) {
        LiquidPress(scope, glassConfig.elasticity, glassConfig.pressScale)
    }
    val accent = MaterialTheme.colorScheme.primary
    val contentColor = when (style) {
        LiquidActionStyle.FILLED -> MaterialTheme.colorScheme.onPrimary
        LiquidActionStyle.OUTLINED -> MaterialTheme.colorScheme.onSurface
        LiquidActionStyle.TEXT -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val tint = when (style) {
        LiquidActionStyle.FILLED -> accent.copy(alpha = (.52f + glassConfig.themeColorIntensity * .4f).coerceAtMost(.92f))
        LiquidActionStyle.OUTLINED -> Color.White.copy(alpha = .06f)
        LiquidActionStyle.TEXT -> Color.White.copy(alpha = .035f)
    }
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else .42f }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = { vibrancy(); blur(2.dp.toPx()); lens(12.dp.toPx(), 24.dp.toPx(), chromaticAberration = true) },
                highlight = { Highlight.Ambient.copy(alpha = .65f + press.progress * .35f) },
                shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = .14f)) },
                innerShadow = { InnerShadow(radius = 4.dp, alpha = .4f + press.progress * .35f) },
                layerBlock = {
                    val scale = lerp(1f, glassConfig.pressScale, press.progress)
                    val maxOffset = size.minDimension
                    translationX = maxOffset * tanh(.05f * press.offset.x / maxOffset)
                    translationY = maxOffset * tanh(.05f * press.offset.y / maxOffset)
                    val angle = atan2(press.offset.y, press.offset.x)
                    scaleX = scale + 4.dp.toPx() / size.height * abs(cos(angle) * press.offset.x / size.maxDimension) * (size.width / size.height).fastCoerceAtMost(1f)
                    scaleY = scale + 4.dp.toPx() / size.height * abs(sin(angle) * press.offset.y / size.maxDimension) * (size.height / size.width).fastCoerceAtMost(1f)
                },
                onDrawSurface = { drawRect(tint); if (style == LiquidActionStyle.FILLED) drawRect(accent.copy(alpha = .28f), blendMode = BlendMode.Hue) }
            )
            .then(if (enabled) press.highlightModifier else Modifier)
            .then(if (enabled) press.modifier(Role.Button) { press.release(); latestClick() } else Modifier)
            .defaultMinSize(minHeight = 48.dp)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = {
            CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
        }
    )
}

@Composable
fun MuseButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
               shape: Shape = RoundedCornerShape(50), colors: ButtonColors = ButtonDefaults.buttonColors(),
               elevation: ButtonElevation? = ButtonDefaults.buttonElevation(), border: BorderStroke? = null,
               contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
               interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) =
    LiquidAction(onClick, modifier, enabled, LiquidActionStyle.FILLED, shape, contentPadding, content)

@Composable
fun MuseOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                       shape: Shape = RoundedCornerShape(50), colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
                       elevation: ButtonElevation? = null, border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
                       contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
                       interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) =
    LiquidAction(onClick, modifier, enabled, LiquidActionStyle.OUTLINED, shape, contentPadding, content)

@Composable
fun MuseTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                   shape: Shape = RoundedCornerShape(50), colors: ButtonColors = ButtonDefaults.textButtonColors(),
                   elevation: ButtonElevation? = null, border: BorderStroke? = null,
                   contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
                   interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) =
    LiquidAction(onClick, modifier, enabled, LiquidActionStyle.TEXT, shape, contentPadding, content)

@Composable
fun MuseFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    LiquidAction(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = if (selected) LiquidActionStyle.FILLED else LiquidActionStyle.OUTLINED,
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
    ) {
        leadingIcon?.invoke()
        label()
        trailingIcon?.invoke()
    }
}

@Composable
fun MuseIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                   colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
                   interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) {
    val backdrop = LocalMuseBackdrop.current
    val blurEnabled = LocalMuseBlurPolicy.current.enabledAt(LocalMuseControlBlurLocation.current)
    if (!LocalMuseLiquidGlass.current || backdrop == null || !blurEnabled) {
        androidx.compose.material3.IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
            content = content
        )
        return
    }
    val scope = rememberCoroutineScope()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.caipan.music.MuseApplication
    val glassConfig by app?.glassConfigStore?.state?.collectAsState() ?: remember { mutableStateOf(MuseGlassConfig()) }
    val latestClick by rememberUpdatedState(onClick)
    val accent = MaterialTheme.colorScheme.primary
    val press = remember(scope, glassConfig.elasticity, glassConfig.pressScale) {
        LiquidPress(scope, glassConfig.elasticity, glassConfig.pressScale)
    }
    Box(modifier.size(48.dp).graphicsLayer { alpha = if (enabled) 1f else .42f }.drawBackdrop(
        backdrop = backdrop, shape = { CircleShape }, effects = {
            vibrancy(); blur(2.dp.toPx()); lens(10.dp.toPx(), 20.dp.toPx(), chromaticAberration = true)
        }, highlight = { Highlight.Ambient.copy(alpha = .65f + press.progress * .35f) },
        shadow = { Shadow(radius = 6.dp, color = Color.Black.copy(.12f)) },
        innerShadow = { InnerShadow(4.dp, alpha = .5f) },
        layerBlock = { val scale = lerp(1f, glassConfig.pressScale, press.progress); scaleX = scale; scaleY = scale },
        onDrawSurface = {
            drawRect(Color.White.copy(alpha = .055f))
            drawRect(accent.copy(alpha = glassConfig.themeColorIntensity * .28f))
        }
    ).then(if (enabled) press.highlightModifier else Modifier)
     .then(if (enabled) press.modifier(Role.Button) { press.release(); latestClick() } else Modifier), Alignment.Center) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuseAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(30.dp),
    containerColor: Color = Color.Transparent,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    BasicAlertDialog(onDismissRequest, properties = properties, modifier = Modifier) {
        DialogBlurEffect(location = BlurLocation.SHEETS)
        val solidSurface = !LocalMuseLiquidGlass.current
        Column(
            modifier.fillMaxWidth().widthIn(max = 400.dp)
                .then(
                    if (solidSurface) {
                        Modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    } else {
                        Modifier.museGlass(
                            LocalMuseBackdrop.current,
                            shape,
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .56f),
                            blurRadius = 24.dp,
                            location = BlurLocation.SHEETS
                        )
                    }
                )
                .padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            icon?.let { Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp)) { it() } }
            title?.let { ProvideTextStyle(MaterialTheme.typography.titleLarge) { Box(Modifier.padding(bottom = 10.dp)) { it() } } }
            text?.let { ProvideTextStyle(MaterialTheme.typography.bodyMedium) { Box(Modifier.padding(bottom = 22.dp)) { it() } } }
            CompositionLocalProvider(LocalMuseLiquidGlass provides false) {
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
