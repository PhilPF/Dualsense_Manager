package com.philpf.dualsensemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetGridTest {
    private val eps = 1e-3f

    // A 3 × 2 widget in portrait on a five-column home screen is about 203 × 220 dp; less the
    // widget's 8 dp padding that leaves 187 × 204 for the tiles.
    private val portraitW = 187f
    private val portraitH = 204f

    @Test
    fun oneControllerFillsTheShorterSide() {
        val grid = WidgetGrid.layout(1, portraitW, portraitH)
        assertEquals(1, grid.columns)
        assertEquals(portraitW, grid.tile, eps)
        assertFalse(grid.scrolls)
    }

    @Test
    fun twoControllersSitSideBySide() {
        // Stacked tiles would be 100 dp, side by side 91.5 dp: within 10 %, and a row wins.
        val grid = WidgetGrid.layout(2, portraitW, portraitH)
        assertEquals(2, grid.columns)
        assertEquals(91.5f, grid.tile, eps)
        assertEquals(portraitW, grid.width, eps)
        assertEquals(91.5f, grid.height, eps)
    }

    @Test
    fun fourControllersMakeTwoByTwo() {
        val grid = WidgetGrid.layout(4, portraitW, portraitH)
        assertEquals(2, grid.columns)
        assertEquals(91.5f, grid.tile, eps)
        assertEquals(2 * 91.5f + WidgetGrid.GAP, grid.height, eps)
        assertFalse(grid.scrolls)
    }

    @Test
    fun fiveControllersScrollInPortrait() {
        val grid = WidgetGrid.layout(5, portraitW, portraitH)
        assertEquals(2, grid.columns)
        assertEquals(91.5f, grid.tile, eps)
        assertEquals(portraitH, grid.height, eps)
        assertTrue(grid.scrolls)
    }

    @Test
    fun wideWidgetShowsSixAsThreeByTwo() {
        val grid = WidgetGrid.layout(6, 269f, 184f)
        assertEquals(3, grid.columns)
        assertEquals(87f, grid.tile, eps)
        assertFalse(grid.scrolls)
    }

    @Test
    fun fourPreferTwoByTwoOverThreeAndOne() {
        // Three columns give 87 dp tiles with two empty places, two columns 90 dp with none.
        val grid = WidgetGrid.layout(4, 269f, 184f)
        assertEquals(2, grid.columns)
        assertEquals(90f, grid.tile, eps)
    }

    @Test
    fun threeInAWideWidgetMakeARow() {
        val grid = WidgetGrid.layout(3, 269f, 184f)
        assertEquals(3, grid.columns)
        assertEquals(87f, grid.tile, eps)
    }

    @Test
    fun narrowWidgetShowsOneAtATime() {
        val grid = WidgetGrid.layout(2, 114f, 86f)
        assertEquals(1, grid.columns)
        assertEquals(86f, grid.tile, eps)
        assertTrue(grid.scrolls)
    }

    @Test
    fun gridAlwaysFitsTheArea() {
        for (count in 0..9) {
            for (w in 60..420 step 7) {
                for (h in 60..420 step 11) {
                    val width = w.toFloat()
                    val height = h.toFloat()
                    val grid = WidgetGrid.layout(count, width, height)
                    val where = "count=$count area=$width×$height -> $grid"
                    assertTrue(where, grid.columns in 1..maxOf(count, 1))
                    assertTrue(where, grid.tile > 0f && grid.tile <= minOf(width, height) + eps)
                    assertEquals(where, grid.columns * grid.tile + (grid.columns - 1) * WidgetGrid.GAP, grid.width, eps)
                    assertTrue(where, grid.width <= width + eps && grid.height <= height + eps)
                    if (!grid.scrolls) {
                        val rows = (maxOf(count, 1) + grid.columns - 1) / grid.columns
                        assertEquals(where, rows * grid.tile + (rows - 1) * WidgetGrid.GAP, grid.height, eps)
                    }
                    // Tiles are never smaller than MIN_TILE when the area itself is not.
                    if (minOf(width, height) >= WidgetGrid.MIN_TILE) {
                        assertTrue(where, grid.tile >= WidgetGrid.MIN_TILE - eps)
                    }
                    // It scrolls only when no number of columns shows every tile at MIN_TILE.
                    if (grid.scrolls) {
                        val n = maxOf(count, 1)
                        for (c in 1..n) {
                            val r = (n + c - 1) / c
                            val side = minOf((width - (c - 1) * WidgetGrid.GAP) / c, (height - (r - 1) * WidgetGrid.GAP) / r)
                            assertTrue("$where c=$c", side < WidgetGrid.MIN_TILE)
                        }
                    }
                }
            }
        }
    }
}
