// Adapted from Kyant0/AndroidLiquidGlass and KernelSU v3.3.1 (Apache 2.0),
// enhanced with live finger position reporting, dynamic valueRange safety, and Q-elastic spring physics.

package com.qpgallery.glass.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.util.fastFirstOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.TimeSource

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    var valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val canDrag: (Offset) -> Boolean = { true },
    val consumeDrag: Boolean = false,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDragCancelled: DampedDragAnimation.() -> Unit = onDragStopped,
    val onDrag: DampedDragAnimation.(size: IntSize, position: Offset, dragAmount: Offset) -> Unit,
) {
    private val valueAnimationSpec = spring(0.88f, 950f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.46f, 280f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(0.92f, 900f, 0.001f)
    private val scaleXAnimationSpec = spring(0.42f, 340f, 0.001f)
    private val scaleYAnimationSpec = spring(0.46f, 340f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private var pressJob: Job? = null
    private var releaseJob: Job? = null
    private var valueJob: Job? = null
    private var velocityJob: Job? = null

    private val velocityTracker = VelocityTracker()
    private val startMark = TimeSource.Monotonic.markNow()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val progress: Float
        get() {
            val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
            return ((value - valueRange.start) / span).coerceIn(0f, 1f)
        }

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            consumeDrag = consumeDrag,
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragCancelled()
                release()
            },
        ) { change, dragAmount ->
            val isInside = canDrag(change.position)
            val wasInside = canDrag(change.previousPosition)
            if (isInside && wasInside) {
                onDrag(size, change.position, dragAmount)
            }
        }
    }

    fun press() {
        releaseJob?.cancel()
        pressJob?.cancel()
        velocityTracker.resetTracking()
        pressJob = animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        releaseJob?.cancel()
        releaseJob = animationScope.launch {
            awaitFrame()
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .first { abs(it - valueAnimation.targetValue) <= threshold }
            }
            launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val clamped = value.coerceIn(valueRange)
        valueJob?.cancel()
        valueJob = animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            valueAnimation.animateTo(clamped, valueAnimationSpec) { updateVelocity() }
            velocityJob?.cancel()
            velocityJob = animationScope.launch {
                velocityAnimation.animateTo(0f, velocityAnimationSpec)
            }
        }
    }

    fun snapValue(value: Float) {
        val clamped = value.coerceIn(valueRange)
        valueJob?.cancel()
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            valueAnimation.snapTo(clamped)
            updateVelocity()
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val clamped = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(clamped, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            startMark.elapsedNow().inWholeMilliseconds,
            Offset(value, 0f),
        )
        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
        val targetVelocity = velocityTracker.calculateVelocity().x / span
        velocityJob?.cancel()
        velocityJob = animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            velocityAnimation.snapTo(targetVelocity)
        }
    }
}

suspend fun PointerInputScope.inspectDragGestures(
    consumeDrag: Boolean = false,
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val upEvent = dragPointer(
            pointerId = initialDown.id,
            onDrag = { change ->
                val delta = change.positionChange()
                if (consumeDrag) change.consume()
                onDrag(change, delta)
            },
        )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.dragPointer(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId,
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else if (dragEvent.previousPosition != dragEvent.position) {
            return dragEvent
        }
    }
}
