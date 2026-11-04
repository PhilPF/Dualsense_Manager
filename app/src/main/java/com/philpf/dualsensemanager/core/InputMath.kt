package com.philpf.dualsensemanager.core

import kotlin.math.hypot

/** Where a controller's two sticks are: each axis in [−1, 1], y pointing down, (0, 0) at rest. */
data class Sticks(
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f,
)

object InputMath {
    /** Sticks rarely rest exactly at 0; tilts below this (as a fraction of full tilt) count as rest. */
    const val DEAD_ZONE = 0.08f

    /**
     * A stick position with a round dead zone of radius [zone]: for v = (x, y) with |v| = m,
     * the result is 0 when m < zone, and otherwise v/m · min(1, (m − zone)/(1 − zone)).
     * So the direction is kept, the length is continuous in v (0 at the edge of the zone)
     * and never exceeds 1.
     */
    fun deadZone(x: Float, y: Float, zone: Float = DEAD_ZONE): Pair<Float, Float> {
        val m = hypot(x, y)
        if (m < zone || m == 0f) return 0f to 0f
        val length = ((m - zone) / (1f - zone)).coerceAtMost(1f)
        return x / m * length to y / m * length
    }

    /** The devices on which something is pressed in [now] that was not in [before]. */
    fun <P> newlyPressed(before: Map<Int, Set<P>>, now: Map<Int, Set<P>>): Set<Int> =
        now.filter { (id, parts) -> !before[id].orEmpty().containsAll(parts) }.keys
}
