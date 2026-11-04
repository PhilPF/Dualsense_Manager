package com.philpf.dualsensemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class InputMathTest {
    private val eps = 1e-5f

    @Test
    fun insideTheDeadZoneIsRest() {
        assertEquals(0f to 0f, InputMath.deadZone(0.05f, -0.05f))
        assertEquals(0f to 0f, InputMath.deadZone(0f, 0f))
    }

    @Test
    fun fullTiltStaysFullAndKeepsDirection() {
        val (x, y) = InputMath.deadZone(0.6f, -0.8f) // |v| = 1
        assertEquals(1f, hypot(x, y), eps)
        assertEquals(0.6f, x, eps)
        assertEquals(-0.8f, y, eps)
    }

    @Test
    fun lengthIsContinuousAndNeverAboveOne() {
        var previous = 0f
        for (i in 0..2000) {
            val m = i / 1000f // 0 … 2, beyond the unit circle too
            val (x, y) = InputMath.deadZone(m * 0.28f, m * 0.96f) // direction of length 1
            val length = hypot(x, y)
            assertTrue(length <= 1f + eps)
            assertTrue("jump at m=$m", length - previous < 0.0015f)
            previous = length
        }
    }

    @Test
    fun newPressesAreFoundPerDevice() {
        val before = mapOf(1 to setOf("CROSS"), 2 to setOf("L1"))
        val now = mapOf(1 to setOf("CROSS", "CIRCLE"), 2 to setOf("L1"), 3 to setOf("R1"))
        assertEquals(setOf(1, 3), InputMath.newlyPressed(before, now))
        // Letting go is not a press.
        assertEquals(emptySet<Int>(), InputMath.newlyPressed(now, before))
    }
}
