package com.philpf.dualsensemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PressOrderTest {
    private fun profile(id: String, number: Int, customColor: Boolean = false) =
        ControllerProfile(id, number, ControllerKind.DUALSENSE, LedPalette.automaticColor(number, emptyList()), customColor)

    @Test
    fun eachControllerTakesTheNextPlaceOnce() {
        var order = PressOrder()
        assertEquals(1, order.next)
        order = order.press("b").press("b").press("a")
        assertEquals(listOf("b", "a"), order.joined)
        assertEquals(3, order.next)
        assertFalse(order.complete(listOf("a", "b", "c")))
        assertTrue(order.complete(listOf("a", "b")))
        assertFalse(PressOrder().complete(emptyList()))
    }

    @Test
    fun pressedFirstThenTheRestInTheirOrder() {
        val order = PressOrder(listOf("c", "gone"))
        assertEquals(listOf("c", "a", "b", "d"), order.order(listOf("a", "b", "c", "d")))
    }

    @Test
    fun rankingNumbersEveryControllerFromOne() {
        // Numbers 2, 4 and 7, with gaps: the first pressed becomes 1 even if a remembered
        // controller held it, and the rest follow.
        val profiles = listOf(profile("away", 2), profile("x", 4), profile("y", 7, customColor = true)).associateBy { it.id }
        val ranked = Numbering.ranked(profiles, listOf("y", "x"), playerColors = emptyList())
        assertEquals(mapOf("y" to 1, "x" to 2, "away" to 3), ranked.mapValues { it.value.number })
        // Automatic colours follow the numbers; a picked colour stays.
        assertEquals(profiles.getValue("y").color, ranked.getValue("y").color)
        assertEquals(ranked.getValue("x").color, LedPalette.automaticColor(2, listOf(ranked.getValue("y").color)))
    }
}
