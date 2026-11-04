package com.philpf.dualsensemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CodecAndColorTest {

    private fun profile(id: String, number: Int, lastSeen: Long = 0L, playerId: String? = null) = ControllerProfile(
        id = id,
        number = number,
        kind = ControllerKind.DUALSENSE,
        color = 0x0030FF,
        playerId = playerId,
        lastSeen = lastSeen,
    )

    @Test
    fun profilesRoundTripKeepEveryField() {
        val profiles = listOf(
            ControllerProfile("a1b2", 1, ControllerKind.DUALSENSE, 0x0030FF, false, "p-1", 1_700_000_000_000L, 55),
            ControllerProfile("c3d4", 2, ControllerKind.DUALSENSE_EDGE, 0xFF0A14, true, null, 0L, -1),
        )
        assertEquals(profiles, ProfileCodec.decode(ProfileCodec.encode(profiles)))
    }

    @Test
    fun firstReleaseDataKeepsNumbersAndColours() {
        val v1 = "dsm1\nabc\t2\tDUALSENSE_EDGE\tAna\tff0a14\t1\t123\t55"
        assertEquals(
            listOf(ControllerProfile("abc", 2, ControllerKind.DUALSENSE_EDGE, 0xFF0A14, true, null, 123L, 55)),
            ProfileCodec.decode(v1),
        )
    }

    @Test
    fun playersRoundTripWithAwkwardNames() {
        val players = listOf(
            Player("p1", "Ana", 0x00E5FF),
            Player("p2", "tab\there\\back\nslash\r!", 0xFFFFFF),
            Player("p3", "", 0),
        )
        assertEquals(players, PlayerCodec.decode(PlayerCodec.encode(players)))
    }

    @Test
    fun badInputIsSkippedNotFatal() {
        assertTrue(ProfileCodec.decode(null).isEmpty())
        assertTrue(ProfileCodec.decode("").isEmpty())
        assertTrue(ProfileCodec.decode("dsm1\nold\tformat").isEmpty())
        assertTrue(PlayerCodec.decode("something else").isEmpty())
        val good = ProfileCodec.encode(listOf(profile("ok", 1)))
        val text = good + "\nbroken line\n\tx\tnot-a-number\t0\t0\t\t0\t0"
        assertEquals(listOf("ok"), ProfileCodec.decode(text).map { it.id })
    }

    @Test
    fun hsvRoundTripsEveryPaletteColour() {
        for (c in LedPalette.colors) {
            assertEquals(c.name, c.rgb, ColorMath.toRgb(ColorMath.toHsv(c.rgb)))
        }
    }

    @Test
    fun hsvRoundTripIsExactOnAGridOfColours() {
        for (r in 0..255 step 17) for (g in 0..255 step 15) for (b in 0..255 step 51) {
            val rgb = (r shl 16) or (g shl 8) or b
            assertEquals(ColorMath.hex(rgb), rgb, ColorMath.toRgb(ColorMath.toHsv(rgb)))
        }
    }

    @Test
    fun primaryHues() {
        assertEquals(0f, ColorMath.toHsv(0xFF0000).hue, 1e-4f)
        assertEquals(120f, ColorMath.toHsv(0x00FF00).hue, 1e-4f)
        assertEquals(240f, ColorMath.toHsv(0x0000FF).hue, 1e-4f)
        assertEquals(0xFF0000, ColorMath.toRgb(Hsv(360f, 1f, 1f)))
        val grey = ColorMath.toHsv(0x808080)
        assertEquals(0f, grey.saturation, 0f)
        assertTrue(abs(grey.value - 128f / 255f) < 1e-6f)
    }

    @Test
    fun hexIsUppercaseAndPadded() {
        assertEquals("#0030FF", ColorMath.hex(0x0030FF))
        assertEquals("#000000", ColorMath.hex(0))
    }

    @Test
    fun viewsPutConnectedFirstByNumberThenMostRecent() {
        val p1 = profile("one", 1, lastSeen = 10)
        val p2 = profile("two", 2, lastSeen = 30)
        val p3 = profile("three", 3, lastSeen = 20, playerId = "ana")
        val p4 = profile("four", 4, lastSeen = 50)
        val live = listOf(
            LiveController("three", 7, ControllerKind.DUALSENSE, Transport.USB, null, true),
            LiveController("one", 5, ControllerKind.DUALSENSE, Transport.BLUETOOTH, null, true),
        )
        val ana = Player("ana", "Ana", 0x00E5FF)
        val views = ControllerViews.build(listOf(p1, p2, p3, p4), mapOf("ana" to ana), live)
        assertEquals(listOf("one", "three", "four", "two"), views.map { it.id })
        assertEquals(listOf(true, true, false, false), views.map { it.isConnected })

        val three = views[1]
        assertEquals("Ana", three.title)
        assertEquals("DualSense 3", three.subtitle)
        assertEquals(0x00E5FF, three.color)
        assertEquals("DualSense 1", views[0].title)
        assertNull(views[0].subtitle)
        assertEquals(0x0030FF, views[0].color)
    }

    @Test
    fun aMissingPlayerFallsBackToTheController() {
        val view = ControllerViews.build(listOf(profile("x", 2, playerId = "gone")), emptyMap(), emptyList()).single()
        assertEquals("DualSense 2", view.title)
        assertNull(view.player)
    }

    @Test
    fun relativeTimes() {
        val now = 1_000_000_000_000L
        assertEquals("just now", RelativeTime.since(now, now - 20_000))
        assertEquals("5 min ago", RelativeTime.since(now, now - 5 * 60_000))
        assertEquals("3 h ago", RelativeTime.since(now, now - 3 * 3_600_000))
        assertEquals("yesterday", RelativeTime.since(now, now - 30 * 3_600_000L))
        assertEquals("4 days ago", RelativeTime.since(now, now - 4 * 86_400_000L))
        assertEquals("a while ago", RelativeTime.since(now, 0))
    }
}
