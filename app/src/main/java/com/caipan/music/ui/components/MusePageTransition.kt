package com.caipan.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.caipan.music.ui.theme.MuseMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Muse 页面转场与动效统一封装。
 *
 * 收编 MainScreen.kt 中复制粘贴的转场表达式:
 * - 全屏 overlay(均衡器/插件/关于/个人/UI 设置/皮肤)的 scale+fade —— 6 处同式;
 * - 矩形展开 reveal(本地歌曲/歌单列表/歌单详情/WebDAV)的 GenericShape —— 4 处同式;
 * - 列表/卡片的按压反馈(pressScale)、加载骨架屏(shimmer/SkeletonSongRows)、
 *   列表交错入场(staggeredEnter)。
 *
 * 时长走 [MuseMotion] 常量;进入快、退出更快(Emil「asymmetric enter/exit」)。
 */

/** 全屏 overlay 统一入场:scale .95 + fade(起点 0.95 而非 0.92,更接近真实物体出现)。 */
fun fullScreenOverlayEnter() = scaleIn(
    initialScale = .95f,
    animationSpec = tween(MuseMotion.EnterFull, easing = FastOutSlowInEasing)
) + fadeIn(tween(MuseMotion.EnterFull, easing = FastOutSlowInEasing))

/** 全屏 overlay 统一退场:与入场同形(scale .95 + fade),但时长更短。 */
fun fullScreenOverlayExit() = scaleOut(
    targetScale = .95f,
    animationSpec = tween(MuseMotion.ExitFull, easing = FastOutSlowInEasing)
) + fadeOut(tween(MuseMotion.ExitFull, easing = FastOutSlowInEasing))

/**
 * 矩形展开 reveal(本地歌曲/歌单列表/歌单详情/WebDAV 共用):
 * alpha 随 progress 渐变,圆角矩形从 origin bounds 扩到全屏、圆角收至 0。
 *
 * progress 以 [State] 传入,在 draw 阶段读取——动画期间只触发重绘(redraw),
 * 不触发重组,与原 MainScreen 实现语义一致。
 *
 * @param progress 转场进度 Animatable 的 derivedStateOf(0..1)
 * @param origin 起始矩形 bounds(触发入口的位置);null 时从屏幕中心展开
 * @param cornerRadiusPx 起始圆角(px),随 progress 收至 0
 */
fun Modifier.rectReveal(progress: State<Float>, origin: Rect?, cornerRadiusPx: Float): Modifier =
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
    }.clip(GenericShape { size, _ ->
        val p = progress.value.coerceIn(0f, 1f)
        val startTop = origin?.top?.coerceIn(0f, size.height) ?: size.height / 2f
        val startBottom = origin?.bottom?.coerceIn(0f, size.height) ?: size.height / 2f
        val startLeft = origin?.left?.coerceIn(0f, size.width) ?: 0f
        val startRight = origin?.right?.coerceIn(0f, size.width) ?: size.width
        addRoundRect(
            RoundRect(
                startLeft * (1f - p),
                startTop * (1f - p),
                startRight + (size.width - startRight) * p,
                startBottom + (size.height - startBottom) * p,
                CornerRadius(cornerRadiusPx * (1f - p))
            )
        )
    })

/**
 * 统一按压反馈:按下 100ms 缩到 0.97,抬起 spring 回弹(damping 1.0,不过冲)。
 *
 * 用于列表项/卡片等 clickable 元素(液态按钮已自带 LiquidPress,不要叠加)。
 * 手势层(pointerInput)只翻转 pressed 状态,动画由 LaunchedEffect 驱动——
 * AwaitPointerEventScope 是 @RestrictsSuspension 受限挂起上下文,不能直接调 Animatable;
 * 动画可打断:快速连按时从当前值平滑反向。
 */
fun Modifier.pressScale(active: Boolean = true): Modifier = composed {
    if (!active) return@composed this
    val pressed = remember { mutableStateOf(false) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed.value) {
        if (pressed.value) {
            scale.animateTo(0.97f, tween(100, easing = FastOutSlowInEasing))
        } else {
            // 有回弹的 spring:抬起后轻微过冲再落回,取代原来 dampingRatio=1 的死板缩放
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            pressed.value = true
            waitForUpOrCancellation()
            pressed.value = false
        }
    }.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * shimmer 扫光:无限循环横向渐变扫过,用于 >400ms 的加载场景
 * (本地扫描/在线搜索/WebDAV 目录),替代纯转圈。
 * Emil「感知性能」:骨架屏让加载感觉比转圈更快。
 */
@Composable
fun Modifier.shimmer(active: Boolean = true): Modifier {
    if (!active) return this
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val light = Color.White.copy(alpha = .28f)
    return background(
        Brush.linearGradient(
            colors = listOf(base, light, base),
            start = Offset(x * 640f - 320f, 0f),
            end = Offset(x * 640f + 320f, 0f)
        )
    )
}

/** 歌曲行骨架(封面方块 + 两行文字条),用于加载中占位。 */
@Composable
fun SkeletonSongRows(count: Int = 8) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        repeat(count) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).shimmer()
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.7f).height(16.dp).clip(RoundedCornerShape(8.dp)).shimmer())
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.45f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                }
            }
        }
    }
}

/**
 * 列表交错入场:子项按 index 依次淡入上移(30-80ms 间隔,封顶 [capDelay])。
 * [animatedKeys] 由调用方持有,跨重组/滚动回收记忆已播放的 key,避免重复播放。
 * 入场期间不阻塞交互(stagger 纯装饰)。
 */
@Composable
fun Modifier.staggeredEnter(
    index: Int,
    key: String,
    animatedKeys: MutableSet<String>,
    baseDelay: Int = 40,
    capDelay: Int = 400
): Modifier {
    val alreadyDone = key in animatedKeys
    val progress = remember(key) { Animatable(if (alreadyDone) 1f else 0f) }
    LaunchedEffect(key) {
        if (!alreadyDone) {
            animatedKeys.add(key)
            delay((index * baseDelay).toLong().coerceAtMost(capDelay.toLong()))
            progress.animateTo(
                1f,
                spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
            )
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}
