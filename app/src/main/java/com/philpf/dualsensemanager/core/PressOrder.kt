package com.philpf.dualsensemanager.core

/**
 * Putting the connected controllers in order, as players join on a console: one empty place
 * per controller, and each controller that presses L2 + R2 takes the next place, so the first
 * becomes number 1, the next number 2, and so on. [joined] holds the controllers placed so far.
 *
 * At the first press the others give up their numbers until they press. When a single one is
 * left, it takes the last place without pressing: with two controllers, one press is enough.
 * Stopping earlier leaves those still waiting without a number; stopping before anyone pressed
 * changes nothing.
 */
data class PressOrder(val joined: List<String> = emptyList()) {
    /** The controllers in [connected] still waiting for a place, in [connected]'s order. */
    fun waiting(connected: List<String>): List<String> = connected.filter { it !in joined }

    /** Whether every controller in [connected] has its place. */
    fun complete(connected: Collection<String>): Boolean = connected.isNotEmpty() && connected.all { it in joined }

    /** [id], one of [connected], pressed L2 + R2: the order after that, and the numbers to give (0: none). */
    fun press(id: String, connected: List<String>): Pair<PressOrder, Map<String, Int>> {
        if (id in joined || id !in connected) return this to emptyMap()
        val numbers = LinkedHashMap<String, Int>()
        if (joined.isEmpty()) for (other in connected) if (other != id) numbers[other] = 0
        var next = PressOrder(joined + id)
        numbers[id] = next.joined.size
        val left = next.waiting(connected)
        if (left.size == 1) {
            next = PressOrder(next.joined + left.single())
            numbers[left.single()] = next.joined.size
        }
        return next to numbers
    }

    /** The numbers to give when stopping now: none for those still waiting, unless nobody pressed. */
    fun stop(connected: List<String>): Map<String, Int> =
        if (joined.isEmpty()) emptyMap() else waiting(connected).associateWith { 0 }
}
