package com.philpf.dualsensemanager.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.ui.theme.GlassPalette

/** Where a controller's light glows from, in root coordinates. */
data class GlowSpot(val center: Offset, val color: Color)

/**
 * The scene behind the glass: a plain gradient with, behind each connected controller's cell,
 * a soft glow in its light-bar colour. The cells blur it, so each one seems to emanate its
 * colour, which also spills out around it.
 */
@Composable
fun GlowBackground(spots: () -> List<GlowSpot>, glass: GlassPalette, modifier: Modifier = Modifier) {
    val noise = rememberNoiseBrush(glass.isDark)
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(glass.backgroundTop, glass.backgroundBottom)))
        // A faint wash of the accent so an empty screen isn't flat.
        val wash = Offset(size.width * 0.15f, 0f)
        drawCircle(
            Brush.radialGradient(listOf(glass.accent.copy(alpha = 0.12f), Color.Transparent), center = wash, radius = size.width),
            radius = size.width,
            center = wash,
        )
        val radius = 240.dp.toPx()
        for (spot in spots()) {
            val a = glass.glowAlpha
            drawCircle(
                brush = Brush.radialGradient(
                    0f to spot.color.copy(alpha = a),
                    0.45f to spot.color.copy(alpha = a * 0.38f),
                    1f to Color.Transparent,
                    center = spot.center,
                    radius = radius,
                ),
                radius = radius,
                center = spot.center,
            )
        }
        drawRect(noise)
    }
}

/** Fine noise that breaks up banding in the smooth gradients. */
@Composable
private fun rememberNoiseBrush(dark: Boolean): Brush = remember(dark) {
    val n = 128
    val random = java.util.Random(7)
    val alpha = if (dark) 7 else 5
    val pixels = IntArray(n * n) {
        val v = random.nextInt(256)
        (alpha shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bitmap = Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}
