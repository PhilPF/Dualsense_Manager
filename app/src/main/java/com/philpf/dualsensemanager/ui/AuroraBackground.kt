package com.philpf.dualsensemanager.ui

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.philpf.dualsensemanager.ui.theme.GlassColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The scene behind the glass: slow, soft glows in the colours of the connected controllers'
 * light bars (a calm default palette when none is connected). Drifts gently unless the
 * system's animations are turned off.
 */
@Composable
fun AuroraBackground(lights: List<Color>, modifier: Modifier = Modifier) {
    val targets = List(SLOTS) { i -> if (lights.isEmpty()) IdlePalette[i] else lights[i % lights.size] }
    val colors = targets.mapIndexed { i, c -> animateColorAsState(c, tween(900), label = "aurora$i") }
    val strength by animateFloatAsState(if (lights.isEmpty()) 0.6f else 1f, tween(900), label = "auroraStrength")
    val time by rememberAuroraTime()
    val noise = rememberNoiseBrush()

    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(GlassColors.BackgroundTop, GlassColors.Background)))
        val w = size.width
        val h = size.height
        val t = time
        for (i in 0 until SLOTS) {
            val c = colors[i].value
            val anchor = Anchors[i]
            val phase = i * 1.7f
            val center = Offset(
                w * (anchor.x + 0.07f * sin(2f * PI.toFloat() * t / Periods[i] + phase)),
                h * (anchor.y + 0.05f * cos(2f * PI.toFloat() * t / (Periods[i] * 1.3f) + phase)),
            )
            val radius = max(w, h) * (0.5f + 0.07f * i)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c.copy(alpha = 0.40f * strength), c.copy(alpha = 0.12f * strength), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        // Fine noise breaks up banding in the dark gradients.
        drawRect(noise)
    }
}

private const val SLOTS = 4
private val Anchors = listOf(Offset(0.15f, 0.10f), Offset(0.90f, 0.32f), Offset(0.20f, 0.72f), Offset(0.85f, 0.95f))
private val Periods = floatArrayOf(29f, 37f, 31f, 41f)
private val IdlePalette = listOf(Color(0xFF3550FF), Color(0xFF7B3BFF), Color(0xFF00A6C2), Color(0xFF2E66FF))

/** Seconds since start, updated at about 30 fps; frozen when system animations are off. */
@Composable
private fun rememberAuroraTime(): State<Float> {
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    return produceState(0f, animate) {
        if (!animate) return@produceState
        val start = withFrameNanos { it }
        var last = start
        while (true) {
            withFrameNanos { now ->
                if (now - last >= FRAME_INTERVAL_NS) {
                    last = now
                    value = (now - start) / 1_000_000_000f
                }
            }
        }
    }
}

private const val FRAME_INTERVAL_NS = 33_000_000L

@Composable
private fun rememberNoiseBrush(): Brush = remember {
    val n = 128
    val random = java.util.Random(7)
    val pixels = IntArray(n * n) {
        val v = random.nextInt(256)
        (7 shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bitmap = Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}
