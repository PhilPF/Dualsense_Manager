package com.philpf.dualsensemanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * Cells in [columns] columns, [gap] apart, each [cellHeight] tall (square when null), in the
 * order they are given: a list with one column, a grid of cards with two.
 */
@Composable
fun CellGrid(columns: Int, gap: Dp, cellHeight: Dp?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val g = gap.roundToPx()
        val width = constraints.maxWidth
        val cellWidth = (width - g * (columns - 1)) / columns
        val height = cellHeight?.roundToPx() ?: cellWidth
        val placeables = measurables.map { it.measure(Constraints.fixed(cellWidth, height)) }
        val rows = (placeables.size + columns - 1) / columns
        layout(width, if (rows == 0) 0 else rows * height + (rows - 1) * g) {
            placeables.forEachIndexed { i, p -> p.place((i % columns) * (cellWidth + g), (i / columns) * (height + g)) }
        }
    }
}
