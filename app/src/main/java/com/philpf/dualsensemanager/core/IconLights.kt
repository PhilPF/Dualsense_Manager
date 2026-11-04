package com.philpf.dualsensemanager.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A coloured light behind the icon's glass: its centre and reach, in the icon's 108 × 108 square. */
data class IconLight(val x: Float, val y: Float, val radius: Float)

object IconLights {
    /** Without connected controllers the icon shows the PS players' colours, like the launcher icon. */
    val DEFAULT_COLORS = listOf(0x0030FF, 0xFF0A14, 0x00FF2A, 0xFF1E8C)

    /**
     * Where [count] lights go: one fills the icon, two sit side by side, three make a triangle,
     * four take the corners (as on the launcher icon, ic_launcher_background), and more go
     * round a circle.
     */
    fun layout(count: Int): List<IconLight> = when (count) {
        0 -> emptyList()
        1 -> listOf(IconLight(54f, 54f, 70f))
        2 -> listOf(IconLight(34f, 54f, 56f), IconLight(74f, 54f, 56f))
        3 -> listOf(IconLight(54f, 32f, 50f), IconLight(33f, 72f, 50f), IconLight(75f, 72f, 50f))
        4 -> listOf(IconLight(30f, 30f, 50f), IconLight(78f, 30f, 50f), IconLight(30f, 78f, 50f), IconLight(78f, 78f, 50f))
        else -> List(count) { k ->
            val angle = 2.0 * PI * k / count - PI / 2.0
            IconLight((54.0 + 28.0 * cos(angle)).toFloat(), (54.0 + 28.0 * sin(angle)).toFloat(), 42f)
        }
    }
}
