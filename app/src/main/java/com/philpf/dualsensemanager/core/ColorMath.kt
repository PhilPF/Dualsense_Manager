package com.philpf.dualsensemanager.core

import kotlin.math.abs
import kotlin.math.roundToInt

/** Hue in [0, 360), saturation and value in [0, 1]. */
data class Hsv(val hue: Float, val saturation: Float, val value: Float)

object ColorMath {
    fun toHsv(rgb: Int): Hsv {
        val r = ((rgb shr 16) and 0xFF) / 255f
        val g = ((rgb shr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val hue = when {
            d == 0f -> 0f
            max == r -> 60f * ((g - b) / d).mod(6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        val saturation = if (max == 0f) 0f else d / max
        return Hsv(hue.mod(360f), saturation, max)
    }

    fun toRgb(hsv: Hsv): Int {
        val h = hsv.hue.mod(360f)
        val s = hsv.saturation.coerceIn(0f, 1f)
        val v = hsv.value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - abs((h / 60f).mod(2f) - 1f))
        val m = v - c
        val (r, g, b) = when ((h / 60f).toInt().coerceIn(0, 5)) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun channel(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255)
        return (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    fun hex(rgb: Int): String = "#" + (rgb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
}
