/*
 * 液态控件的拖拽/回弹运动模型。
 *
 * Adapted from Kyant0/AndroidLiquidGlass 的 catalog 示例
 * DampedDragAnimation.kt 与 DragGestureInspector.kt。
 *
 * Upstream: https://github.com/Kyant0/AndroidLiquidGlass
 * License: Apache License 2.0 - see licenses/APACHE-2.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Motion model adapted from Kyant0/AndroidLiquidGlass's Apache-2.0 catalog controls. */
internal class LiquidControlMotion(
    private val scope: CoroutineScope,
    initialValue: Float,
    val valueRange: ClosedRange<Float>,
    visibilityThreshold: Float,
    private val elasticity: Float = 0.5f,
    private val pressScale: Float = 1.5f,
    private val onDragStopped: LiquidControlMotion.() -> Unit,
    private val onDrag: LiquidControlMotion.(IntSize, Offset) -> Unit
) {
    private val value = Animatable(initialValue, visibilityThreshold)
    private val velocityValue = Animatable(0f, 5f)
    private val press = Animatable(0f, 0.001f)
    private val scaleXValue = Animatable(1f, 0.001f)
    private val scaleYValue = Animatable(1f, 0.001f)
    private val mutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()
    private val damping = 1f - elasticity.coerceIn(0f, 1f) * 0.65f
    private val stiffness = 1200f - elasticity.coerceIn(0f, 1f) * 1000f
    private val valueSpec = spring<Float>(damping, stiffness, visibilityThreshold)

    val currentValue: Float get() = value.value
    val targetValue: Float get() = value.targetValue
    val progress: Float get() {
        val range = valueRange.endInclusive - valueRange.start
        return if (range <= 0f) 0f else ((currentValue - valueRange.start) / range).coerceIn(0f, 1f)
    }
    val pressProgress: Float get() = press.value
    val scaleX: Float get() = scaleXValue.value
    val scaleY: Float get() = scaleYValue.value
    val velocity: Float get() = velocityValue.value

    val modifier = Modifier.pointerInput(Unit) {
        inspectLiquidDragGestures(
            onStart = { press() },
            onEnd = { onDragStopped(); release() },
            onCancel = { onDragStopped(); release() },
            onDrag = { _, amount -> onDrag(size, amount) }
        )
    }

    fun updateValue(newValue: Float) {
        scope.launch {
            value.animateTo(newValue.coerceIn(valueRange), valueSpec) {
                velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(this.value, 0f))
                val range = valueRange.endInclusive - valueRange.start
                val velocity = velocityTracker.calculateVelocity().x / range
                scope.launch { velocityValue.snapTo(velocity) }
            }
        }
    }

    fun animateToValue(newValue: Float, showPress: Boolean = true) {
        scope.launch {
            mutex.mutate {
                if (showPress) press()
                value.animateTo(newValue.coerceIn(valueRange), valueSpec)
                launch { velocityValue.animateTo(0f, spring(0.5f, 300f, 0.01f)) }
                if (showPress) release()
            }
        }
    }

    private fun press() {
        velocityTracker.resetTracking()
        scope.launch {
            launch { press.animateTo(1f, spring(1f, 1000f, 0.001f)) }
            launch { scaleXValue.animateTo(pressScale, spring(damping, stiffness, 0.001f)) }
            launch { scaleYValue.animateTo(pressScale, spring(damping, stiffness, 0.001f)) }
        }
    }

    private fun release() {
        scope.launch {
            withFrameNanos { }
            if (currentValue != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { value.value }.filter { abs(it - value.targetValue) < threshold }.first()
            }
            launch { press.animateTo(0f, spring(1f, 1000f, 0.001f)) }
            launch { scaleXValue.animateTo(1f, spring(damping, stiffness, 0.001f)) }
            launch { scaleYValue.animateTo(1f, spring(damping, stiffness, 0.001f)) }
        }
    }
}

internal suspend fun PointerInputScope.inspectLiquidDragGestures(
    onStart: (PointerInputChange) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit
) = awaitEachGesture {
    val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
    awaitFirstDown(false)
    onStart(initialDown)
    onDrag(initialDown, Offset.Zero)
    val up = liquidDrag(initialDown.id) { change -> onDrag(change, change.positionChange()) }
    if (up == null) onCancel() else onEnd()
}

private suspend inline fun AwaitPointerEventScope.liquidDrag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    if (currentEvent.changes.firstOrNull { it.id == pointerId }?.pressed != true) return null
    var pointer = pointerId
    while (true) {
        val change = awaitLiquidDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend fun AwaitPointerEventScope.awaitLiquidDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointer } ?: return null
        if (change.changedToUpIgnoreConsumed()) {
            val other = event.changes.firstOrNull { it.pressed } ?: return change
            pointer = other.id
        } else if (change.previousPosition != change.position) {
            return change
        }
    }
}
