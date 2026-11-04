package com.philpf.dualsensemanager.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser

/**
 * A stylised DualSense seen from the front, with its two light strips beside the touchpad
 * glowing in [light]. When [lit] is false (controller away, or light bar not controllable)
 * the strips show the colour faintly, without the glow.
 */
@Composable
fun ControllerArt(light: Color, lit: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(ART_WIDTH / ART_HEIGHT)) {
        val s = size.width / ART_WIDTH
        scale(s, s, pivot = Offset.Zero) { drawController(light, lit) }
    }
}

private const val ART_WIDTH = 240f
private const val ART_HEIGHT = 150f

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

private val BodyPath = svgPath(
    "M46 20 C70 14 100 12 120 12 C140 12 170 14 194 20 C214 25 226 34 231 52 " +
        "C238 76 240 104 233 126 C229 140 214 146 202 140 C192 135 184 122 174 110 " +
        "C166 101 156 98 144 98 L96 98 C84 98 74 101 66 110 C56 122 48 135 38 140 " +
        "C26 146 11 140 7 126 C0 104 2 76 9 52 C14 34 26 25 46 20 Z",
)
private val Strips = listOf(
    svgPath("M80 22 C78 36 79 50 86 62"),
    svgPath("M160 22 C162 36 161 50 154 62"),
)

private val HaloCenter = Offset(120f, 42f)
private val BodyFill = Brush.verticalGradient(listOf(Color(0x29FFFFFF), Color(0x0DFFFFFF)), startY = 12f, endY = 146f)
private val Line = Color(0x52FFFFFF)
private val Faint = Color(0x38FFFFFF)
private val Fill = Color(0x0FFFFFFF)

private fun DrawScope.drawController(light: Color, lit: Boolean) {
    if (lit) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(light.copy(alpha = 0.45f), light.copy(alpha = 0.14f), Color.Transparent),
                center = HaloCenter,
                radius = 92f,
            ),
            radius = 92f,
            center = HaloCenter,
        )
    }

    drawPath(BodyPath, BodyFill)
    drawPath(BodyPath, Line, style = Stroke(width = 1.3f))

    // Touchpad
    drawRoundRect(Fill, topLeft = Offset(84f, 15f), size = Size(72f, 47f), cornerRadius = CornerRadius(9f))
    drawRoundRect(Faint, topLeft = Offset(84f, 15f), size = Size(72f, 47f), cornerRadius = CornerRadius(9f), style = Stroke(1f))

    // Light strips
    for (strip in Strips) {
        if (lit) {
            drawPath(strip, light.copy(alpha = 0.14f), style = Stroke(14f, cap = StrokeCap.Round))
            drawPath(strip, light.copy(alpha = 0.32f), style = Stroke(7.5f, cap = StrokeCap.Round))
            drawPath(strip, light, style = Stroke(3.2f, cap = StrokeCap.Round))
            drawPath(strip, lerp(light, Color.White, 0.7f), style = Stroke(1.1f, cap = StrokeCap.Round))
        } else {
            drawPath(strip, light.copy(alpha = 0.5f), style = Stroke(2.4f, cap = StrokeCap.Round))
        }
    }

    // Player LEDs
    for (i in 0 until 5) drawCircle(Faint, radius = 1.3f, center = Offset(108f + 6f * i, 70f))

    // PS button and sticks
    drawCircle(Fill, radius = 4.2f, center = Offset(120f, 84f))
    drawCircle(Line, radius = 4.2f, center = Offset(120f, 84f), style = Stroke(1f))
    for (x in floatArrayOf(92f, 148f)) {
        drawCircle(Fill, radius = 12f, center = Offset(x, 86f))
        drawCircle(Faint, radius = 12f, center = Offset(x, 86f), style = Stroke(1f))
        drawCircle(Color(0x14FFFFFF), radius = 6.5f, center = Offset(x, 86f))
    }

    // D-pad
    val arm = Stroke(1f)
    for ((x, y, w, h) in listOf(
        floatArrayOf(47.5f, 35f, 9f, 10f),
        floatArrayOf(47.5f, 55f, 9f, 10f),
        floatArrayOf(37f, 45.5f, 10f, 9f),
        floatArrayOf(57f, 45.5f, 10f, 9f),
    )) {
        drawRoundRect(Faint, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(2.5f), style = arm)
    }

    // Face buttons
    for ((x, y) in listOf(188f to 38f, 188f to 62f, 176f to 50f, 200f to 50f)) {
        drawCircle(Faint, radius = 5.2f, center = Offset(x, y), style = arm)
    }

    // Create and Options buttons
    drawRoundRect(Faint, topLeft = Offset(68f, 20f), size = Size(4f, 9f), cornerRadius = CornerRadius(2f), style = arm)
    drawRoundRect(Faint, topLeft = Offset(168f, 20f), size = Size(4f, 9f), cornerRadius = CornerRadius(2f), style = arm)
}
