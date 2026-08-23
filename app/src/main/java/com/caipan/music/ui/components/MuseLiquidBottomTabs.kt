/*
 * Muse 液态玻璃底部标签栏。
 *
 * Ported from Kyant0/AndroidLiquidGlass (Apache-2.0) 的 catalog 示例
 * `LiquidBottomTabs.kt` 与 `LiquidBottomTab.kt`：
 * 沿用其 rememberLayerBackdrop() 二次录制方案（内容层 + 标签层各自成层）、
 * 选中指示器的弹簧参数与容器底色常量（浅色 #FAFAFA / 深色 #121212）。
 *
 * Upstream: https://github.com/Kyant0/AndroidLiquidGlass
 * License: Apache License 2.0 —— 见 licenses/APACHE-2.0.txt
 */
package com.caipan.music.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caipan.music.plugin.BlurLocation
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** A tab rendered by [MuseLiquidBottomTabs]. Prefer [painter] for custom bitmap icons. */
data class MuseLiquidTab(val title: String, val icon: ImageVector, val painter: androidx.compose.ui.graphics.painter.Painter? = null)

/**
 * Layered port of AndroidLiquidGlass' LiquidBottomTabs.
 *
 * The large capsule is the refractive container. A second, independently rendered
 * glass lens travels between tabs and adds chromatic aberration, highlight, shadow
 * and inner depth. This is intentionally not a Material NavigationBar skin.
 */
