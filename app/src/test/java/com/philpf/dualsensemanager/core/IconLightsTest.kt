package com.philpf.dualsensemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconLightsTest {
    @Test
    fun oneLightPerColourInsideTheIcon() {
        for (n in 0..8) {
            val lights = IconLights.layout(n)
            assertEquals(n, lights.size)
            for (light in lights) {
                assertTrue(light.x in 0f..108f && light.y in 0f..108f)
                // Every light reaches the icon's centre, so the colours mix there.
                val toCentre = kotlin.math.hypot(light.x - 54f, light.y - 54f)
                assertTrue("n=$n $light", toCentre < light.radius)
            }
        }
    }

    @Test
    fun fourLightsMatchTheLauncherIcon() {
        assertEquals(
            listOf(IconLight(30f, 30f, 50f), IconLight(78f, 30f, 50f), IconLight(30f, 78f, 50f), IconLight(78f, 78f, 50f)),
            IconLights.layout(4),
        )
    }
}
