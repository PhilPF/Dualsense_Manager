package com.philpf.dualsensemanager.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SonyHeadphonesTest {
    private fun hex(s: String): List<Int> = s.split(':').map { it.toInt(16) }
    private fun bytes(s: String): ByteArray = hex(s).map { it.toByte() }.toByteArray()

    @Test
    fun theFirstRequestOnTheWire() {
        // Type 0C, sequence 0, length 2, payload 00 00, checksum 0C + 02 = 0E.
        assertArrayEquals(bytes("3e:0c:00:00:00:00:02:00:00:0e:3c"), SonyFrames.encode(SonyFrames.COMMAND, 0, SonyCommands.INIT))
    }

    @Test
    fun markersInsideAMessageAreEscaped() {
        val payload = listOf(0x3c, 0x3d, 0x3e, 0x10)
        val frame = SonyFrames.encode(SonyFrames.COMMAND, 1, payload)
        // 3C -> 3D 2C, 3D -> 3D 2D, 3E -> 3D 2E; checksum 0C + 01 + 04 + 3C + 3D + 3E + 10 = D8.
        assertArrayEquals(bytes("3e:0c:01:00:00:00:04:3d:2c:3d:2d:3d:2e:10:d8:3c"), frame)
        assertEquals(SonyMessage(SonyFrames.COMMAND, 1, payload), SonyFrames.decode(frame))
    }

    @Test
    fun badChecksumsAndLengthsAreRejected() {
        assertNull(SonyFrames.decode(bytes("3e:0c:00:00:00:00:02:00:00:0f:3c")))
        assertNull(SonyFrames.decode(bytes("3e:0c:00:00:00:00:03:00:00:0f:3c")))
        assertNull(SonyFrames.decode(bytes("0c:00:3c")))
    }

    @Test
    fun theReaderFindsFramesInAStream() {
        val reader = SonyFrameReader()
        val stream = bytes("ff:3e:01:01:00:00:00:00:02:3c:3e:0c")
        val frames = stream.toList().mapNotNull(reader::feed)
        assertEquals(listOf(SonyMessage(SonyFrames.ACK, 1, emptyList())), frames.map(SonyFrames::decode))
    }

    @Test
    fun noiseControlPayloadsMatchGadgetbridgesTestVectors() {
        fun set(mode: NoiseMode, voice: Boolean, level: Int, wind: Boolean = false) =
            SonyCommands.setNoise(NoiseControl(mode, level, voice, wind))
        assertEquals(hex("68:02:00:00:00:01:00:05"), set(NoiseMode.OFF, false, 5))
        assertEquals(hex("68:02:00:00:00:01:01:0a"), set(NoiseMode.OFF, true, 10))
        assertEquals(hex("68:02:11:00:00:01:00:0f"), set(NoiseMode.AMBIENT, false, 15))
        assertEquals(hex("68:02:11:00:00:01:01:0a"), set(NoiseMode.AMBIENT, true, 10))
        assertEquals(hex("68:02:11:00:01:01:01:00"), set(NoiseMode.NOISE_CANCELLING, true, 15))
        assertEquals(hex("68:02:11:02:02:01:00:00"), set(NoiseMode.NOISE_CANCELLING, false, 0, wind = true))
        assertEquals(hex("68:02:11:02:00:01:01:0f"), set(NoiseMode.AMBIENT, true, 15, wind = true))
        assertEquals(hex("10:00"), SonyCommands.BATTERY)
        assertEquals(hex("58:01:16:00"), SonyCommands.setEqualizer(SonyEqualizer.BASS_BOOST))
        assertEquals(hex("58:01:00:00"), SonyCommands.setEqualizer(SonyEqualizer.OFF))
    }

    @Test
    fun repliesAreRead() {
        assertEquals(SonyEvent.Ready(1), SonyCommands.parse(hex("01:00:00:00")))
        assertEquals(SonyEvent.Ready(2), SonyCommands.parse(hex("01:00:00:00:00:00:00:00")))
        assertEquals(SonyEvent.Battery(70, charging = true), SonyCommands.parse(hex("13:00:46:01")))
        assertEquals(
            SonyEvent.Noise(NoiseControl(NoiseMode.AMBIENT, 12, voiceFocus = true)),
            SonyCommands.parse(hex("67:02:01:00:00:01:01:0c")),
        )
        assertEquals(
            SonyEvent.Noise(NoiseControl(NoiseMode.NOISE_CANCELLING, 0, voiceFocus = false, windVariant = true)),
            SonyCommands.parse(hex("69:02:01:02:02:01:00:00")),
        )
        assertEquals(SonyEvent.Noise(NoiseControl(NoiseMode.OFF, 5, false)), SonyCommands.parse(hex("69:02:00:00:00:01:00:05")))
        assertEquals(SonyEvent.Equalizer(SonyEqualizer.BASS_BOOST), SonyCommands.parse(hex("57:01:16:06:0a:0a:0a:0a:0a:0a")))
        assertNull(SonyCommands.parse(hex("67:02:01:00:00:01:01:30")))
    }

    @Test
    fun aConversation() {
        val session = SonySession()
        assertEquals(listOf(SonyMessage(SonyFrames.COMMAND, 0, SonyCommands.INIT)), session.start().map(SonyFrames::decode))
        // The headphones acknowledge with the next sequence number, then answer version 1.
        val (afterAck, _) = session.receive(SonyMessage(SonyFrames.ACK, 1, emptyList()))
        assertEquals(emptyList<ByteArray>(), afterAck)
        val (afterReady, ready) = session.receive(SonyMessage(SonyFrames.COMMAND, 0, hex("01:00:00:00")))
        assertEquals(SonyEvent.Ready(1), ready)
        // Their message is acknowledged with 1 - its sequence, and the battery is asked for.
        assertEquals(
            listOf(SonyMessage(SonyFrames.ACK, 1, emptyList()), SonyMessage(SonyFrames.COMMAND, 1, SonyCommands.BATTERY)),
            afterReady.map(SonyFrames::decode),
        )
        // Nothing more until that is acknowledged.
        assertEquals(emptyList<ByteArray>(), session.send(SonyCommands.POWER_OFF))
        val (next, _) = session.receive(SonyMessage(SonyFrames.ACK, 0, emptyList()))
        assertEquals(listOf(SonyMessage(SonyFrames.COMMAND, 0, SonyCommands.NOISE)), next.map(SonyFrames::decode))
        // A repeated acknowledgement changes nothing.
        assertEquals(emptyList<ByteArray>(), session.receive(SonyMessage(SonyFrames.ACK, 0, emptyList())).first)
    }

    @Test
    fun unansweredRequestsAreSentAgainThenSkipped() {
        val session = SonySession()
        session.start()
        val again = session.timeout()
        assertEquals(listOf(SonyMessage(SonyFrames.COMMAND, 0, SonyCommands.INIT)), again.map(SonyFrames::decode))
        session.send(SonyCommands.BATTERY)
        session.timeout()
        // Third try gives up on it and sends the next.
        assertEquals(listOf(SonyMessage(SonyFrames.COMMAND, 0, SonyCommands.BATTERY)), session.timeout().map(SonyFrames::decode))
    }
}
