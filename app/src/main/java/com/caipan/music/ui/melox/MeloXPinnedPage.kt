/*
 * Fixed iOS navigation bar over a progressively blurred scroll source.
 *
 * Ported from NEORUAA/Mei_MeloX_Android
 * (app/src/main/java/com/ljyh/mei/ui/glass/IosPinnedListPage.kt).
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun rememberIosListCollapseProgress(
    listState: LazyListState,
    collapseDistance: Dp = 56.dp,
): Float {
    val collapseDistancePx = with(LocalDensity.current) { collapseDistance.toPx() }
    val progress by remember(listState, collapseDistancePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
            }
        }
    }
    return progress
}

@Composable
fun IosPinnedListPage(
    title: String,
    bottomPadding: Dp,
    horizontalContentPadding: Dp = 16.dp,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    listState: LazyListState = rememberLazyListState(),
    showsLargeTitle: Boolean = true,
    largeTitleHorizontalPadding: Dp = 4.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    backgroundColor: Color? = null,
    content: LazyListScope.() -> Unit,
) {
    val collapseProgress = rememberIosListCollapseProgress(listState)
    IosPinnedPage(
        title = title,
        subtitle = subtitle,
        bottomPadding = bottomPadding,
        modifier = modifier,
        onNavigateBack = onNavigateBack,
        actions = actions,
        collapseProgress = collapseProgress,
        backgroundColor = backgroundColor,
    ) { contentPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalContentPadding,
                top = contentPadding.calculateTopPadding(),
                end = horizontalContentPadding,
                bottom = contentPadding.calculateBottomPadding(),
            ),
            verticalArrangement = verticalArrangement,
        ) {
            if (showsLargeTitle) {
                item(key = "ios-large-title:$title") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = 1f - collapseProgress
                                val scale = 1f - 0.04f * collapseProgress
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            }
                            .blur(6.dp * collapseProgress)
                            .offset(y = (-10).dp)
                            .padding(horizontal = largeTitleHorizontalPadding, vertical = 6.dp),
                    ) {
                        Text(
                            text = title,
                            style = IosTypography.largeTitle,
                            fontWeight = FontWeight.Bold,
                            color = LocalGlassColors.current.content,
                        )
                        subtitle?.let {
                            Text(
                                text = it,
                                style = IosTypography.subheadline,
                                color = LocalGlassColors.current.secondaryContent,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                }
            }
            content()
        }
    }
}

/**
 * Fixed iOS navigation bar for pages whose body is not a single [LazyColumn].
 *
 * [content] is the only exported sample layer. All glass controls inside it continue to read
 * [LocalGlassBackdrop], while the toolbar reads this dedicated page layer, preventing feedback.
 */
@Composable
fun IosPinnedPage(
    title: String,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    collapseProgress: Float = 1f,
    backgroundColor: Color? = null,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val pageBackdrop = rememberLayerBackdrop()
    val parentBackdrop = LocalGlassBackdrop.current
    val topBarBackdrop = rememberCombinedBackdrop(parentBackdrop, pageBackdrop)
    val colors = LocalGlassColors.current
    val pageBackground = backgroundColor ?: colors.groupedBackground
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val toolbarHeight = statusBarHeight + 62.dp
    val contentPadding = PaddingValues(
        start = 16.dp,
        top = toolbarHeight + 10.dp,
        end = 16.dp,
        bottom = bottomPadding + 24.dp,
    )

    CompositionLocalProvider(LocalContentColor provides colors.content) {
        Box(modifier.fillMaxSize().background(pageBackground)) {
            Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
                content(contentPadding)
            }
            // Do not draw a separate opaque sample layer here. On this app's
            // backdrop runtime it visually splits the title area from the page.
            // The toolbar stays transparent and the page background remains continuous.
            CompositionLocalProvider(LocalGlassBackdrop provides topBarBackdrop) {
                IosTopToolbar(
                    // Alpha03 cannot reproduce the upstream blurred material
                    // toolbar. Keeping only its compact title reads as a detached
                    // floating word after scrolling, so pages retain the large
                    // in-content title and leave this transparent chrome text-free.
                    title = "",
                    subtitle = null,
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().align(Alignment.TopCenter),
                    collapseProgress = collapseProgress,
                    navigation = onNavigateBack?.let { navigateBack ->
                        {
                            GlassIconButton(onClick = navigateBack) {
                                SfIcon(
                                    SfSymbol.ChevronBack,
                                    "返回",
                                    mirrored = true,
                                )
                            }
                        }
                    },
                    actions = actions,
                )
            }
        }
    }
}
