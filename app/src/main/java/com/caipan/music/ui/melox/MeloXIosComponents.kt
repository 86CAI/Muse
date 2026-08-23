/*
 * iOS 27 style components (toolbar / segmented control / grouped list / card)
 *
 * Ported from NEORUAA/Mei_MeloX_Android
 * (ui/glass/Ios27Components.kt, ui/glass/GlassControls.kt).
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
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
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule

enum class IosTopBarStyle { Default, CompactLargeTitle, LargeTitle, TwoLine, TwoLineLeading }
private val IosTopToolbarActionGap = 8.dp

/** The five top-toolbar variants from Figma node 5661:41970. */
@Composable
fun IosTopToolbar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    style: IosTopBarStyle = IosTopBarStyle.Default,
    collapseProgress: Float = 1f,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val progress = collapseProgress.coerceIn(0f, 1f)
    CompositionLocalProvider(
        LocalGlassSurfaceBrightness provides 1f,
        LocalGlassSurfaceStyle provides GlassSurfaceStyle.Navigation,
        LocalContentColor provides LocalGlassColors.current.content,
    ) {
        when (style) {
            IosTopBarStyle.LargeTitle -> Column(modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { navigation?.invoke() }
                    Row(horizontalArrangement = Arrangement.spacedBy(IosTopToolbarActionGap), content = actions)
                }
                Column(Modifier.offset(y = (-10).dp)) {
                    Text(title, style = IosTypography.largeTitle, color = LocalGlassColors.current.content)
                    subtitle?.let { Text(it, style = IosTypography.subheadline, color = LocalGlassColors.current.secondaryContent) }
                }
            }
            IosTopBarStyle.CompactLargeTitle -> Row(
                modifier.fillMaxWidth().height(54.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = IosTypography.largeTitle,
                    color = LocalGlassColors.current.content,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(IosTopToolbarActionGap), content = actions)
            }
            else -> {
                var leadingWidth by remember { mutableIntStateOf(0) }
                var trailingWidth by remember { mutableIntStateOf(0) }
                val density = LocalDensity.current
                val leadingPad = with(density) { leadingWidth.toDp() }
                val trailingPad = with(density) { trailingWidth.toDp() }
                val centeredTitleSidePadding = maxOf(leadingPad, trailingPad) +
                    if (leadingWidth > 0 || trailingWidth > 0) IosTopToolbarActionGap else 0.dp
                Box(modifier.fillMaxWidth().height(54.dp).padding(horizontal = 16.dp)) {
                    Row(
                        Modifier.align(Alignment.CenterStart).onSizeChanged { leadingWidth = it.width },
                        verticalAlignment = Alignment.CenterVertically,
                    ) { navigation?.invoke() }
                    Column(
                        Modifier
                            .align(if (style == IosTopBarStyle.TwoLineLeading) Alignment.CenterStart else Alignment.Center)
                            .fillMaxWidth()
                            .padding(
                                start = if (style == IosTopBarStyle.TwoLineLeading) leadingPad
                                else centeredTitleSidePadding,
                                end = if (style == IosTopBarStyle.TwoLineLeading) trailingPad
                                else centeredTitleSidePadding,
                            )
                            .graphicsLayer {
                                alpha = progress
                                val scale = 0.92f + 0.08f * progress
                                scaleX = scale
                                scaleY = scale
                            }
                            .blur(8.dp * (1f - progress)),
                        horizontalAlignment = if (style == IosTopBarStyle.TwoLineLeading) Alignment.Start else Alignment.CenterHorizontally,
                    ) {
                        Text(
                            title,
                            style = if (subtitle == null) IosTypography.headline
                            else IosTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                            color = LocalGlassColors.current.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        subtitle?.let {
                            Text(
                                it,
                                style = IosTypography.caption,
                                color = LocalGlassColors.current.secondaryContent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        Modifier.align(Alignment.CenterEnd).onSizeChanged { trailingWidth = it.width },
                        horizontalArrangement = Arrangement.spacedBy(IosTopToolbarActionGap),
                        content = actions,
                    )
                }
            }
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") backdrop: Backdrop = LocalGlassBackdrop.current,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalGlassColors.current
    val merged = LocalMergedGlassCards.current
    Box(
        modifier = modifier
            .then(
                if (merged) {
                    Modifier.drawBehind {
                        drawLine(
                            colors.separator,
                            Offset(16.dp.toPx(), 0f),
                            Offset(size.width - 16.dp.toPx(), 0f),
                            1.dp.toPx(),
                        )
                    }
                } else {
                    Modifier
                        .clip(ContinuousRoundedRectangle(LocalGlassDimensions.current.regularCornerRadius))
                        .background(colors.elevatedBackground)
                },
            )
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ) else Modifier,
            ),
        content = {
            CompositionLocalProvider(
                LocalContentColor provides colors.content,
                LocalGlassContentColor provides colors.content,
            ) {
                content()
            }
        },
    )
}

@Composable
fun <T> GlassSegmentedControl(
    items: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
) {
    require(items.isNotEmpty())
    val colors = LocalGlassColors.current
    val isLight = !colors.isDark
    val selectedIndex = items.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val currentItems by rememberUpdatedState(items)
    val currentOnSelected by rememberUpdatedState(onSelected)
    val scope = rememberCoroutineScope()
    val tabsBackdrop = rememberLayerBackdrop()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val trackColor = if (isLight) Color(0x1F767680) else Color.White.copy(alpha = 0.12f)

    // Same three-layer structure as LiquidBottomTabs:
    // visible tabs -> invisible exported tabs with labels -> movable combined-backdrop lens.
    BoxWithConstraints(modifier.height(32.dp)) {
        val density = LocalDensity.current
        val contentWidthPx = (constraints.maxWidth - with(density) { 4.dp.roundToPx() })
            .coerceAtLeast(items.size)
            .toFloat()
        val tabWidthPx = contentWidthPx / items.size
        val trackPaddingPx = with(density) { 2.dp.toPx() }
        val animation = remember(scope, items.size) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..items.lastIndex.coerceAtLeast(1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 36f / 28f,
                onDragStarted = {},
                onDragStopped = {
                    val target = targetValue.fastRoundToInt().fastCoerceIn(0, currentItems.lastIndex)
                    currentOnSelected(currentItems[target].first)
                    animateToValue(target.toFloat())
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, currentItems.lastIndex.toFloat()),
                    )
                },
            )
        }
        androidx.compose.runtime.LaunchedEffect(selectedIndex) { animation.animateToValue(selectedIndex.toFloat()) }
        val interactiveHighlight = remember(scope, isLtr) {
            InteractiveHighlight(
                animationScope = scope,
                position = { size, _ ->
                    Offset(
                        x = if (isLtr) {
                            trackPaddingPx + (animation.value + 0.5f) * tabWidthPx
                        } else {
                            size.width - trackPaddingPx - (animation.value + 0.5f) * tabWidthPx
                        },
                        y = size.height / 2f,
                    )
                },
            )
        }

        // 1. Visible gray track and labels. The exported duplicate mirrors its tap targets.
        Row(
            Modifier
                .fillMaxSize()
                .clip(Capsule())
                .background(trackColor)
                .then(interactiveHighlight.modifier)
                .padding(2.dp),
        ) {
            SegmentedTabContent(
                items = items,
                selected = selected,
                onSelected = onSelected,
                selectedBackground = Color.Transparent,
            )
        }

        // 2. Exact duplicate exported as a hidden sampling source. It includes the labels,
        // so the lens carries and refracts the selected tab content just like the nav bar.
        Row(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics { }
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .clip(Capsule())
                .background(trackColor)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        val press = animation.pressProgress
                        vibrancy()
                        blur(2.dp.toPx())
                        lens(
                            8.dp.toPx() * press,
                            16.dp.toPx() * press,
                            depthEffect = press > 0.01f,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = animation.pressProgress) },
                    onDrawSurface = { drawRect(trackColor) },
                )
                .then(interactiveHighlight.modifier)
                .padding(2.dp),
        ) {
            SegmentedTabContent(
                items = items,
                selected = selected,
                onSelected = onSelected,
                selectedBackground = if (isLight) Color.White else Color(0xFF636366),
            )
        }

        // 3. The only drag surface. It sits over the selected cell, samples layer 2 and
        // becomes larger only while pressed; unselected cells remain tappable through layer 2.
        Box(
            Modifier
                .graphicsLayer {
                    val visualIndex = if (isLtr) animation.value else items.lastIndex - animation.value
                    translationX = 2.dp.toPx() + visualIndex * tabWidthPx
                    translationY = 2.dp.toPx()
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                }
                .then(interactiveHighlight.gestureModifier)
                .then(animation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val press = animation.pressProgress
                        lens(
                            7.dp.toPx() * press,
                            18.dp.toPx() * press,
                            depthEffect = press > 0.01f,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = animation.pressProgress) },
                    shadow = { Shadow(alpha = 0.72f * animation.pressProgress) },
                    innerShadow = {
                        InnerShadow(
                            radius = 6.dp * animation.pressProgress,
                            alpha = 0.72f * animation.pressProgress,
                        )
                    },
                    layerBlock = {
                        scaleX = animation.scaleX
                        scaleY = animation.scaleY
                        val velocity = animation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.18f, 0.18f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.14f, 0.14f)
                    },
                    onDrawSurface = {
                        val press = animation.pressProgress
                        // The selected white pill and its label are already exported by
                        // tabsBackdrop. A resting white overlay here would cover that sampled
                        // label instead of refracting it, so only add a subtle pressed sheen.
                        drawRect(Color.White.copy(alpha = 0.10f * press))
                    },
                )
                .height(28.dp)
                .layout { measurable, constraints ->
                    val width = tabWidthPx.fastRoundToInt()
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = width, maxWidth = width),
                    )
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
        )
    }
}

