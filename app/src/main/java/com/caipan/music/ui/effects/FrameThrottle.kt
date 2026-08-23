package com.caipan.music.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.caipan.music.plugin.FrameRateScene
import com.caipan.music.ui.components.LocalMusePerformancePolicy

/**
 * 帧率节流器：把持续动画循环按目标帧率上限节流，降低 GPU 负载。
 *
 * 持续动画的惯用写法是 `while (isActive) withFrameNanos { ... }`，每帧都会恢复执行；
 * 若每帧都写状态就会每帧重绘。用法：
 *
 * ```
 * val throttle = rememberSceneFrameThrottle(FrameRateScene.GALLERY)
 * LaunchedEffect(throttle) {
 *     val startedAt = withFrameNanos { it }
 *     while (isActive) withFrameNanos { frameTime ->
 *         if (throttle.shouldUpdate(frameTime)) {
 *             progress = ((frameTime - startedAt) / ...).toFloat()
 *         }
 *     }
 * }
 * ```
 *
 * [shouldUpdate] 返回 false 时跳过状态写入，该帧不触发重绘。
 * 动画仍用绝对时间戳计算进度，所以跳帧不会造成动画变慢。
 */
@Immutable
class FrameThrottle(val intervalNanos: Long) {
    private var lastNanos = 0L

    /** 返回 true 表示应在当前帧更新状态。 */
    fun shouldUpdate(frameNanos: Long): Boolean {
        if (intervalNanos <= 0L) return true
        if (frameNanos - lastNanos >= intervalNanos) {
            lastNanos = frameNanos
            return true
        }
        return false
    }
}

/** 按 [scene] 当前的帧率上限创建节流器；fps 为 0 时返回不节流的实例。 */
@Composable
fun rememberSceneFrameThrottle(scene: FrameRateScene): FrameThrottle {
    val fps = LocalMusePerformancePolicy.current.fpsFor(scene)
    val interval = remember(fps) { if (fps > 0) 1_000_000_000L / fps else 0L }
    return remember(interval) { FrameThrottle(interval) }
}
