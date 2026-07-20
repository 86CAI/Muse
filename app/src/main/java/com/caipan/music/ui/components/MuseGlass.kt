package com.caipan.music.ui.components

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.caipan.music.plugin.BlurLocation
import com.caipan.music.plugin.BlurPolicy
import com.caipan.music.ui.theme.MuseDesign
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/** `false` keeps Muse Original on classic frosted blur; `true` enables Liquid Glass. */
val LocalMuseLiquidGlass = staticCompositionLocalOf { false }
val LocalMuseBlurPolicy = staticCompositionLocalOf { BlurPolicy() }

/**
 * Muse 的 Apple 风格 Liquid Glass 材质。
 *
 * 先用背景模糊建立磨砂基底，再通过带深度的透镜折射和 RGB 色散处理边缘。
 * 这与 AndroidLiquidGlass 的悬浮控件方案一致：玻璃会真实扭曲其后的动态
 * 壁纸，并在高对比背景上呈现细微的彩色折射，而不是单纯的半透明蒙层。
 */
@Composable
fun Modifier.museGlass(
    backdrop: Backdrop?,
    shape: Shape,
    tint: Color,
    blurRadius: Dp = 12.dp,
    borderColor: Color = Color.Transparent,
    liquidGlass: Boolean = LocalMuseLiquidGlass.current,
    location: BlurLocation = BlurLocation.CARDS,
    readabilityBoost: Boolean = false,
    /** Set to 0.dp for full-screen glass that must remain edge-to-edge. */
    cornerRadius: Dp? = null
): Modifier {
    val app = LocalContext.current.applicationContext as? com.caipan.music.MuseApplication
    val config = app?.glassConfigStore?.state?.collectAsState()?.value ?: MuseGlassConfig()
    val effectiveShape = RoundedCornerShape((cornerRadius ?: config.cornerRadius.dp).coerceAtLeast(0.dp))
    val policy = LocalMuseBlurPolicy.current
    val locationEnabled = policy.enabledAt(location)
    val effectsBackdrop = backdrop?.takeIf { locationEnabled }
    // Liquid Glass keeps the source sharp by default. Text-heavy routes may request a small,
    // user-controlled separation blur without changing Original's already-strong frost.
    val liquidBlur = if (readabilityBoost) (2f + policy.liquidReadabilityBlur * 8f).dp else 2.dp
    val effectiveBlurRadius = if (liquidGlass) config.blurRadius.dp else blurRadius
    // Disabling Liquid effects must become an opaque Monet/Material surface. A translucent
    // fallback would still expose the previous route even though blur/refraction is disabled.
    val fallbackTint = when {
        liquidGlass && !locationEnabled && location == BlurLocation.FULL_SCREEN -> MaterialTheme.colorScheme.background
        liquidGlass && !locationEnabled && location == BlurLocation.PLAYER -> MaterialTheme.colorScheme.background
        liquidGlass && !locationEnabled && location == BlurLocation.SHEETS -> MaterialTheme.colorScheme.surfaceContainerHigh
        liquidGlass && !locationEnabled -> MaterialTheme.colorScheme.surfaceContainer
        liquidGlass -> Color.White.copy(alpha = 0.08f)
        else -> tint
    }
    val glass = if (effectsBackdrop != null) drawBackdrop(
        backdrop = effectsBackdrop,
        shape = { effectiveShape },
        effects = {
            blur(effectiveBlurRadius.toPx())
            vibrancy()
            if (liquidGlass) {
                lens(
                    refractionHeight = config.refractionHeight.dp.toPx(),
                    refractionAmount = (config.refractionAmount * 1.0f).dp.toPx(),
                    depthEffect = true,
                    chromaticAberration = config.chromaticAberration > 0.01f
                )
            }
        },
        highlight = if (liquidGlass) ({ Highlight.Default }) else null,
        shadow = if (liquidGlass) ({ Shadow.Default }) else null,
        innerShadow = if (liquidGlass) ({ InnerShadow(radius = 4.dp, alpha = 0.55f) }) else null,
        onDrawSurface = {
            drawRect(if (liquidGlass) Color.White.copy(alpha = 0.055f) else tint)
        }
    ) else background(fallbackTint, effectiveShape)
    return glass.then(
        if (borderColor.alpha > 0f) Modifier.border(1.dp, borderColor, effectiveShape) else Modifier
    )
}

@Composable
fun DialogBlurEffect(radius: Int = 28) {
    val view = LocalView.current
    val blurEnabled = LocalMuseBlurPolicy.current.enabledAt(BlurLocation.SHEETS)
    DisposableEffect(view, radius, blurEnabled) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val previousDimAmount = window?.attributes?.dimAmount
        window?.let {
            if (blurEnabled) {
                it.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                it.attributes = it.attributes.also { attributes ->
                    attributes.blurBehindRadius = radius
                    attributes.dimAmount = 0.18f
                }
            } else {
                it.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                it.attributes = it.attributes.also { attributes ->
                    attributes.blurBehindRadius = 0
                    attributes.dimAmount = 0.32f
                }
            }
        }
        onDispose {
            window?.let {
                it.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                if (previousDimAmount != null) {
                    it.attributes = it.attributes.also { attributes -> attributes.dimAmount = previousDimAmount }
                }
            }
        }
    }
}

@Composable
fun MuseGlassBox(modifier: Modifier, backdrop: Backdrop?, shape: Shape, tint: Color, content: @Composable () -> Unit) {
    Box(modifier.museGlass(backdrop, shape, tint), content = { content() })
}

@Composable
fun FullScreenGlassRoute(
    backdrop: Backdrop?,
    isLightTheme: Boolean,
    content: @Composable () -> Unit
) {
    val blocker = remember { MutableInteractionSource() }
    val tint = if (isLightTheme) {
        MaterialTheme.colorScheme.background.copy(alpha = MuseDesign.GlassAlphaLight)
    } else {
        MaterialTheme.colorScheme.background.copy(alpha = MuseDesign.GlassAlphaDark)
    }
    Box(
        Modifier.fillMaxSize()
            .museGlass(
                backdrop, RoundedCornerShape(0.dp), tint, 28.dp,
                location = BlurLocation.FULL_SCREEN,
                readabilityBoost = true,
                cornerRadius = 0.dp
            )
            .clickable(interactionSource = blocker, indication = null) {},
        contentAlignment = androidx.compose.ui.Alignment.TopStart
    ) { content() }
}
