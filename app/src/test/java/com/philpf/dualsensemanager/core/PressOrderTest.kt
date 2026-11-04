package com.philpf.dualsensemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PressOrderTest {
    private val blue = 0x0030FF
    private val red = 0xFF0A14
    private val green = 0x00FF2A

    private fun profile(id: String, number: Int, color: Int = LedPalette.automaticColor(number, emptyList()), custom: Boolean = false) =
        ControllerProfile(id, number, ControllerKind.DUALSENSE, color, custom)

    @Test
    fun withTwoControllersOnePressIsEnough() {
        val (order, numbers) = PressOrder().press("b", listOf("a", "b"))
        assertEquals(listOf("b", "a"), order.joined)
        assertEquals(mapOf("a" to 2, "b" to 1), numbers)
        assertTrue(order.complete(listOf("a", "b")))
    }

    @Test
    fun othersWaitWithoutANumberAndTheLastIsPlacedByItself() {
        val connected = listOf("a", "b", "c")
        val (first, numbers1) = PressOrder().press("c", connected)
        assertEquals(mapOf("a" to 0, "b" to 0, "c" to 1), numbers1)
        assertEquals(listOf("a", "b"), first.waiting(connected))
        assertFalse(first.complete(connected))
        // Pressing again changes nothing.
        assertEquals(first to emptyMap<String, Int>(), first.press("c", connected))
        val (second, numbers2) = first.press("a", connected)
        assertEquals(mapOf("a" to 2, "b" to 3), numbers2)
        assertEquals(listOf("c", "a", "b"), second.joined)
        assertTrue(second.complete(connected))
    }

    @Test
    fun stoppingEarlyLeavesTheWaitingWithoutANumber() {
        val connected = listOf("a", "b", "c", "d")
        assertEquals(emptyMap<String, Int>(), PressOrder().stop(connected))
        val (order, _) = PressOrder().press("b", connected)
        assertEquals(mapOf("a" to 0, "c" to 0, "d" to 0), order.stop(connected))
    }

    @Test
    fun takingANumberMovesWhoeverHeldIt() {
        // A remembered controller holds 1; the first to press takes it, and it moves to 3.
        val profiles = listOf(profile("away", 1), profile("a", 2), profile("b", 3)).associateBy { it.id }
        val next = Numbering.set(profiles, mapOf("b" to 1, "a" to 0), playerColors = emptyList())
        assertEquals(mapOf("away" to 2, "a" to 0, "b" to 1), next.mapValues { it.value.number })
        // Number 1 is blue: b takes it from "away", which takes the next free colour; "a",
        // left without a number, keeps red.
        assertEquals(blue, next.getValue("b").color)
        assertEquals(red, next.getValue("a").color)
        assertEquals(green, next.getValue("away").color)
    }

    @Test
    fun withoutANumberAControllerHasNoLightsAndNoNumberInItsName() {
        assertEquals(emptySet<Int>(), PlayerLeds.pattern(0))
        assertEquals("DualSense", profile("x", 0).defaultName)
        assertEquals("DualSense 2", profile("x", 2).defaultName)
        // The first free colour, as it has no colour of its own.
        assertEquals(red, LedPalette.automaticColor(0, listOf(blue)))
    }

    @Test
    fun controllersWithoutANumberAreListedAfterTheNumberedOnes() {
        val profiles = listOf(profile("n0", 0), profile("n2", 2), profile("n1", 1))
        val live = profiles.map { LiveController(it.id, 0, ControllerKind.DUALSENSE, Transport.USB, null, true) }
        assertEquals(listOf("n1", "n2", "n0"), ControllerViews.build(profiles, emptyMap(), live).map { it.id })
    }
}
