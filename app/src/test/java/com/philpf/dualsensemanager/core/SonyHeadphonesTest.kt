package com.philpf.dualsensemanager.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SonyHeadphonesTest {
    private fun hex(s: String): List<Int> = s.split(':').map { it.toInt(16) }
    private fun bytes(s: String): ByteArray = hex(s).map { it.toByte() }.toByteArray()
    private fun frames(list: List<ByteArray>) = list.map(SonyFrames::decode)
    private fun command(sequence: Int, payload: List<Int>) = SonyMessage(SonyFrames.COMMAND, sequence, payload)
    private fun ack(sequence: Int) = SonyMessage(SonyFrames.ACK, sequence, emptyList())

    private val v1Wind = NoiseForm(1, 0x02, windByte = true)
    private val v2 = NoiseForm(2, 0x17)

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
        assertEquals(command(1, payload), SonyFrames.decode(frame))
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
        assertEquals(listOf(ack(1)), stream.toList().mapNotNull(reader::feed).map(SonyFrames::decode))
    }

    @Test
    fun versionOneNoiseControlMatchesGadgetbridgesVectors() {
        fun set(mode: NoiseMode, voice: Boolean, level: Int, form: NoiseForm = NoiseForm()) =
            SonyCommands.setNoise(NoiseControl(mode, level, voice, form))
        assertEquals(hex("68:02:00:00:00:01:00:05"), set(NoiseMode.OFF, false, 5))
        assertEquals(hex("68:02:00:00:00:01:01:0a"), set(NoiseMode.OFF, true, 10))
        assertEquals(hex("68:02:11:00:00:01:00:0f"), set(NoiseMode.AMBIENT, false, 15))
        assertEquals(hex("68:02:11:00:00:01:01:0a"), set(NoiseMode.AMBIENT, true, 10))
        assertEquals(hex("68:02:11:00:01:01:01:00"), set(NoiseMode.NOISE_CANCELLING, true, 15))
        assertEquals(hex("68:02:11:02:02:01:00:00"), set(NoiseMode.NOISE_CANCELLING, false, 0, v1Wind))
        assertEquals(hex("68:02:11:02:00:01:01:0f"), set(NoiseMode.AMBIENT, true, 15, v1Wind))
        assertEquals(hex("10:00"), SonyCommands.battery(1))
        assertEquals(hex("58:01:16:00"), SonyCommands.setEqualizer(1, SonyEqualizer.BASS_BOOST))
        assertEquals(hex("58:01:00:00"), SonyCommands.setEqualizer(1, SonyEqualizer.OFF))
        assertEquals(hex("22:00:01"), SonyCommands.powerOff(1))
    }

    @Test
    fun versionTwoRequestsMatchGadgetbridgesFrames() {
        assertArrayEquals(bytes("3e:0c:00:00:00:00:02:56:00:64:3c"), SonyFrames.encode(SonyFrames.COMMAND, 0, SonyCommands.equalizer(2)))
        assertArrayEquals(
            bytes("3e:0c:01:00:00:00:04:58:00:16:00:7f:3c"),
            SonyFrames.encode(SonyFrames.COMMAND, 1, SonyCommands.setEqualizer(2, SonyEqualizer.BASS_BOOST)),
        )
        assertEquals(hex("22:00"), SonyCommands.battery(2))
        assertEquals(hex("24:03:01"), SonyCommands.powerOff(2))
        assertEquals(hex("06:00"), SonyCommands.FUNCTIONS)
    }

    @Test
    fun versionTwoNoiseControlInEachForm() {
        fun set(mode: NoiseMode, voice: Boolean, level: Int, form: NoiseForm) = SonyCommands.setNoise(NoiseControl(mode, level, voice, form))
        // Gadgetbridge's (inquiry 17, a byte for noise cancelling or ambient sound).
        assertEquals(hex("68:17:01:01:01:00:14"), set(NoiseMode.AMBIENT, false, 20, v2))
        assertEquals(hex("68:17:01:00:00:00:14"), set(NoiseMode.OFF, false, 20, v2))
        assertEquals(hex("68:17:01:01:00:00:14"), set(NoiseMode.NOISE_CANCELLING, false, 20, v2))
        // Cans' (inquiry 15 with a wind byte; 21 without noise cancelling; 19 with two more bytes).
        assertEquals(hex("68:15:01:01:00:02:01:0a"), set(NoiseMode.NOISE_CANCELLING, true, 10, NoiseForm(2, 0x15, windByte = true)))
        assertEquals(hex("68:21:01:01:00:0c"), set(NoiseMode.AMBIENT, false, 12, NoiseForm(2, 0x21, ncByte = false)))
        assertEquals(hex("68:19:01:01:01:00:0c:05:06"), set(NoiseMode.AMBIENT, false, 12, NoiseForm(2, 0x19, extra = listOf(5, 6))))
    }

    @Test
    fun versionOneRepliesAreRead() {
        assertEquals(SonyEvent.Ready(1), SonyCommands.parse(hex("01:00:00:00")))
        assertEquals(SonyEvent.Battery(70, charging = true), SonyCommands.parse(hex("13:00:46:01")))
        assertEquals(SonyEvent.Noise(NoiseControl(NoiseMode.AMBIENT, 12, voiceFocus = true)), SonyCommands.parse(hex("67:02:01:00:00:01:01:0c")))
        assertEquals(SonyEvent.Noise(NoiseControl(NoiseMode.NOISE_CANCELLING, 0, false, v1Wind), notified = true), SonyCommands.parse(hex("69:02:01:02:02:01:00:00")))
        assertEquals(SonyEvent.Noise(NoiseControl(NoiseMode.OFF, 5, false), notified = true), SonyCommands.parse(hex("69:02:00:00:00:01:00:05")))
        assertEquals(SonyEvent.Equalizer(SonyEqualizer.BASS_BOOST), SonyCommands.parse(hex("57:01:16:06:0a:0a:0a:0a:0a:0a")))
        // A curve of its own (the WH-1000XM4 reports FF) is Manual.
        assertEquals(SonyEvent.Equalizer(SonyEqualizer.MANUAL), SonyCommands.parse(hex("57:01:ff:06:0a:0a:0a:0a:0a:0a")))
        assertNull(SonyCommands.parse(hex("67:02:01:00:00:01:01:30")))
    }

    @Test
    fun versionTwoRepliesAreRead() {
        assertEquals(SonyEvent.Ready(2), SonyCommands.parse(hex("01:00:00:00:00:00:00:00")))
        assertEquals(SonyEvent.Battery(55, charging = false), SonyCommands.parse(hex("25:00:37:00")))
        // A framed equalizer notification from Gadgetbridge's tests.
        val frame = SonyFrames.decode(bytes("3e:0c:01:00:00:00:0a:59:00:16:06:11:0a:0a:0a:0a:0a:cf:3c"))!!
        assertEquals(SonyEvent.Equalizer(SonyEqualizer.BASS_BOOST, notified = true), SonyCommands.parse(frame.payload))
        assertEquals(SonyEvent.Noise(NoiseControl(NoiseMode.AMBIENT, 20, false, v2)), SonyCommands.parse(hex("67:17:01:01:01:00:14")))
        val wind = NoiseForm(2, 0x15, windByte = true)
        assertEquals(SonyEvent.Noise(NoiseControl(NoiseMode.NOISE_CANCELLING, 10, true, wind), notified = true), SonyCommands.parse(hex("69:15:01:01:00:02:01:0a")))
        assertEquals(SonyEvent.Noise(NoiseControl(NoiseMode.NOISE_CANCELLING, 10, false, wind), notified = true), SonyCommands.parse(hex("69:15:01:01:00:03:00:0a")))
        assertEquals(
            SonyEvent.Noise(NoiseControl(NoiseMode.AMBIENT, 12, false, NoiseForm(2, 0x21, ncByte = false))),
            SonyCommands.parse(hex("67:21:01:01:00:0c")),
        )
        assertEquals(
            SonyEvent.Noise(NoiseControl(NoiseMode.NOISE_CANCELLING, 10, false, NoiseForm(2, 0x19, extra = listOf(5, 6)))),
            SonyCommands.parse(hex("67:19:01:01:00:00:0a:05:06")),
        )
        // What is read is written back the same way.
        val read = (SonyCommands.parse(hex("67:17:01:01:01:00:14")) as SonyEvent.Noise).control
        assertEquals(hex("68:17:01:01:01:00:14"), SonyCommands.setNoise(read))
    }

    @Test
    fun theSupportedFunctionsGiveTheNoiseForm() {
        assertEquals(SonyEvent.Functions(0x17), SonyCommands.parse(hex("07:00:03:66:00:68:00:6b:00")))
        assertEquals(SonyEvent.Functions(0x19), SonyCommands.parse(hex("07:00:02:6b:00:6d:00")))
        assertEquals(SonyEvent.Functions(0x21), SonyCommands.parse(hex("07:00:01:66:00")))
        assertEquals(SonyEvent.Functions(null), SonyCommands.parse(hex("07:00:01:10:00")))
    }

    @Test
    fun aVersionOneConversation() {
        val session = SonySession()
        assertEquals(listOf(command(0, SonyCommands.INIT)), frames(session.start()))
        // The headphones acknowledge with the next sequence number, then answer version 1.
        assertEquals(emptyList<ByteArray>(), session.receive(ack(1)).first)
        val (afterReady, ready) = session.receive(command(0, hex("01:00:00:00")))
        assertEquals(SonyEvent.Ready(1), ready)
        // Their message is acknowledged with 1 - its sequence, and the battery is asked for.
        assertEquals(listOf(ack(1), command(1, SonyCommands.battery(1))), frames(afterReady))
        // Nothing more until that is acknowledged.
        assertEquals(emptyList<ByteArray>(), session.send(SonyCommands.powerOff(1)))
        assertEquals(listOf(command(0, SonyCommands.noise(0x02))), frames(session.receive(ack(0)).first))
        // A repeated acknowledgement changes nothing.
        assertEquals(emptyList<ByteArray>(), session.receive(ack(0)).first)
    }

    @Test
    fun aVersionTwoConversation() {
        val session = SonySession()
        session.start()
        session.receive(ack(1))
        val (afterReady, _) = session.receive(command(0, hex("01:00:00:00:00:00:00:00")))
        assertEquals(2, session.version)
        assertEquals(listOf(ack(1), command(1, SonyCommands.FUNCTIONS)), frames(afterReady))
        assertEquals(listOf(command(0, SonyCommands.battery(2))), frames(session.receive(ack(0)).first))
        // The functions come while the battery request awaits its acknowledgement.
        val (afterFunctions, functions) = session.receive(command(1, hex("07:00:02:68:00:6b:00")))
        assertEquals(SonyEvent.Functions(0x17), functions)
        assertEquals(listOf(ack(0)), frames(afterFunctions))
        assertEquals(listOf(command(1, SonyCommands.equalizer(2))), frames(session.receive(ack(1)).first))
        assertEquals(listOf(command(0, SonyCommands.noise(0x17))), frames(session.receive(ack(0)).first))
        assertEquals(emptyList<ByteArray>(), session.receive(ack(1)).first)
        // Read: nothing is guessed.
        val (_, noise) = session.receive(command(0, hex("67:17:01:01:00:00:14")))
        assertEquals(SonyEvent.Noise(NoiseControl(NoiseMode.NOISE_CANCELLING, 20, false, v2)), noise)
        assertEquals(true, session.noiseKnown)
        assertEquals(emptyList<ByteArray>(), session.guessNoise())
    }

    @Test
    fun functionsWithoutANoiseFunctionAskTheDefaultForm() {
        // The WH-XB910N's functions give no noise form: MelodyLink's 17 is asked.
        val session = SonySession()
        session.start()
        session.receive(ack(1))
        session.receive(command(0, hex("01:00:00:00:00:00:00:00")))
        session.receive(command(1, hex("07:00:02:10:00:22:00")))
        session.receive(ack(0)) // functions acknowledged: battery
        session.receive(ack(1)) // battery acknowledged: equalizer
        assertEquals(listOf(command(0, SonyCommands.noise(0x17))), frames(session.receive(ack(0)).first))
    }

    @Test
    fun aFormAnsweredBeforeIsAskedFirst() {
        val session = SonySession(knownNoise = 0x15)
        session.start()
        session.receive(ack(1))
        session.receive(command(0, hex("01:00:00:00:00:00:00:00")))
        session.receive(ack(0)) // functions acknowledged: battery
        session.receive(ack(1)) // battery acknowledged: equalizer
        assertEquals(listOf(command(0, SonyCommands.noise(0x15))), frames(session.receive(ack(0)).first))
    }

    @Test
    fun settingTheNoiseControlReadsItBack() {
        val session = SonySession()
        session.start()
        session.receive(ack(1))
        session.receive(command(0, hex("01:00:00:00:00:00:00:00")))
        listOf(ack(0), ack(1), ack(0), ack(1)).forEach { session.receive(it) } // functions, battery, equalizer
        val control = NoiseControl(NoiseMode.AMBIENT, 12, true, v2)
        assertEquals(listOf(command(1, SonyCommands.setNoise(control))), frames(session.setNoise(control)))
        assertEquals(listOf(command(0, SonyCommands.noise(0x17))), frames(session.receive(ack(0)).first))
    }

    @Test
    fun answersAndNotificationsAreTold() {
        assertEquals(false, (SonyCommands.parse(hex("67:17:01:01:00:00:14")) as SonyEvent.Noise).notified)
        assertEquals(true, (SonyCommands.parse(hex("69:17:01:01:00:00:14")) as SonyEvent.Noise).notified)
        assertEquals(false, (SonyCommands.parse(hex("57:00:16:06")) as SonyEvent.Equalizer).notified)
        assertEquals(true, (SonyCommands.parse(hex("59:00:16:06")) as SonyEvent.Equalizer).notified)
    }

    @Test
    fun withoutTheFunctionsTheFormsAreTriedInTurn() {
        val session = SonySession()
        session.start()
        session.receive(ack(1))
        session.receive(command(0, hex("01:00:00:00:00:00:00:00")))
        // FUNCTIONS in flight: the guesses wait in line, 17 first, then 15.
        assertEquals(emptyList<ByteArray>(), session.guessNoise())
        session.receive(ack(0)) // battery
        session.receive(ack(1)) // equalizer
        assertEquals(listOf(command(0, SonyCommands.noise(0x17))), frames(session.receive(ack(0)).first))
        assertEquals(listOf(command(1, SonyCommands.noise(0x15))), frames(session.receive(ack(1)).first))
        // 15 answers: the other forms are not asked.
        session.receive(command(1, hex("67:15:01:01:00:02:00:0a")))
        assertEquals(emptyList<ByteArray>(), session.receive(ack(0)).first)
    }

    @Test
    fun theFirstRequestIsRepeatedWithEachSequenceNumber() {
        val session = SonySession()
        session.start()
        // Not answered: asked again, with 1, then 0.
        assertEquals(listOf(command(1, SonyCommands.INIT)), frames(session.restart()))
        assertEquals(listOf(command(0, SonyCommands.INIT)), frames(session.restart()))
        assertEquals(listOf(command(1, SonyCommands.INIT)), frames(session.restart()))
        // Answered late, to an earlier one: the last is still acknowledged, and then the battery is asked for.
        assertEquals(listOf(ack(1)), frames(session.receive(command(0, hex("01:00:00:00"))).first))
        assertEquals(listOf(command(0, SonyCommands.battery(1))), frames(session.receive(ack(0)).first))
        assertEquals(emptyList<ByteArray>(), session.restart())
    }

    @Test
    fun longerFirstAnswersAreVersionTwo() {
        assertEquals(SonyEvent.Ready(2), SonyCommands.parse(hex("01:00:03:00:00:00:00:00:00")))
        assertNull(SonyCommands.parse(hex("01:00:00")))
    }

    @Test
    fun messagesOfTheSecondTableAreAcknowledgedButNotRead() {
        val session = SonySession()
        session.start()
        val (out, event) = session.receive(SonyMessage(SonyFrames.COMMAND_2, 1, hex("23:00:46:01")))
        assertEquals(listOf(ack(0)), frames(out))
        assertNull(event)
    }

    @Test
    fun theButtonStepsThroughTheModesTheyHave() {
        val nc = NoiseControl(NoiseMode.NOISE_CANCELLING, 10, false, v2)
        assertEquals(NoiseMode.AMBIENT, nc.nextMode())
        assertEquals(NoiseMode.OFF, nc.copy(mode = NoiseMode.AMBIENT).nextMode())
        assertEquals(NoiseMode.NOISE_CANCELLING, nc.copy(mode = NoiseMode.OFF).nextMode())
        // Without noise cancelling: ambient sound and off.
        val ambient = NoiseControl(NoiseMode.AMBIENT, 10, false, NoiseForm(2, 0x21, ncByte = false))
        assertEquals(listOf(NoiseMode.AMBIENT, NoiseMode.OFF), ambient.modes)
        assertEquals(NoiseMode.OFF, ambient.nextMode())
        assertEquals(NoiseMode.AMBIENT, ambient.copy(mode = NoiseMode.OFF).nextMode())
    }

    @Test
    fun unansweredRequestsAreSentAgainThenSkipped() {
        val session = SonySession()
        session.start()
        assertEquals(listOf(command(0, SonyCommands.INIT)), frames(session.timeout()))
        session.send(SonyCommands.battery(1))
        session.timeout()
        // Third try gives up on it and sends the next.
        assertEquals(listOf(command(0, SonyCommands.battery(1))), frames(session.timeout()))
    }
}