@Composable
private fun <T> androidx.compose.foundation.layout.RowScope.SegmentedTabContent(
    items: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    selectedBackground: Color,
) {
    val colors = LocalGlassColors.current
    items.forEach { (key, label) ->
        val isSelected = key == selected
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(Capsule())
                .background(if (isSelected) selectedBackground else Color.Transparent)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Tab,
                    onClick = { onSelected(key) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = IosTypography.caption,
                color = colors.content,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun IosGroupedList(
    modifier: Modifier = Modifier,
    framed: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalGlassColors.current
    val background = colors.elevatedBackground.copy(
        alpha = LocalGroupedListBackgroundAlpha.current.coerceIn(0f, 1f),
    )
    val shape = ContinuousRoundedRectangle(26.dp)
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (framed) {
                    Modifier
                        .clip(shape)
                        .background(background, shape)
                        .drawWithContent {
                            drawContent()
                            // Hide only the first merged row's inset separator. Clipping the
                            // container prevents the cover leaking across the top corners.
                            val inset = 16.dp.toPx()
                            drawRect(
                                color = background,
                                topLeft = Offset(inset, 0f),
                                size = Size((size.width - inset * 2f).coerceAtLeast(0f), 1.dp.toPx()),
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        CompositionLocalProvider(
            LocalMergedGlassCards provides true,
            LocalGroupedListIconColor provides colors.accent,
            LocalContentColor provides colors.content,
        ) { content() }
    }
}

@Composable
fun IosListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    detail: String? = null,
    systemName: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showTopSeparator: Boolean = true,
) {
    val colors = LocalGlassColors.current
    Row(
        modifier
            .fillMaxWidth()
            .height(if (subtitle == null) 52.dp else 62.dp)
            .drawBehind {
                if (showTopSeparator) {
                    drawLine(
                        colors.separator,
                        Offset(16.dp.toPx(), 0f),
                        Offset(size.width - 16.dp.toPx(), 0f),
                        1.dp.toPx(),
                    )
                }
            }
            .padding(horizontal = 16.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { it(); Spacer(Modifier.width(12.dp)) }
        systemName?.let {
            SfSymbol.fromSystemName(it)?.let { symbol ->
                SfIcon(symbol, null, size = 23.dp, tint = colors.accent)
                Spacer(Modifier.width(12.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = IosTypography.body,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    it,
                    style = IosTypography.subheadline,
                    color = colors.secondaryContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        detail?.let { Text(it, style = IosTypography.subheadline, color = colors.secondaryContent) }
        trailing?.invoke()
        if (onClick != null && trailing == null) {
            Spacer(Modifier.width(8.dp))
            SfIcon(SfSymbol.ChevronForward, null, size = 12.dp, tint = colors.separator)
        }
    }
}
