package com.philpf.dualsensemanager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.launch

/** How long one wave takes to flood its panel and fade away, in ms. */
private const val WAVE_MS = 1_000
private const val MAX_WAVES = 3

/** The waves running on one panel; each value goes from 0 to 1. */
typealias PressWaves = List<Animatable<Float, AnimationVector1D>>

/** Starts a wave each time [presses] grows (not for the count it starts with). */
@Composable
fun rememberPressWaves(presses: Int): PressWaves {
    val waves = remember { mutableStateListOf<Animatable<Float, AnimationVector1D>>() }
    // Waves run in this scope, so a newer press does not cancel an older wave.
    val scope = rememberCoroutineScope()
    var seen by remember { mutableIntStateOf(presses) }
    LaunchedEffect(presses) {
        if (presses > seen) {
            val wave = Animatable(0f)
            waves += wave
            while (waves.size > MAX_WAVES) waves.removeAt(0)
            scope.launch {
                wave.animateTo(1f, tween(WAVE_MS, easing = LinearEasing))
                waves -= wave
            }
        }
        seen = presses
    }
    return waves
}

/**
 * Draws [waves] of [light]: discs growing from [origin] until they cover the whole area,
 * brightest at their front, which then fade. Callers clip to their panel.
 */
fun DrawScope.drawPressWaves(waves: PressWaves, origin: Offset, light: Color) {
    if (waves.isEmpty()) return
    val reach = maxOf(
        origin.getDistance(),
        (origin - Offset(size.width, 0f)).getDistance(),
        (origin - Offset(0f, size.height)).getDistance(),
        (origin - Offset(size.width, size.height)).getDistance(),
    )
    for (wave in waves) {
        val t = wave.value
        // The front reaches the farthest corner at about two thirds of the time, then the
        // flooded panel fades.
        val radius = 1f + reach * 1.05f * FastOutSlowInEasing.transform(t)
        val strength = 0.62f * (1f - smoothstep(0.35f, 1f, t))
        if (strength <= 0.001f) continue
        drawCircle(
            brush = Brush.radialGradient(
                0f to light.copy(alpha = strength * 0.45f),
                0.72f to light.copy(alpha = strength * 0.7f),
                0.95f to light.copy(alpha = strength),
                1f to light.copy(alpha = 0f),
                center = origin,
                radius = radius,
            ),
            radius = radius,
            center = origin,
        )
    }
}

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
