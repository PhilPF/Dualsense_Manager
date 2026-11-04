package com.philpf.dualsensemanager.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Small line icons drawn on a 24-unit grid, so they stay crisp at any size without an icon library.

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

private val BluetoothPath = svgPath("M7 7 L17 17 L12 22 L12 2 L17 7 L7 17")
private val BoltPath = svgPath("M13.5 2 L4.5 13.5 L11 13.5 L10 22 L19.5 10 L13 10 Z")
private val ChevronPath = svgPath("M9 5 L16 12 L9 19")

@Composable
private fun Icon24(modifier: Modifier, draw: DrawScope.() -> Unit) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) { draw() }
    }
}

private fun lineStroke(width: Float = 2f) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

@Composable
fun BluetoothIcon(tint: Color, modifier: Modifier = Modifier.size(14.dp)) = Icon24(modifier) {
    drawPath(BluetoothPath, tint, style = lineStroke(2.2f))
}

/** A cable plug: connector, plug body and cable. */
@Composable
fun UsbIcon(tint: Color, modifier: Modifier = Modifier.size(14.dp)) = Icon24(modifier) {
    drawRoundRect(tint, topLeft = Offset(8.5f, 2.5f), size = Size(7f, 6.5f), cornerRadius = CornerRadius(1.2f), style = lineStroke(1.8f))
    drawRoundRect(tint, topLeft = Offset(6f, 9f), size = Size(12f, 7.5f), cornerRadius = CornerRadius(2.2f))
    drawLine(tint, Offset(12f, 16.5f), Offset(12f, 22f), strokeWidth = 2.2f, cap = StrokeCap.Round)
}

@Composable
fun BoltIcon(tint: Color, modifier: Modifier = Modifier.size(14.dp)) = Icon24(modifier) {
    drawPath(BoltPath, tint)
}

@Composable
fun ChevronIcon(tint: Color, modifier: Modifier = Modifier.size(16.dp)) = Icon24(modifier) {
    drawPath(ChevronPath, tint, style = lineStroke(2.4f))
}

/**
 * Battery outline filled to [fraction] (null: unknown, drawn empty). Drawn on a 26 x 13 grid.
 */
@Composable
fun BatteryGlyph(fraction: Float?, tint: Color, fill: Color, modifier: Modifier = Modifier.size(width = 24.dp, height = 12.dp)) {
    Canvas(modifier) {
        val s = minOf(size.width / 26f, size.height / 13f)
        scale(s, s, pivot = Offset.Zero) {
            drawRoundRect(tint, topLeft = Offset(0.75f, 0.75f), size = Size(21.5f, 11.5f), cornerRadius = CornerRadius(3.2f), style = Stroke(1.5f))
            drawRoundRect(tint, topLeft = Offset(23f, 4f), size = Size(2f, 5f), cornerRadius = CornerRadius(1f))
            if (fraction != null) {
                val w = 17.5f * fraction.coerceIn(0f, 1f)
                if (w > 0.3f) {
                    drawRoundRect(fill, topLeft = Offset(2.75f, 2.75f), size = Size(w, 7.5f), cornerRadius = CornerRadius(1.6f))
                }
            }
        }
    }
}
