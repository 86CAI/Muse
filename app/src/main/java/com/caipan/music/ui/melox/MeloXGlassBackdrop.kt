/*
 * Glass backdrop plumbing
 *
 * Ported from NEORUAA/Mei_MeloX_Android
 * (app/src/main/java/com/ljyh/mei/ui/glass/GlassBackdrop.kt).
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop> {
    error("Glass controls must be hosted by GlassBackdropHost or GlassBackdropProvider")
}

@Composable
fun GlassBackdropProvider(
    backdrop: Backdrop,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = content)
}

/**
 * Keeps sampled content and glass overlays in separate layers. Glass controls must never be
 * placed inside [sampledContent], otherwise the backdrop can recursively sample itself.
 */
@Composable
fun GlassBackdropHost(
    modifier: Modifier = Modifier,
    sampledContent: @Composable BoxScope.(LayerBackdrop) -> Unit,
    overlayContent: @Composable BoxScope.(LayerBackdrop) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(modifier) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
            ) {
                sampledContent(backdrop)
            }
            overlayContent(backdrop)
        }
    }
}

/** Attach next to a [layerBackdrop] recording so other windows can locate the source. */
fun Modifier.trackBackdropPosition(backdrop: LayerBackdrop): Modifier =
    onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            backdropSourcePositions[backdrop] = coordinates
        }
    }

/** Window-space positions of backdrop source layers, for cross-window sampling. */
internal val backdropSourcePositions =
    java.util.WeakHashMap<LayerBackdrop, androidx.compose.ui.layout.LayoutCoordinates>()
