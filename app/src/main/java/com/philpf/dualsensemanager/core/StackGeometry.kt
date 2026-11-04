package com.philpf.dualsensemanager.core

import kotlin.math.ceil
import kotlin.math.roundToInt

/** A StackView's size and position (top-left), in pixels, inside its widget. */
data class StackPlacement(val width: Int, val height: Int, val left: Int, val top: Int)

/**
 * Where to put a StackView so that its front card covers the whole widget and the cards
 * behind it fall outside the widget, which clips them: one card at a time, edge to edge, still
 * snapping from card to card when swiped.
 *
 * StackView (AOSP) lays each card out at round(9/10) of its own width and height, inside a frame
 * padded by p on every side, and moves the front card down by 1/10 of its height; the cards
 * behind sit higher and further right. So a stack W × H placed at (x, y) shows its front card
 * at [x + p, x + round(0.9 W) − p] × [y + 0.1 H + p, y + 0.1 H + round(0.9 H) − p].
 * Asking for that rectangle to cover [−o, w + o] × [−o, h + o] (a small overshoot o hides
 * rounding) gives W = ⌈(w + 2p + 2o) / 0.9⌉, H likewise, x = −(p + o), y = −(0.1 H + p + o).
 */
object StackGeometry {
    /** StackView's share of its size for the cards' offsets (its PERSPECTIVE_SHIFT_FACTOR). */
    const val SHIFT = 0.1f

    /** StackView's frame padding around each card, in pixels: ⌈4 dp⌉ (its FRAME_PADDING). */
    fun framePadding(density: Float): Int = ceil(4f * density).toInt()

    fun placement(width: Int, height: Int, framePadding: Int, overshoot: Int = 1): StackPlacement {
        val p = framePadding
        val o = overshoot
        val stackWidth = ceil((width + 2 * p + 2 * o) / (1f - SHIFT)).toInt()
        val stackHeight = ceil((height + 2 * p + 2 * o) / (1f - SHIFT)).toInt()
        return StackPlacement(
            width = stackWidth,
            height = stackHeight,
            left = -(p + o),
            top = -((SHIFT * stackHeight).roundToInt() + p + o),
        )
    }
}
