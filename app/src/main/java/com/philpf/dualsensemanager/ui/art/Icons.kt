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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Small icons on a 24-unit grid, drawn directly so they stay crisp without an icon library.
// The gear and the bin follow the shapes of Google's Material Icons (Apache License 2.0).

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

private val BluetoothPath = svgPath("M7 7 L17 17 L12 22 L12 2 L17 7 L7 17")
private val BoltPath = svgPath("M13.5 2 L4.5 13.5 L11 13.5 L10 22 L19.5 10 L13 10 Z")
private val ChevronPath = svgPath("M9 5 L16 12 L9 19")
private val GearPath = svgPath(
    "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58" +
        "c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96" +
        "c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84" +
        "c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33" +
        "c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58" +
        "C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61" +
        "l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54" +
        "c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54" +
        "c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32" +
        "c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94z" +
        "M12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z",
).apply { fillType = PathFillType.EvenOdd }
private val BinPath = svgPath(
    "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM8 9h8v10H8V9zm7.5-5l-1-1h-5l-1 1H5v2h14V4z",
).apply { fillType = PathFillType.EvenOdd }

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

@Composable
fun GearIcon(tint: Color, modifier: Modifier = Modifier.size(22.dp)) = Icon24(modifier) {
    drawPath(GearPath, tint)
}

@Composable
fun BinIcon(tint: Color, modifier: Modifier = Modifier.size(20.dp)) = Icon24(modifier) {
    drawPath(BinPath, tint)
}

@Composable
fun PlusIcon(tint: Color, modifier: Modifier = Modifier.size(16.dp)) = Icon24(modifier) {
    drawLine(tint, Offset(12f, 5f), Offset(12f, 19f), strokeWidth = 2.4f, cap = StrokeCap.Round)
    drawLine(tint, Offset(5f, 12f), Offset(19f, 12f), strokeWidth = 2.4f, cap = StrokeCap.Round)
}

@Composable
fun CloseIcon(tint: Color, modifier: Modifier = Modifier.size(16.dp)) = Icon24(modifier) {
    drawLine(tint, Offset(6f, 6f), Offset(18f, 18f), strokeWidth = 2.4f, cap = StrokeCap.Round)
    drawLine(tint, Offset(18f, 6f), Offset(6f, 18f), strokeWidth = 2.4f, cap = StrokeCap.Round)
}

/** "Find": a dot sending out waves on both sides, like a buzzing controller. */
@Composable
fun FindIcon(tint: Color, modifier: Modifier = Modifier.size(22.dp)) = Icon24(modifier) {
    drawCircle(tint, radius = 2.6f, center = Offset(12f, 12f))
    val wave = lineStroke(2f)
    for ((radius, sweep) in listOf(6f to 70f, 10f to 64f)) {
        val topLeft = Offset(12f - radius, 12f - radius)
        val arc = Size(2 * radius, 2 * radius)
        drawArc(tint, startAngle = -sweep / 2f, sweepAngle = sweep, useCenter = false, topLeft = topLeft, size = arc, style = wave)
        drawArc(tint, startAngle = 180f - sweep / 2f, sweepAngle = sweep, useCenter = false, topLeft = topLeft, size = arc, style = wave)
    }
}

/** Power symbol: a ring open at the top, with a stroke through the gap. */
@Composable
fun PowerIcon(tint: Color, modifier: Modifier = Modifier.size(20.dp)) = Icon24(modifier) {
    drawArc(tint, startAngle = -50f, sweepAngle = 280f, useCenter = false, topLeft = Offset(5f, 6f), size = Size(14f, 14f), style = lineStroke(2f))
    drawLine(tint, Offset(12f, 3.5f), Offset(12f, 11.5f), strokeWidth = 2f, cap = StrokeCap.Round)
}

/** Battery outline filled to [fraction] (null: unknown, drawn empty). Drawn on a 26 x 13 grid. */
@Composable
fun BatteryGlyph(fraction: Float?, tint: Color, fill: Color, modifier: Modifier = Modifier.size(width = 22.dp, height = 11.dp)) {
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
