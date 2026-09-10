package com.bringyour.network.ui.connect

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.animateToWithDecay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlin.math.abs

/** The connect drawer's two resting positions. */
enum class ConnectDrawerValue { Collapsed, Expanded }

/**
 * The connect drawer's position, on foundation's anchored draggable so the
 * drawer, its drag handle and the nested-scroll connection under its content
 * all move the same state and the gesture rules (mmm/DESIGNSTYLE.md) can be
 * enforced in full: a Material sheet would grab any touch that lands while
 * it animates, and gives no way to move or snap it from outside.
 */
@Stable
class ConnectDrawerState(initialValue: ConnectDrawerValue = ConnectDrawerValue.Collapsed) {

    val draggable = AnchoredDraggableState(initialValue)

    val currentValue: ConnectDrawerValue
        get() = draggable.currentValue

    /** Where the drawer is heading: its resting position, or the one it animates to. */
    val targetValue: ConnectDrawerValue
        get() = draggable.targetValue

    val isAnimationRunning: Boolean
        get() = draggable.isAnimationRunning

    fun requireOffset(): Float = draggable.requireOffset()

    suspend fun expand() {
        draggable.animateTo(ConnectDrawerValue.Expanded, AnchoredDraggableDefaults.SnapAnimationSpec)
    }

    suspend fun partialExpand() {
        draggable.animateTo(ConnectDrawerValue.Collapsed, AnchoredDraggableDefaults.SnapAnimationSpec)
    }

    /**
     * Stops a running animation so a drag through the content moves the
     * drawer from where it is, instead of fighting the animation frame by
     * frame.
     */
    suspend fun interruptAnimation() {
        if (draggable.isAnimationRunning) {
            draggable.anchoredDrag(MutatePriority.UserInput) { }
        }
    }

    /**
     * Settles the drawer after a drag through its content was released with
     * [velocity] (negative upward), by the same rule as the drag handle: a
     * release faster than [velocityThresholdPx] goes the way it moves, a
     * slower one goes to the other position only once it has travelled
     * [positionalThresholdPx] from where it rested.
     */
    suspend fun release(velocity: Float, velocityThresholdPx: Float, positionalThresholdPx: Float) {
        val anchors = draggable.anchors
        if (!anchors.hasPositionFor(ConnectDrawerValue.Expanded) || !anchors.hasPositionFor(ConnectDrawerValue.Collapsed)) {
            return
        }
        val target = releaseTarget(
            offset = requireOffset(),
            velocity = velocity,
            from = draggable.settledValue,
            expandedOffset = anchors.positionOf(ConnectDrawerValue.Expanded),
            collapsedOffset = anchors.positionOf(ConnectDrawerValue.Collapsed),
            velocityThresholdPx = velocityThresholdPx,
            positionalThresholdPx = positionalThresholdPx,
        )
        draggable.animateToWithDecay(target, velocity)
    }

    companion object {

        /** The resting position a release lands on. Pure, for tests. */
        fun releaseTarget(
            offset: Float,
            velocity: Float,
            from: ConnectDrawerValue,
            expandedOffset: Float,
            collapsedOffset: Float,
            velocityThresholdPx: Float,
            positionalThresholdPx: Float,
        ): ConnectDrawerValue {
            if (abs(velocity) >= velocityThresholdPx) {
                return if (velocity < 0f) ConnectDrawerValue.Expanded else ConnectDrawerValue.Collapsed
            }
            return when (from) {
                ConnectDrawerValue.Collapsed ->
                    if (offset <= collapsedOffset - positionalThresholdPx) ConnectDrawerValue.Expanded
                    else ConnectDrawerValue.Collapsed
                ConnectDrawerValue.Expanded ->
                    if (offset >= expandedOffset + positionalThresholdPx) ConnectDrawerValue.Collapsed
                    else ConnectDrawerValue.Expanded
            }
        }
    }
}

@Composable
fun rememberConnectDrawerState(): ConnectDrawerState {
    return remember { ConnectDrawerState() }
}
