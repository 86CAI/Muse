/*
 * Muse 玻璃材质封装。
 *
 * 建立在 Kyant0/AndroidLiquidGlass (Apache-2.0) 的 backdrop 库之上，
 * 只使用其公开 API（drawBackdrop / vibrancy / blur / lens / Highlight /
 * InnerShadow）；材质配方（模糊基底 + 带深度的透镜折射 + RGB 色散）参考了
 * 该项目 catalog 中的悬浮控件示例。
 *
 * Upstream: https://github.com/Kyant0/AndroidLiquidGlass
 * License: Apache License 2.0 - see licenses/APACHE-2.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
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
import androidx.compose.runtime.mutableStateOf
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
/** Apple/MeloX surface mode: keep shared sheets and controls opaque and flat. */
val LocalMuseAppleStyle = staticCompositionLocalOf { false }
/** `true` = 纯莫奈（Material You）：所有玻璃/模糊退化为不透明 tonal surface。 */
val LocalMuseMonet = staticCompositionLocalOf { false }
val LocalMuseBlurPolicy = staticCompositionLocalOf { BlurPolicy() }
val LocalMuseBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalMuseControlBlurLocation = staticCompositionLocalOf { BlurLocation.CARDS }
val LocalMusePerformancePolicy = staticCompositionLocalOf { com.caipan.music.plugin.PerformancePolicy() }
val LocalMuseScrolling = staticCompositionLocalOf { mutableStateOf(false) }

/**
 * Muse 的 Liquid Glass 材质。
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
    val themeColor = MaterialTheme.colorScheme.primary
    val effectiveShape = RoundedCornerShape((cornerRadius ?: config.cornerRadius.dp).coerceAtLeast(0.dp))
    if (LocalMuseAppleStyle.current) {
        val appleSurface = when (location) {
            BlurLocation.FULL_SCREEN, BlurLocation.PLAYER -> MaterialTheme.colorScheme.background
            BlurLocation.SHEETS, BlurLocation.BOTTOM_TABS -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        }
        return background(appleSurface, effectiveShape).then(
            if (borderColor.alpha > 0f) Modifier.border(1.dp, borderColor, effectiveShape) else Modifier
        )
    }
    // 纯莫奈：不采样背景、不模糊、不折射，直接退化为 Material 3 tonal surface。
    if (LocalMuseMonet.current) {
        val monetSurface = when (location) {
            BlurLocation.FULL_SCREEN, BlurLocation.PLAYER -> MaterialTheme.colorScheme.background
            BlurLocation.SHEETS, BlurLocation.BOTTOM_TABS -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        }
        return background(monetSurface, effectiveShape).then(
            if (borderColor.alpha > 0f) Modifier.border(1.dp, borderColor, effectiveShape) else Modifier
        )
    }
    val policy = LocalMuseBlurPolicy.current
    val locationEnabled = policy.enabledAt(location)
    val effectsBackdrop = backdrop?.takeIf { locationEnabled }
    // 全局模糊强度：所有玻璃（卡片/菜单/播放器/全屏）统一跟随 config.blurRadius，
    // 在"界面 → 模糊强度"滑杆调节后全部同步变化
    val readabilityFactor = if (readabilityBoost) policy.liquidReadabilityBlur else 1f
    val effectiveBlurRadius = (config.blurRadius * readabilityFactor).dp
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
    // 把绘制图按影响参数缓存：只有 config/形状/启用状态等真正变化时才重建 drawBackdrop。
    // 播放进度等无关刷新（如 250ms 的 uiState 更新）不会重建玻璃材质，避免无谓的 RenderEffect 重建。
    return remember(effectsBackdrop, effectiveShape, config, effectiveBlurRadius, liquidGlass, locationEnabled, fallbackTint, themeColor, borderColor) {
        val base = if (effectsBackdrop != null) drawBackdrop(
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
                if (liquidGlass) {
                    drawRect(Color.White.copy(alpha = 0.055f))
                    drawRect(themeColor.copy(alpha = config.themeColorIntensity * 0.28f))
                } else drawRect(tint)
            }
        ) else background(fallbackTint, effectiveShape)
        base.then(
            if (borderColor.alpha > 0f) Modifier.border(1.dp, borderColor, effectiveShape) else Modifier
        )
    }
}

@Composable
fun DialogBlurEffect(radius: Int? = null, location: BlurLocation = BlurLocation.SHEETS) {
    if (LocalMuseAppleStyle.current) return
    if (LocalMuseMonet.current) return  // 纯莫奈：弹窗不模糊
    val view = LocalView.current
    val blurEnabled = LocalMuseBlurPolicy.current.enabledAt(location)
    val app = LocalContext.current.applicationContext as? com.caipan.music.MuseApplication
    val config = app?.glassConfigStore?.state?.collectAsState()?.value
    val effectiveRadius = radius ?: config?.blurRadius?.toInt()?.coerceIn(1, 64) ?: 28
    DisposableEffect(view, effectiveRadius, blurEnabled) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val previousDimAmount = window?.attributes?.dimAmount
        window?.let {
            if (blurEnabled) {
                it.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                it.attributes = it.attributes.also { attributes ->
                    attributes.blurBehindRadius = effectiveRadius
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