@Composable
fun MuseLiquidBottomTabs(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    tabs: List<MuseLiquidTab>,
    backdrop: Backdrop?,
    accentColor: Color,
    contentColor: Color,
    isLightTheme: Boolean,
    liquidGlass: Boolean = true,
    showLabels: Boolean = false,
    showContainer: Boolean = true,
    showSelector: Boolean = true,
    dockHeight: Dp = 64.dp,
    horizontalInset: Dp = 16.dp,
    verticalInset: Dp = 6.dp,
    selectorHeight: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    val blurEnabled = LocalMuseBlurPolicy.current.enabledAt(BlurLocation.BOTTOM_TABS)
    val appleStyle = LocalMuseAppleStyle.current
    // Apple/MeloX surfaces stay opaque even when a caller still supplies the
    // page backdrop (online mode does this for shared routing).
    val monet = LocalMuseMonet.current || appleStyle
    val capsule = RoundedCornerShape(50)
    // Liquid Glass still needs to sample the scene when Gaussian blur is
    // disabled. The policy controls the expensive blur pass; it must not turn
    // the material into an opaque color block.
    // Monet/Liquid mode still needs the backdrop recorder. Using an opaque
    // Material surface here turns the dock into a flat color and hides the
    // mini-player or cards underneath it.
    val sampleBackdrop = if (appleStyle) null else backdrop?.takeIf { blurEnabled || liquidGlass || monet }
    val surfaceBlur = if (liquidGlass && !blurEnabled) 0.dp else if (liquidGlass) 2.dp else 18.dp
    // The open-source LiquidBottomTabs keeps a second layer recorder for the
    // dock itself. The moving lens must sample both the page and the dock;
    // sampling only the page makes the bottom glass look like a flat tint.
    val tabsBackdrop = rememberLayerBackdrop()
    val containerColor = if (isLightTheme) {
        Color(0xFFFAFAFA).copy(alpha = 0.30f)
    } else {
        Color(0xFF121212).copy(alpha = 0.30f)
    }

    // Floating pill: the capsule is inset from the screen edges with its own depth
    // shadow, instead of a full-width docked bar with a frost sheet behind it.
    Box(modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = horizontalInset, vertical = verticalInset)
                .fillMaxWidth()
                .height(dockHeight),
            contentAlignment = Alignment.CenterStart
        ) {
        // The Row below has 4.dp horizontal content insets. Calculate the
        // actual tab width from the inset content area so the selector and
        // drag hit targets stay aligned with the icons.
        val tabWidth = (maxWidth - 8.dp) / tabs.size
        val density = LocalDensity.current
        val tabWidthPx = with(density) { tabWidth.toPx() }
        val animationScope = rememberCoroutineScope()
        val position = remember { Animatable(selectedIndex.toFloat()) }
        var dragStretch by remember { mutableFloatStateOf(0f) }
        val pressProgress = remember { Animatable(0f, 0.001f) }
        var pressedTabIndex by remember { mutableIntStateOf(-1) }

        LaunchedEffect(selectedIndex) {
            if (!position.isRunning || position.targetValue != selectedIndex.toFloat()) {
                position.animateTo(
                    selectedIndex.coerceIn(tabs.indices).toFloat(),
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
        }

        val containerGlass = if (sampleBackdrop != null) {
            Modifier.drawBackdrop(
                backdrop = sampleBackdrop,
                shape = { capsule },
                effects = {
                    vibrancy()
                    blur(surfaceBlur.toPx())
                    if (liquidGlass) {
                        lens(24.dp.toPx(), 24.dp.toPx(), depthEffect = true, chromaticAberration = true)
                    }
                },
                highlight = if (liquidGlass) ({ Highlight.Default }) else null,
                shadow = if (liquidGlass) ({ Shadow(radius = 18.dp, color = Color.Black.copy(alpha = 0.28f)) }) else null,
                innerShadow = if (liquidGlass) ({ InnerShadow(radius = 2.dp, alpha = 0.38f) }) else null,
                onDrawSurface = { drawRect(containerColor) }
            )
        } else if (monet) {
            Modifier
                .shadow(16.dp, capsule, ambientColor = Color.Black.copy(alpha = 0.26f), spotColor = Color.Black.copy(alpha = 0.26f))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f), capsule)
        } else if (!blurEnabled) {
            Modifier
                .shadow(16.dp, capsule, ambientColor = Color.Black.copy(alpha = 0.26f), spotColor = Color.Black.copy(alpha = 0.26f))
                // Blur policy disables the render effect, not the material's
                // translucency. An opaque tonal surface hides controls that
                // are intentionally laid out underneath the floating dock.
                .background(containerColor, capsule)
        } else {
            Modifier
                .shadow(16.dp, capsule, ambientColor = Color.Black.copy(alpha = 0.26f), spotColor = Color.Black.copy(alpha = 0.26f))
                .background(containerColor, capsule)
        }

        // The reference implementation records a second copy of the *same*
        // tab row. This matters: the moving selector samples the page and the
        // dock contents, instead of seeing an empty/flat backdrop.
        val visibleRowModifier = Modifier
            .fillMaxSize()
            .then(if (showContainer) containerGlass else Modifier)
            .pointerInput(tabs.size, tabWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { dragStretch = 0.25f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragStretch = (dragAmount / tabWidthPx).coerceIn(-1f, 1f)
                        animationScope.launch {
                            position.snapTo(
                                (position.value + dragAmount / tabWidthPx)
                                    .coerceIn(0f, tabs.lastIndex.toFloat())
                            )
                        }
                    },
                    onDragCancel = { dragStretch = 0f },
                    onDragEnd = {
                        dragStretch = 0f
                        val target = position.value.roundToInt().coerceIn(tabs.indices)
                        onSelected(target)
                        animationScope.launch {
                            position.animateTo(
                                target.toFloat(),
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    }
                )
            }
            .padding(4.dp)

        MuseLiquidTabRow(
            tabs = tabs,
            selectedIndex = selectedIndex,
            selectedIconColor = accentColor,
            contentColor = contentColor,
            capsule = capsule,
            interactive = true,
            onSelected = onSelected,
            showLabels = showLabels,
            onTabPress = { index, pressed ->
                pressedTabIndex = when {
                    pressed -> index
                    pressedTabIndex == index -> -1
                    else -> pressedTabIndex
                }
                animationScope.launch {
                    pressProgress.animateTo(
                        if (pressed) 1f else 0f,
                        spring(0.5f, 300f, 0.001f)
                    )
                }
            },
            modifier = visibleRowModifier
        )

        // Record the rendered dock as a separate backdrop. Keep this row
        // transparent on screen, but retain its pixels in tabsBackdrop.
        if (sampleBackdrop != null) {
            MuseLiquidTabRow(
                tabs = tabs,
                selectedIndex = -1,
                selectedIconColor = accentColor,
                contentColor = contentColor,
                capsule = capsule,
                interactive = false,
                onSelected = {},
                showLabels = showLabels,
                onTabPress = { _, _ -> },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(selectorHeight)
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .drawBackdrop(
                        backdrop = sampleBackdrop,
                        shape = { capsule },
                        effects = {
                            vibrancy()
                            blur(surfaceBlur.toPx())
                            if (liquidGlass) lens(24.dp.toPx(), 24.dp.toPx(), depthEffect = true)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .padding(horizontal = 4.dp)
            )
        }

        val selectorBackdrop = if (backdrop != null) {
            rememberCombinedBackdrop(backdrop, tabsBackdrop)
        } else {
            null
        }
        // A tap on an unselected tab should still light the tab being touched;
        // the reference implementation moves its lens during the gesture.
        val selectorIndex = pressedTabIndex.takeIf { it in tabs.indices }?.toFloat() ?: position.value
        val selectorPress = if (pressedTabIndex in tabs.indices) pressProgress.value else 0f
        val selectorGlass = if (!liquidGlass && monet) {
            Modifier.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f), capsule)
        } else if (!liquidGlass) {
            Modifier.background(accentColor, capsule)
        } else if (selectorBackdrop != null && sampleBackdrop != null) {
            Modifier.drawBackdrop(
                backdrop = selectorBackdrop,
                shape = { capsule },
                effects = {
                    lens(
                        refractionHeight = 10.dp.toPx(),
                        refractionAmount = 14.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true
                    )
                },
                highlight = { Highlight.Ambient.copy(alpha = selectorPress) },
                shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.18f + 0.12f * selectorPress)) },
                innerShadow = { InnerShadow(radius = 8.dp, alpha = 0.72f) },
                onDrawSurface = {
                    drawRect(
                        if (isLightTheme) Color.Black.copy(alpha = 0.08f)
                        else Color.White.copy(alpha = 0.12f)
                    )
                }
            )
        } else if (!blurEnabled || backdrop == null) {
            Modifier.background(accentColor.copy(alpha = .18f), capsule)
        } else {
            Modifier
        }

        val selectedIconColor = if (!showSelector) {
            contentColor
        } else if (liquidGlass) {
            accentColor
        } else if (accentColor.luminance() > 0.48f) {
            Color.Black.copy(alpha = 0.82f)
        } else {
            Color.White
        }

        if (showSelector) {
            Box(
                Modifier
                    .offset(x = tabWidth * selectorIndex)
                    .padding(4.dp)
                    .size(tabWidth - 8.dp, selectorHeight)
                    .graphicsLayer {
                        val stretch = abs(dragStretch).coerceIn(0f, 1f)
                        val press = selectorPress
                        scaleX = (1f + stretch * 0.18f) * (1f - press * 0.06f)
                        scaleY = (1f - stretch * 0.07f) * (1f - press * 0.06f)
                    }
                    .then(selectorGlass)
                    // InteractiveHighlight in AndroidLiquidGlass is a separate
                    // additive layer. Keep a visible fallback on devices where
                    // the backdrop highlight shader is subtle or unavailable.
                    .drawWithContent {
                        val p = selectorPress
                        // Draw the glass first. Additive feedback must be painted
                        // afterwards or the surface shader can cover it.
                        drawContent()
                        if (p > 0f) {
                            drawRect(Color.White.copy(alpha = 0.08f * p), blendMode = BlendMode.Plus)
                            drawRect(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.28f * p),
                                        Color.White.copy(alpha = 0.08f * p),
                                        Color.Transparent
                                    ),
                                    radius = size.minDimension * 1.35f
                                ),
                                blendMode = BlendMode.Plus
                            )
                        }
                    }
                    .clip(capsule)
            )
        }
        }
    }
}

