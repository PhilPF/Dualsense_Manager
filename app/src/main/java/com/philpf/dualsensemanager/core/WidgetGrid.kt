package com.philpf.dualsensemanager.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The home-screen widget's tiles: [columns] columns of square tiles of side [tile], in a grid
 * of [width] × [height], all in dp, centred in the widget. When [scrolls], there are more rows
 * below.
 */
data class TileGrid(
    val columns: Int,
    val tile: Float,
    val width: Float,
    val height: Float,
    val scrolls: Boolean,
)

object WidgetGrid {
    /** Space between tiles, in dp. */
    const val GAP = 4f

    /** The smallest tile that still shows the drawing and two lines of text, in dp. */
    const val MIN_TILE = 80f

    /** Other layouts count as good as the best one while their tiles are this fraction of its side. */
    const val NEAR_BEST = 0.9f

    /**
     * Square tiles for [count] controllers in an area of [width] × [height] dp.
     *
     * With c columns there are r(c) = ⌈count / c⌉ rows, and the largest square side that fits is
     *   s(c) = min((width − (c − 1)·GAP) / c, (height − (r(c) − 1)·GAP) / r(c)).
     * Let s* = max { s(c) : 1 ≤ c ≤ count }. If s* ≥ MIN_TILE, every tile is shown: among the c
     * with s(c) ≥ max(NEAR_BEST·s*, MIN_TILE), the grid takes the one leaving the fewest empty
     * places c·r(c) − count, and then the one with more columns (a row reads better than a
     * column). Otherwise the grid fits as many columns of at least MIN_TILE as the width allows
     * (at least one), with tiles no taller than the area, and scrolls.
     */
    fun layout(count: Int, width: Float, height: Float): TileGrid {
        require(width > 0f && height > 0f) { "empty area" }
        val n = max(count, 1)
        fun rows(columns: Int) = (n + columns - 1) / columns
        fun side(columns: Int): Float {
            val r = rows(columns)
            return min((width - (columns - 1) * GAP) / columns, (height - (r - 1) * GAP) / r)
        }
        fun span(cells: Int, tile: Float) = cells * tile + (cells - 1) * GAP

        val best = (1..n).maxOf { side(it) }
        if (best >= MIN_TILE) {
            val threshold = max(NEAR_BEST * best, MIN_TILE)
            val columns = (1..n)
                .filter { side(it) >= threshold }
                .minWith(compareBy<Int> { it * rows(it) - n }.thenByDescending { it })
            val tile = side(columns)
            return TileGrid(columns, tile, span(columns, tile), span(rows(columns), tile), scrolls = false)
        }
        val columns = max(1, floor((width + GAP) / (MIN_TILE + GAP)).toInt()).coerceAtMost(n)
        val tile = min((width - (columns - 1) * GAP) / columns, height)
        val content = span(rows(columns), tile)
        return TileGrid(columns, tile, span(columns, tile), min(content, height), scrolls = content > height)
    }
}
