package com.caipan.music.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caipan.music.plugin.BlurLocation
import com.kyant.backdrop.Backdrop
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

/** A tab rendered by [MuseLiquidBottomTabs]. */
data class MuseLiquidTab(val title: String, val icon: ImageVector)

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
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    val blurEnabled = LocalMuseBlurPolicy.current.enabledAt(BlurLocation.BOTTOM_TABS)
    val capsule = RoundedCornerShape(50)
    val containerColor = if (isLightTheme) {
        Color(0xFFFAFAFA).copy(alpha = 0.30f)
    } else {
        Color(0xFF121212).copy(alpha = 0.30f)
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val tabWidth = maxWidth / tabs.size
        val density = LocalDensity.current
        val tabWidthPx = with(density) { tabWidth.toPx() }
        val animationScope = rememberCoroutineScope()
        val position = remember { Animatable(selectedIndex.toFloat()) }
        var dragStretch by remember { mutableFloatStateOf(0f) }

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

        val containerGlass = if (backdrop != null && blurEnabled) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { capsule },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(24.dp.toPx(), 24.dp.toPx(), depthEffect = true, chromaticAberration = true)
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.16f)) },
                innerShadow = { InnerShadow(radius = 2.dp, alpha = 0.38f) },
                onDrawSurface = { drawRect(containerColor) }
            )
        } else if (!blurEnabled) {
            Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, capsule)
        } else {
            Modifier.background(containerColor, capsule)
        }

        Box(
            Modifier
                .fillMaxSize()
                .then(containerGlass)
                .clip(capsule)
        )

        val selectorGlass = if (backdrop != null && blurEnabled) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { capsule },
                effects = {
                    lens(
                        refractionHeight = 10.dp.toPx(),
                        refractionAmount = 14.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true
                    )
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.18f)) },
                innerShadow = { InnerShadow(radius = 8.dp, alpha = 0.72f) },
                onDrawSurface = {
                    drawRect(
                        if (isLightTheme) Color.Black.copy(alpha = 0.08f)
                        else Color.White.copy(alpha = 0.12f)
                    )
                }
            )
        } else if (!blurEnabled) {
            Modifier.background(accentColor.copy(alpha = .18f), capsule)
        } else {
            Modifier
        }

        Box(
            Modifier
                .offset(x = tabWidth * position.value)
                .padding(4.dp)
                .size(tabWidth - 8.dp, 56.dp)
                .graphicsLayer {
                    val stretch = abs(dragStretch).coerceIn(0f, 1f)
                    scaleX = 1f + stretch * 0.18f
                    scaleY = 1f - stretch * 0.07f
                }
                .then(selectorGlass)
                .clip(capsule)
        )

        Row(
            Modifier
                .fillMaxSize()
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
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val interactionSource = remember { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = when {
                        pressed -> 0.88f
                        selected -> 1.12f
                        else -> 1f
                    },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "liquidTabScale"
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(capsule)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onSelected(index) }
                        )
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = if (selected) accentColor else contentColor.copy(alpha = 0.62f),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = tab.title,
                        color = if (selected) accentColor else contentColor.copy(alpha = 0.62f),
                        fontSize = 11.sp,
                        lineHeight = 12.sp
                    )
                }
            }
        }
    }
}
