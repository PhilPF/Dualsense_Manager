package com.philpf.dualsensemanager.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/**
 * Over-ear headphones in lines, in [ink], in the same box as the controller drawing so the two
 * line up: a padded headband over two cups, faintly filled, with their ear pads. [glow], when
 * not transparent, lights the cups (the noise control).
 */
@Composable
fun HeadphonesArt(ink: Color, modifier: Modifier = Modifier, glow: Color = Color.Transparent) {
    Canvas(modifier.aspectRatio(ControllerArtAspect)) {
        val s = minOf(size.width / WIDTH, size.height / HEIGHT)
        translate((size.width - WIDTH * s) / 2f, (size.height - HEIGHT * s) / 2f) {
            scale(s, s, pivot = Offset.Zero) {
                val line = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val band = Path().apply {
                    moveTo(32f, 54f)
                    cubicTo(30f, 2f, 116f, 2f, 114f, 54f)
                }
                val padding = Path().apply {
                    moveTo(40f, 50f)
                    cubicTo(39f, 14f, 107f, 14f, 106f, 50f)
                }
                drawPath(band, ink, style = line)
                drawPath(padding, ink.copy(alpha = ink.alpha * 0.45f), style = Stroke(width = 2f, cap = StrokeCap.Round))
                for (left in listOf(true, false)) {
                    val x = if (left) 22f else 104f
                    if (glow.alpha > 0f) {
                        drawRoundRect(glow, topLeft = Offset(x - 5f, 47f), size = Size(30f, 54f), cornerRadius = CornerRadius(14f))
                    }
                    drawRoundRect(ink.copy(alpha = ink.alpha * 0.08f), topLeft = Offset(x, 52f), size = Size(20f, 44f), cornerRadius = CornerRadius(9f))
                    drawRoundRect(ink, topLeft = Offset(x, 52f), size = Size(20f, 44f), cornerRadius = CornerRadius(9f), style = line)
                    // The ear pad, on the inner side.
                    val padX = if (left) x + 15f else x - 1f
                    drawRoundRect(ink, topLeft = Offset(padX, 57f), size = Size(6f, 34f), cornerRadius = CornerRadius(3f), style = Stroke(width = 2.2f))
                }
            }
        }
    }
}

private const val WIDTH = 146f
private const val HEIGHT = 100f
