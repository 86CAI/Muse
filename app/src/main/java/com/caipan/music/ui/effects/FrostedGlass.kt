package com.caipan.music.ui.effects

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize

// ============================================================================
// Sky — 共享状态桥梁，连接背景截图器和前景模糊遮罩
// Sky - shared state bridge between background capturer and blur overlay
// ============================================================================

/**
 * Shared state between [Modifier.sky] (background capture) and [Modifier.frosted] (blur overlay).
 * Create one instance and pass to both modifiers.
 *
 * Usage:
 * ```
 * val sky = remember { Sky() }
 * Box(Modifier.sky(sky)) {
 *     // background content (captured every frame)
 *     Box(Modifier.frosted(sky, radius = 20, tint = Color.White.copy(alpha = 0.15f))) {
 *         // foreground content on frosted glass
 *     }
 * }
 * ```
 */
class Sky {
    internal var backgroundLayer: GraphicsLayer? = null
    internal var isCapturing: Boolean = false
    internal var sourceBounds: Rect = Rect.Zero
}

// ============================================================================
// .sky() modifier — 每帧截取背后内容到 GraphicsLayer
// ============================================================================

/**
 * Place on the background container to capture its content into a [GraphicsLayer]
 * every frame. The captured layer is stored in [Sky.backgroundLayer] and
 * read by descendant [frosted] modifiers.
 *
 * Must be a parent (ancestor) of any [frosted] modifier that reads the same [Sky].
 */
@Composable
fun Modifier.sky(sky: Sky): Modifier = this.then(SkyModifierElement(sky))

private data class SkyModifierElement(val sky: Sky) : ModifierNodeElement<SkyModifierNode>() {
    override fun InspectorInfo.inspectableProperties() {
        name = "sky"
    }

    override fun create(): SkyModifierNode = SkyModifierNode(sky)
    override fun update(node: SkyModifierNode) { node.sky = sky }
}

private class SkyModifierNode(var sky: Sky) :
    Modifier.Node(),
    DrawModifierNode,
    GlobalPositionAwareModifierNode {

    private var graphicsLayer: GraphicsLayer? = null
    private var positionInRoot: Offset = Offset.Zero

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        positionInRoot = coordinates.positionInRoot()
        sky.sourceBounds = Rect(positionInRoot, coordinates.size.toSize())
    }

    /**
     * Two-phase rendering to prevent cyclic RenderNode graph:
     * 1. Capture pass: record background into layer (frosted overlay skips itself)
     * 2. On-screen pass: draw normally (frosted overlay now paints its blur)
     */
    override fun ContentDrawScope.draw() {
        val context = requireGraphicsContext()
        val layer = graphicsLayer ?: context.createGraphicsLayer().also { graphicsLayer = it }

        // Phase 1: Capture — overlay returns early (checks isCapturing)
        sky.isCapturing = true
        try {
            layer.record { this@draw.drawContent() }
        } finally {
            sky.isCapturing = false
        }

        sky.backgroundLayer = layer

        // Phase 2: On-screen — overlay now draws blurred backdrop
        drawContent()
    }

    override fun onDetach() {
        graphicsLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        graphicsLayer = null
        sky.backgroundLayer = null
    }
}

// ============================================================================
// .frosted() modifier — 实时 GPU 模糊 + 半透明着色叠加
// ============================================================================

/**
 * Renders a real-time frosted glass overlay by GPU-blurring the background
 * captured by a parent [sky] modifier.
 *
 * Uses RenderEffect (API 31+) for synchronous GPU-accelerated blur — no async
 * processing, no frame delay. Works at 60fps for dynamic backgrounds (video,
 * animation, scrolling content).
 *
 * @param sky  Shared state from a parent [sky] modifier
 * @param radius Blur radius in pixels (1-25). Typical: 15-25 for strong glass.
 * @param tint  Semi-transparent color overlay. White.copy(alpha=0.10~0.20) for
 *              light glass, Black.copy(alpha=0.20~0.40) for dark glass.
 */
