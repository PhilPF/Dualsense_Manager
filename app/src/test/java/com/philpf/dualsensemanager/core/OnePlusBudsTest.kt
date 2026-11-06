package com.philpf.dualsensemanager.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnePlusBudsTest {
    private fun hex(s: String): List<Int> = s.split(':').map { it.toInt(16) }
    private fun bytes(s: String): ByteArray = hex(s).map { it.toByte() }.toByteArray()
    private fun decode(list: List<ByteArray>) = list.flatMap { OpoFrameReader().feed(it) }
    private fun reply(command: Int, sequence: Int, payload: String) = OpoFrame(command or OpoFrame.REPLY, sequence, hex(payload))
    private fun push(payload: String) = OpoFrame(OpoCommands.PUSH, OpoFrame.PUSH_SEQUENCE, hex(payload))

    @Test
    fun capturedFramesOnTheWire() {
        // QuickBuds' captures: the handshake, and setting the noise control off.
        assertArrayEquals(bytes("aa:07:00:00:00:01:01:00:00"), OnePlusSession().start().single())
        assertArrayEquals(bytes("aa:0a:00:00:04:04:06:03:00:01:01:01"), OpoFrame(OpoCommands.SET_NOISE, 6, OpoCommands.setNoise(0x01)).encode())
    }

    @Test
    fun aCapturedPushIsRead() {
        val frames = OpoFrameReader().feed(bytes("aa:0c:00:00:04:02:ff:05:00:03:01:01:08:00"))
        assertEquals(listOf(push("03:01:01:08:00")), frames)
        assertEquals(OpoEvent.Noise(NoiseMode.OFF, null, notified = true), OpoCommands.parse(frames.single()))
    }

    @Test
    fun longLengthsTakeMoreBytes() {
        // 7 + 124 = 131 = 0x83 0x01, and 7 + 250 = 257 = 0x81 0x02, as captured.
        val long = OpoFrame(0x0418, 1, List(124) { it })
        assertEquals(hex("aa:83:01"), long.encode().take(3).map { it.toInt() and 0xFF })
        assertEquals(hex("aa:81:02"), OpoFrame(0x8122, 1, List(250) { 0 }).encode().take(3).map { it.toInt() and 0xFF })
        assertEquals(listOf(long), OpoFrameReader().feed(long.encode()))
    }

    @Test
    fun theReaderSkipsJunkAndWaitsForTheRest() {
        val reader = OpoFrameReader()
        val frame = OpoFrame(OpoCommands.BATTERY or OpoFrame.REPLY, 3, hex("00:01:01:5a"))
        val wire = bytes("13:37") + frame.encode()
        assertEquals(emptyList<OpoFrame>(), reader.feed(wire.copyOfRange(0, 7)))
        assertEquals(listOf(frame), reader.feed(wire.copyOfRange(7, wire.size)))
        // An AA whose header does not hold together is skipped.
        assertEquals(listOf(frame), OpoFrameReader().feed(bytes("aa:07:00:00:00:01:01:05:00") + frame.encode()))
    }

    @Test
    fun batteriesFromRepliesAndPushes() {
        // Left 100 %, right 100 %, case 80 %, then the case charging; a level of 0 is not known.
        assertEquals(
            OpoEvent.Battery(listOf(PartBattery(BudPart.LEFT, 100, false), PartBattery(BudPart.RIGHT, 100, false), PartBattery(BudPart.CASE, 80, false))),
            OpoCommands.parse(reply(OpoCommands.BATTERY, 1, "00:03:01:64:02:64:03:50")),
        )
        assertEquals(OpoEvent.Battery(listOf(PartBattery(BudPart.CASE, 80, true))), OpoCommands.parse(reply(OpoCommands.BATTERY, 1, "00:01:03:d0")))
        assertEquals(OpoEvent.Battery(listOf(PartBattery(BudPart.LEFT, 90, false))), OpoCommands.parse(push("01:02:01:5a:02:00")))
        assertNull(OpoCommands.parse(reply(OpoCommands.BATTERY, 1, "00:02:01:5a")))
    }

    @Test
    fun noiseControlIsSetInOneTableAndReportedInAnother() {
        assertEquals(hex("01:01:04"), OpoCommands.setNoise(OpoNoise.mask(NoiseMode.AMBIENT, null)))
        assertEquals(hex("01:01:02"), OpoCommands.setNoise(OpoNoise.mask(NoiseMode.NOISE_CANCELLING, null)))
        assertEquals(hex("01:01:80"), OpoCommands.setNoise(OpoNoise.mask(NoiseMode.NOISE_CANCELLING, NoiseStrength.SMART)))
        assertEquals(hex("01:01:10"), OpoCommands.setNoise(OpoNoise.mask(NoiseMode.NOISE_CANCELLING, NoiseStrength.EXTREME)))
        // Adaptive, bit 11, takes two bytes.
        assertEquals(hex("01:01:00:08"), OpoCommands.setNoise(0x0800))
        assertEquals(OpoEvent.Noise(NoiseMode.NOISE_CANCELLING, NoiseStrength.EXTREME), OpoCommands.parse(reply(OpoCommands.NOISE, 2, "00:01:01:10:00")))
        assertEquals(OpoEvent.Noise(NoiseMode.AMBIENT, null), OpoCommands.parse(reply(OpoCommands.NOISE, 2, "00:01:01:00:01")))
        assertEquals(OpoEvent.Noise(NoiseMode.NOISE_CANCELLING, NoiseStrength.MILD, notified = true), OpoCommands.parse(push("03:01:01:40:00")))
        // 03 02 is the press-and-hold cycle, 03 04 the strength Smart chose: not the mode.
        assertNull(OpoCommands.parse(push("03:02:01:07:00")))
        assertNull(OpoCommands.parse(push("03:04:01:20:00")))
    }

    @Test
    fun productEventsEqualizerAndRefusals() {
        assertEquals(OpoEvent.Product("062014"), OpoCommands.parse(reply(OpoCommands.PRODUCT, 2, "00:14:20:06")))
        // The Buds 4's offer, as captured: battery, wearing, noise control and gestures are asked for.
        val offered = OpoCommands.parse(reply(OpoCommands.EVENTS, 3, "00:09:01:02:03:04:08:0b:f1:f2:f3")) as OpoEvent.Offered
        assertEquals(hex("04:01:02:03:f1"), OpoCommands.subscribe(offered.events))
        assertNull(OpoCommands.subscribe(hex("04:08")))
        assertEquals(OpoEvent.Equalizer(2), OpoCommands.parse(reply(OpoCommands.EQUALIZER, 4, "00:02")))
        assertEquals(OpoEvent.Equalizer(1, notified = true), OpoCommands.parse(OpoFrame(OpoCommands.EQUALIZER_CHANGED, 0xFF, hex("01"))))
        assertEquals(OpoEvent.Refused(OpoCommands.SET_NOISE, 0x0E), OpoCommands.parse(reply(OpoCommands.SET_NOISE, 5, "0e")))
        assertNull(OpoCommands.parse(reply(OpoCommands.SET_NOISE, 5, "00")))
        assertEquals(OpoEvent.Gesture, OpoCommands.parse(push("f1:01:01:02:01:00")))
    }

    @Test
    fun theBudsRequestsAreAnswered() {
        // 2026-02-26 13:55:01 UTC = 0x69A050B5, the "token" in QuickBuds' first captures.
        assertEquals(OpoFrame(0x8500, 5, hex("00:b5:50:a0:69")), OpoCommands.answer(OpoFrame(OpoCommands.TIME, 5, hex("00:00:00:00")), 0x69A050B5L))
        assertEquals(OpoFrame(0x851D, 9, hex("01")), OpoCommands.answer(OpoFrame(0x051D, 9), 0L))
        assertNull(OpoCommands.answer(push("01:01:01:5a"), 0L))
        // The session answers at once, between its own requests.
        val session = OnePlusSession()
        session.start()
        val (out, event) = session.receive(OpoFrame(OpoCommands.TIME, 7, hex("00:00:00:00")), 0x69A050B5L)
        assertEquals(listOf(OpoFrame(0x8500, 7, hex("00:b5:50:a0:69"))), decode(out))
        assertNull(event)
        assertTrue(session.waiting)
    }

    @Test
    fun aConversation() {
        val session = OnePlusSession()
        assertEquals(listOf(OpoFrame(OpoCommands.HANDSHAKE, 1)), decode(session.start()))
        assertFalse(session.ready)
        assertEquals(listOf(OpoFrame(OpoCommands.PRODUCT, 2)), decode(session.receive(reply(OpoCommands.HANDSHAKE, 1, "00:ff"), 0L).first))
        assertTrue(session.ready)
        assertEquals(listOf(OpoFrame(OpoCommands.EVENTS, 3)), decode(session.receive(reply(OpoCommands.PRODUCT, 2, "00:14:20:06"), 0L).first))
        assertEquals("062014", session.product)
        // The subscription goes before the reads.
        val afterOffer = session.receive(reply(OpoCommands.EVENTS, 3, "00:03:01:02:03"), 0L).first
        assertEquals(listOf(OpoFrame(OpoCommands.SUBSCRIBE, 4, hex("03:01:02:03"))), decode(afterOffer))
        assertEquals(listOf(OpoFrame(OpoCommands.NOISE, 5, hex("01:01"))), decode(session.receive(reply(OpoCommands.SUBSCRIBE, 4, "01:03:01:00:02:00:03:00"), 0L).first))
        // A late reply to an earlier try is read, but does not answer the one in flight.
        assertEquals(emptyList<OpoFrame>(), decode(session.receive(reply(OpoCommands.NOISE, 2, "00:01:01:08:00"), 0L).first))
        assertEquals(listOf(OpoFrame(OpoCommands.BATTERY, 6)), decode(session.receive(reply(OpoCommands.NOISE, 5, "00:01:01:08:00"), 0L).first))
        assertEquals(listOf(OpoFrame(OpoCommands.WEAR, 7)), decode(session.receive(reply(OpoCommands.BATTERY, 6, "00:01:01:5a"), 0L).first))
        assertEquals(listOf(OpoFrame(OpoCommands.EQUALIZER, 8)), decode(session.receive(reply(OpoCommands.WEAR, 7, "00:02:01:03:02:03"), 0L).first))
        assertEquals(emptyList<OpoFrame>(), decode(session.receive(reply(OpoCommands.EQUALIZER, 8, "00:00"), 0L).first))
        assertFalse(session.waiting)
        // A change is read back.
        assertEquals(listOf(OpoFrame(OpoCommands.SET_EQUALIZER, 9, hex("02"))), decode(session.setEqualizer(2)))
        assertEquals(listOf(OpoFrame(OpoCommands.EQUALIZER, 10)), decode(session.receive(reply(OpoCommands.SET_EQUALIZER, 9, "00"), 0L).first))
    }

    @Test
    fun unansweredRequestsAreSentAgainThenSkipped() {
        val session = OnePlusSession()
        session.start()
        assertEquals(listOf(OpoFrame(OpoCommands.HANDSHAKE, 1)), decode(session.timeout()))
        session.timeout()
        // Some realme buds never answer the handshake: the product id goes next.
        assertEquals(listOf(OpoFrame(OpoCommands.PRODUCT, 2)), decode(session.timeout()))
        assertEquals(emptyList<ByteArray>(), OnePlusSession().timeout())
    }

    @Test
    fun whereTheBudsAre() {
        fun wear(part: BudPart, state: Int) = PartWear(part, state and 1 != 0, state and 2 != 0, state and 4 != 0)
        // QuickBuds' states: 0 in the closed case, 1 and 5 out of an ear, 3 and 7 in one, 4 in the open case.
        val both = OpoCommands.parse(reply(OpoCommands.WEAR, 3, "00:03:01:03:02:03:03:04")) as OpoEvent.Wearing
        assertEquals(listOf(wear(BudPart.LEFT, 3), wear(BudPart.RIGHT, 3), wear(BudPart.CASE, 4)), both.parts)
        // A reply may come without its status byte.
        val one = OpoCommands.parse(reply(OpoCommands.WEAR, 3, "02:01:07:02:04")) as OpoEvent.Wearing
        assertEquals(listOf(wear(BudPart.LEFT, 7), wear(BudPart.RIGHT, 4)), one.parts)
        assertEquals(OpoEvent.Wearing(listOf(wear(BudPart.LEFT, 0), wear(BudPart.RIGHT, 0), wear(BudPart.CASE, 0))), OpoCommands.parse(push("02:03:01:00:02:00:03:00")))
        assertNull(OpoCommands.parse(push("02:02:01:03:05:03")))
        // In use: the buds out of the case, the case while open.
        val pair = Headphones("a", "Buds", connected = true, kind = HeadphonesKind.ONEPLUS)
        assertEquals(setOf(BudPart.LEFT, BudPart.RIGHT, BudPart.CASE), pair.copy(wear = both.parts).inUse)
        assertEquals(setOf(BudPart.LEFT), pair.copy(wear = one.parts).inUse)
        assertEquals(setOf(BudPart.CASE), pair.copy(wear = listOf(wear(BudPart.LEFT, 4), wear(BudPart.RIGHT, 4), wear(BudPart.CASE, 4))).inUse)
        assertNull(pair.copy(wear = listOf(wear(BudPart.LEFT, 0), wear(BudPart.RIGHT, 0))).inUse)
        // Their batteries are the ones shown; all known ones when none is in use.
        val batteries = listOf(PartBattery(BudPart.LEFT, 80, false), PartBattery(BudPart.RIGHT, 70, false), PartBattery(BudPart.CASE, 40, true))
        assertEquals(batteries.take(1), pair.copy(parts = batteries, wear = one.parts).shownBatteries)
        assertEquals(batteries, pair.copy(parts = batteries).shownBatteries)
    }

    @Test
    fun namesAndPresets() {
        assertTrue(OnePlusBuds.isOnePlus("OnePlus Buds Pro 2"))
        assertTrue(OnePlusBuds.isOnePlus("OnePlus Nord Buds 3 Pro"))
        assertTrue(OnePlusBuds.isOnePlus("Buds Pro 2"))
        assertFalse(OnePlusBuds.isOnePlus("realme Buds Air 7"))
        assertFalse(OnePlusBuds.isOnePlus("WH-XB910N"))
        assertEquals(listOf("Balanced", "Bold bass", "Serenade", "Clear"), OnePlusBuds.presets("062014").map { it.label })
        assertEquals(EqPreset(5, "Custom", selectable = false), OnePlusBuds.preset(5, OnePlusBuds.presets("062014")))
    }
}
