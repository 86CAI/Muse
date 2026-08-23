package com.caipan.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 液态玻璃按压高光。
 *
 * 移植自 Kyant0/AndroidLiquidGlass 的 `InteractiveHighlight`(Apache-2.0):
 * 按下时以手指为中心画一圈径向白色光斑(BlendMode.Plus 加亮),抬起后回弹消失;
 * 手指拖动时高光跟随移动,产生「光在玻璃里流动」的液态感。
 *
 * 上游用 RuntimeShader 画光斑,这里用 [Brush.radialGradient] 等价实现,避免
 * 依赖 backdrop 的内部 shader API,在任意 GPU 上表现一致。
 *
 * Upstream: https://github.com/Kyant0/AndroidLiquidGlass
 * License: Apache License 2.0 - see licenses/APACHE-2.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
internal class MuseLiquidHighlight(private val scope: CoroutineScope) {

    private val pressSpec = spring(0.5f, 300f, 0.001f)
    private val positionSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgress = Animatable(0f, 0.001f)
    private val position = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero

    /** 按压进度:0=未按,1=完全按下;按下/抬起由 spring 驱动,带轻微过冲(弹性)。 */
    val progress: Float get() = pressProgress.value
    /** 手指相对按下点的偏移,用于拖拽形变。 */
    val offset: Offset get() = position.value - startPosition

    /** 径向高光绘制层。加在玻璃背景之后、内容之前。 */
    val highlightModifier: Modifier = Modifier.drawWithContent {
        val p = pressProgress.value
        if (p > 0f) {
            val center = position.value
            val radius = size.minDimension * 1.5f
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f * p),
                        Color.White.copy(alpha = 0.06f * p),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                blendMode = BlendMode.Plus
            )
        }
        drawContent()
    }

    /** 手势层:按下即触发按压,抬起/取消释放,拖动时高光跟随。 */
    val gestureModifier: Modifier = Modifier.pointerInput(scope) {
        inspectLiquidDragGestures(
            onStart = { down ->
                startPosition = down.position
                scope.launch {
                    launch { pressProgress.animateTo(1f, pressSpec) }
                    launch { position.snapTo(startPosition) }
                }
            },
            onEnd = { release() },
            onCancel = { release() },
            onDrag = { change, _ -> scope.launch { position.snapTo(change.position) } }
        )
    }

    private fun release() {
        scope.launch {
            launch { pressProgress.animateTo(0f, pressSpec) }
            launch { position.animateTo(startPosition, positionSpec) }
        }
    }
}

/**
 * 主页玻璃卡片的按压反馈:按下轻微缩小并出现径向高光,抬起 spring 弹性回弹。
 *
 * 取代原来的 `.pressScale().clickable(...)` —— 它只有固定缩放、无高光、无回弹。
 * 用于 NowPlaying / AccessCard / BrowseCard 等 [Modifier.museGlass] 卡片。
 */
@Composable
fun Modifier.museLiquidCardPress(
    onClick: () -> Unit,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f
): Modifier {
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { MuseLiquidHighlight(scope) }
    return this
        .graphicsLayer {
            val s = lerp(1f, pressedScale, highlight.progress)
            scaleX = s
            scaleY = s
        }
        .clickable(interactionSource = null, indication = null, role = Role.Button, enabled = enabled, onClick = onClick)
        .then(highlight.highlightModifier)
        .then(if (enabled) highlight.gestureModifier else Modifier)
}
