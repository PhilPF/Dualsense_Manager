package com.philpf.dualsensemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AppIconChoiceTest {
    private val blue = 0x0030FF
    private val red = 0xFF0A14
    private val pink = 0xFF1E8C
    private val cyan = 0x00E5FF

    @Test
    fun noControllersShowThePlainIcon() {
        assertEquals(AppIconChoice.PLAIN_KEY, AppIconChoice.key(emptyList()))
        assertEquals(emptyList<Int>(), AppIconChoice.colors(AppIconChoice.PLAIN_KEY))
        assertEquals("icon.Plain", AppIconChoice.aliasName(AppIconChoice.PLAIN_KEY))
        assertEquals("icon.Lights0123", AppIconChoice.aliasName("0123"))
        assertEquals(listOf(blue, red, 0x00FF2A, pink), AppIconChoice.colors("0123"))
    }

    @Test
    fun coloursAreSortedAndRepeatsDropped() {
        assertEquals("1", AppIconChoice.key(listOf(red)))
        assertEquals("01", AppIconChoice.key(listOf(red, blue)))
        assertEquals("0", AppIconChoice.key(listOf(blue, blue)))
    }

    @Test
    fun customColoursTakeTheNearestPreset() {
        assertEquals(5, AppIconChoice.nearestPreset(0x10E0F0)) // near cyan
        assertEquals(0, AppIconChoice.nearestPreset(0x102080)) // dark blue
        assertEquals("35", AppIconChoice.key(listOf(cyan, 0xF02090)))
    }

    @Test
    fun atMostFourColoursTheFirstOnes() {
        val five = listOf(0xFFFFFF, cyan, red, blue, pink) // white, cyan, red, blue, then pink
        assertEquals("0158", AppIconChoice.key(five))
    }

    @Test
    fun everyKeyIsAShippedIcon() {
        // tools/app_icons.py ships the strictly increasing index strings of length 1 to 4.
        val random = Random(7)
        repeat(2_000) {
            val colors = List(random.nextInt(1, 7)) { random.nextInt(0, 0x1000000) }
            val key = AppIconChoice.key(colors)
            assertTrue(key, key.length in 1..AppIconChoice.MAX_COLORS)
            assertTrue(key, key.all { it in '0'..'8' })
            assertTrue(key, key.zipWithNext().all { (a, b) -> a < b })
        }
    }
}
