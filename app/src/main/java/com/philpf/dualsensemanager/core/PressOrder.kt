package com.philpf.dualsensemanager.core

/**
 * Putting the controllers in order by pressing a button on each, in turn, as players join on a
 * console: the first one pressed becomes number 1 (its player lights show 1), the next number 2,
 * and so on. [joined] holds the controllers pressed so far, in order.
 */
data class PressOrder(val joined: List<String> = emptyList()) {
    /** The place the next controller pressed takes, from 1. */
    val next: Int get() = joined.size + 1

    /** [id] was pressed: it takes the next place, unless it already has one. */
    fun press(id: String): PressOrder = if (id in joined) this else PressOrder(joined + id)

    /** Whether every controller in [connected] has taken its place. */
    fun complete(connected: Collection<String>): Boolean = connected.isNotEmpty() && connected.all { it in joined }

    /** All the controllers in [current] (in their present order), those pressed first, in the order pressed. */
    fun order(current: List<String>): List<String> = joined.filter { it in current } + current.filter { it !in joined }
}