@Composable
fun Modifier.frosted(
    sky: Sky,
    radius: Int = 20,
    tint: Color = Color.White.copy(alpha = 0.15f)
): Modifier {
    require(radius in 0..25) { "Blur radius must be 0-25, was $radius" }
    return this.then(FrostedModifierElement(sky, radius, tint))
}

private data class FrostedModifierElement(
    val sky: Sky,
    val radius: Int,
    val tint: Color
) : ModifierNodeElement<FrostedModifierNode>() {

    override fun InspectorInfo.inspectableProperties() {
        name = "frosted"
        properties["radius"] = radius
        properties["tint"] = tint
    }

    override fun create(): FrostedModifierNode =
        FrostedModifierNode(sky, radius, tint)

    override fun update(node: FrostedModifierNode) {
        node.update(sky, radius, tint)
    }
}

private class FrostedModifierNode(
    private var sky: Sky,
    private var radius: Int,
    private var tint: Color
) : Modifier.Node(),
    DrawModifierNode,
    GlobalPositionAwareModifierNode,
    LayoutAwareModifierNode {

    private var positionInRoot: Offset = Offset.Zero
    private var size: IntSize = IntSize.Zero

    fun update(sky: Sky, radius: Int, tint: Color) {
        val needsRedraw = this.sky !== sky || this.radius != radius || this.tint != tint
        this.sky = sky
        this.radius = radius
        this.tint = tint
        if (needsRedraw && isAttached) invalidateDraw()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        positionInRoot = coordinates.positionInRoot()
    }

    override fun onRemeasured(size: IntSize) {
        this.size = size
    }

    override fun ContentDrawScope.draw() {
        // CRITICAL: Do nothing during capture phase.
        // If this composable drew itself into the capture layer, it would sample
        // that same layer for blur — a cyclic RenderNode graph that crashes the
        // render thread. See: https://github.com/skydoves/Cloudy/issues/112
        if (sky.isCapturing) return

        val backgroundLayer = sky.backgroundLayer
        if (backgroundLayer == null) {
            drawContent()
            return
        }

        if (radius <= 0) {
            // No blur — draw background region directly (for tint-only glass)
            drawBackgroundRegion(backgroundLayer)
            return
        }

        val skyBounds = sky.sourceBounds
        val offsetX = positionInRoot.x - skyBounds.left
        val offsetY = positionInRoot.y - skyBounds.top

        // GPU blur via RenderEffect — synchronous, no frame delay
        val blurEffect = RenderEffect
            .createBlurEffect(radius.toFloat(), radius.toFloat(), Shader.TileMode.CLAMP)
            .asComposeRenderEffect()

        val context = requireGraphicsContext()
        val blurLayer = context.createGraphicsLayer()

        try {
            // Record background region into a temporary layer
            blurLayer.record {
                drawContext.canvas.save()
                drawContext.canvas.translate(-offsetX, -offsetY)
                drawLayer(backgroundLayer)
                drawContext.canvas.restore()
            }

            // Attach blur effect to the temporary layer
            blurLayer.renderEffect = blurEffect

            // Draw blurred backdrop + tint within clip bounds
            clipRect {
                drawLayer(blurLayer)

                // Semi-transparent tint — this is what gives the "glass" feel
                if (tint != Color.Transparent) {
                    drawRect(color = tint, blendMode = BlendMode.SrcOver)
                }
            }
            drawContent() // draw foreground (text, icons, etc.) on top
        } finally {
            context.releaseGraphicsLayer(blurLayer)
        }
    }

    private fun ContentDrawScope.drawBackgroundRegion(layer: GraphicsLayer) {
        val skyBounds = sky.sourceBounds
        val offsetX = positionInRoot.x - skyBounds.left
        val offsetY = positionInRoot.y - skyBounds.top

        drawContext.canvas.save()
        drawContext.canvas.translate(-offsetX, -offsetY)
        drawLayer(layer)
        drawContext.canvas.restore()

        if (tint != Color.Transparent) {
            drawRect(color = tint, blendMode = BlendMode.SrcOver)
        }
        drawContent()
    }
}