/** Shared tab content used by the visible row and its transparent recorder. */
@Composable
private fun MuseLiquidTabRow(
    tabs: List<MuseLiquidTab>,
    selectedIndex: Int,
    selectedIconColor: Color,
    contentColor: Color,
    capsule: Shape,
    interactive: Boolean,
    onSelected: (Int) -> Unit,
    showLabels: Boolean,
    onTabPress: (Int, Boolean) -> Unit,
    modifier: Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            LaunchedEffect(pressed) {
                if (interactive) onTabPress(index, pressed)
            }
            val scale by animateFloatAsState(
                targetValue = when {
                    !interactive -> 1f
                    pressed -> 0.96f
                    selected -> if (showLabels) 1.04f else 1.12f
                    else -> 1f
                },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "liquidTabScale"
            )
            var tabModifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(capsule)
            if (interactive) {
                tabModifier = tabModifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { onSelected(index) }
                )
            }
            Column(
                tabModifier
                    .drawWithContent {
                        drawContent()
                        // Tab-level feedback remains visible even before the
                        // selector animation reaches the tapped tab.
                        if (interactive && pressed) {
                            drawRect(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.20f),
                                        Color.White.copy(alpha = 0.05f),
                                        Color.Transparent
                                    ),
                                    radius = size.minDimension * 1.2f
                                ),
                                blendMode = BlendMode.Plus
                            )
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                if (tab.painter != null) {
                    Icon(
                        painter = tab.painter,
                        contentDescription = tab.title,
                        tint = if (selected) selectedIconColor else contentColor.copy(alpha = 0.62f),
                        modifier = Modifier.size(if (showLabels) 22.dp else 28.dp)
                    )
                } else {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = if (selected) selectedIconColor else contentColor.copy(alpha = 0.62f),
                        modifier = Modifier.size(if (showLabels) 22.dp else 28.dp)
                    )
                }
                if (showLabels) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.title,
                        color = if (selected) selectedIconColor else contentColor.copy(alpha = 0.62f),
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Frosted sheet used behind the bottom bar. It spans the whole bar area including the
 * navigation inset and fades out downward, so the frosted region has a soft bottom
 * edge instead of a hard rectangle under the capsule.
 */
@Composable
fun MuseBottomFrostSheet(
    backdrop: Backdrop?,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    val blurEnabled = LocalMuseBlurPolicy.current.enabledAt(BlurLocation.BOTTOM_TABS)
    val monet = LocalMuseMonet.current
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val scrim = Brush.verticalGradient(
        0f to Color.Transparent,
        0.08f to containerColor,
        0.5f to containerColor,
        1f to Color.Transparent
    )
    val glass = if (monet) {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
    } else if (backdrop != null) {
        if (blurEnabled) Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(12.dp.toPx())
            },
            onDrawSurface = { drawRect(scrim) }
        ) else Modifier.background(scrim, shape)
    } else Modifier
    Box(modifier.then(glass).clip(shape))
}
