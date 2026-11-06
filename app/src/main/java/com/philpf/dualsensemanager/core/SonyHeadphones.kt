package com.philpf.dualsensemanager.core

/*
 * Sony's headphone control protocol, version 1: the one its own app (Headphones Connect, now
 * Sound Connect) speaks over a Bluetooth RFCOMM channel with headphones of the WH-1000XM4 and
 * WH-XB910N generation. Written from the description of the protocol in Gadgetbridge, an
 * open-source app (no code taken from it); its test vectors are this file's tests.
 */

/** One message: its [type] (acknowledgement or command), [sequence] number and [payload] bytes (0..255). */
data class SonyMessage(val type: Int, val sequence: Int, val payload: List<Int>)

/**
 * A message on the wire: 0x3E, then type, sequence, payload length (4 bytes, big-endian),
 * payload and checksum, then 0x3C. The checksum is the sum of the bytes between the markers,
 * modulo 256. Between the markers, the bytes 0x3C, 0x3D and 0x3E are sent as 0x3D followed by
 * the byte with bit 4 cleared.
 */
object SonyFrames {
    const val ACK = 0x01
    const val COMMAND = 0x0C
    const val COMMAND_2 = 0x0E
    const val START = 0x3E
    const val END = 0x3C
    private const val ESCAPE = 0x3D
    private const val ESCAPED_BIT = 0x10

    fun encode(type: Int, sequence: Int, payload: List<Int>): ByteArray {
        val size = payload.size
        val body = listOf(type, sequence, size ushr 24, size ushr 16, size ushr 8, size).map { it and 0xFF } + payload.map { it and 0xFF }
        val inner = body + (body.sum() and 0xFF)
        val out = ArrayList<Int>(inner.size + 4)
        out += START
        for (b in inner) {
            if (b == START || b == END || b == ESCAPE) {
                out += ESCAPE
                out += b and ESCAPED_BIT.inv()
            } else {
                out += b
            }
        }
        out += END
        return ByteArray(out.size) { out[it].toByte() }
    }

    /** The message in [frame] (from 0x3E to 0x3C), or null when it is malformed. */
    fun decode(frame: ByteArray): SonyMessage? {
        val raw = frame.map { it.toInt() and 0xFF }
        if (raw.size < 2 || raw.first() != START || raw.last() != END) return null
        val inner = ArrayList<Int>(raw.size)
        var i = 1
        while (i < raw.size - 1) {
            val b = raw[i]
            if (b == ESCAPE) {
                if (i + 1 >= raw.size - 1) return null
                inner += raw[i + 1] or ESCAPED_BIT
                i += 2
            } else {
                inner += b
                i++
            }
        }
        if (inner.size < 7) return null
        val checksum = inner.last()
        val body = inner.subList(0, inner.size - 1)
        if ((body.sum() and 0xFF) != checksum) return null
        val size = (body[2] shl 24) or (body[3] shl 16) or (body[4] shl 8) or body[5]
        if (size != body.size - 6) return null
        return SonyMessage(body[0], body[1], body.subList(6, body.size).toList())
    }
}

/** Collects the bytes read from the headphones into frames (0x3E ... 0x3C). */
class SonyFrameReader {
    private val buffer = ArrayList<Byte>()
    private var inFrame = false

    /** Returns a complete frame when [byte] ends one. */
    fun feed(byte: Byte): ByteArray? {
        val b = byte.toInt() and 0xFF
        if (b == SonyFrames.START) {
            buffer.clear()
            inFrame = true
        }
        if (!inFrame) return null
        buffer += byte
        if (b != SonyFrames.END) return null
        inFrame = false
        return buffer.toByteArray()
    }
}

enum class NoiseMode { NOISE_CANCELLING, AMBIENT, OFF }

/**
 * The noise control: [mode]; the ambient sound level, 0 to 20, kept while noise cancelling so
 * it comes back with ambient sound; whether ambient sound focuses on voices; and whether the
 * headphones use the form of the message with a wind-noise setting (as the WH-1000XM4 does).
 */
data class NoiseControl(
    val mode: NoiseMode,
    val ambientLevel: Int,
    val voiceFocus: Boolean,
    val windVariant: Boolean = false,
)

enum class SonyEqualizer(val code: Int, val label: String) {
    OFF(0x00, "Off"),
    BASS_BOOST(0x16, "Bass boost"),
    TREBLE_BOOST(0x15, "Treble boost"),
    VOCAL(0x14, "Vocal"),
    BRIGHT(0x10, "Bright"),
    EXCITED(0x11, "Excited"),
    MELLOW(0x12, "Mellow"),
    RELAXED(0x13, "Relaxed"),
    SPEECH(0x17, "Speech"),
    MANUAL(0xA0, "Manual"),
    CUSTOM_1(0xA1, "Custom 1"),
    CUSTOM_2(0xA2, "Custom 2"),
    ;

    /** Presets that can be chosen as they are (the others need their bands set in Sony's app). */
    val selectable: Boolean get() = code < 0xA0

    companion object {
        fun fromCode(code: Int): SonyEqualizer? = entries.firstOrNull { it.code == code }
    }
}

/** What the headphones said. */
sealed interface SonyEvent {
    /** The answer to the first request: the protocol version they speak (1 or 2). */
    data class Ready(val version: Int) : SonyEvent

    data class Battery(val level: Int, val charging: Boolean) : SonyEvent

    data class Noise(val control: NoiseControl) : SonyEvent

    data class Equalizer(val preset: SonyEqualizer) : SonyEvent
}

/** The payloads of version 1, and what the headphones send back. */
object SonyCommands {
    val INIT = listOf(0x00, 0x00)
    val BATTERY = listOf(0x10, 0x00)
    val NOISE = listOf(0x66, 0x02)
    val EQUALIZER = listOf(0x56, 0x01)
    val POWER_OFF = listOf(0x22, 0x00, 0x01)

    fun setNoise(control: NoiseControl): List<Int> {
        val modeByte = when (control.mode) {
            NoiseMode.NOISE_CANCELLING -> if (control.windVariant) 0x02 else 0x01
            else -> 0x00
        }
        return listOf(
            0x68,
            0x02,
            if (control.mode == NoiseMode.OFF) 0x00 else 0x11,
            if (control.windVariant) 0x02 else 0x00,
            modeByte,
            0x01,
            if (control.voiceFocus) 0x01 else 0x00,
            if (control.mode == NoiseMode.NOISE_CANCELLING) 0 else control.ambientLevel.coerceIn(0, 20),
        )
    }

    fun setEqualizer(preset: SonyEqualizer): List<Int> = listOf(0x58, 0x01, preset.code, 0x00)

    fun parse(payload: List<Int>): SonyEvent? {
        if (payload.isEmpty()) return null
        return when (payload[0]) {
            0x01 -> when (payload.size) {
                4 -> SonyEvent.Ready(1)
                8 -> SonyEvent.Ready(2)
                else -> null
            }
            // A single battery (headphones), not earbuds (0x01) or their case (0x02).
            0x11, 0x13 -> if (payload.size >= 4 && payload[1] == 0x00) SonyEvent.Battery(payload[2].coerceIn(0, 100), payload[3] == 1) else null
            0x67, 0x69 -> parseNoise(payload)
            0x57, 0x59 -> if (payload.size == 10) SonyEqualizer.fromCode(payload[2])?.let(SonyEvent::Equalizer) else null
            else -> null
        }
    }

    private fun parseNoise(p: List<Int>): SonyEvent.Noise? {
        if (p.size != 8) return null
        val wind = p[3] == 0x02
        val mode = when {
            p[2] == 0x00 -> NoiseMode.OFF
            p[2] != 0x01 && p[2] != 0x11 -> return null
            p[4] == 0x00 -> NoiseMode.AMBIENT
            // In the wind form, 1 is wind-noise reduction, a kind of noise cancelling.
            p[4] == 0x01 || (wind && p[4] == 0x02) -> NoiseMode.NOISE_CANCELLING
            else -> return null
        }
        if (p[6] > 1 || p[7] > 20) return null
        return SonyEvent.Noise(NoiseControl(mode, p[7], p[6] == 1, wind))
    }
}

/**
 * One conversation with a pair of headphones: the sequence number (0 or 1, the next one given
 * by each acknowledgement), acknowledging what they send, and one request at a time, each sent
 * when the previous one has been acknowledged. The first request asks which version they speak;
 * with version 1, the battery, noise control and equalizer are asked for next.
 */
class SonySession {
    private var sequence = 0
    private val queue = ArrayDeque<List<Int>>()
    private var inFlight: List<Int>? = null
    private var tries = 0

    var version: Int? = null
        private set

    /** The frames to send first. */
    fun start(): List<ByteArray> = send(SonyCommands.INIT)

    /** Queues [payload]; returns the frames to write now (none while another request awaits its acknowledgement). */
    fun send(payload: List<Int>): List<ByteArray> {
        queue.addLast(payload)
        return pump()
    }

    /** A message arrived: the frames to write back, and what it said. */
    fun receive(message: SonyMessage): Pair<List<ByteArray>, SonyEvent?> {
        if (message.type == SonyFrames.ACK) {
            if (message.sequence == sequence) return emptyList<ByteArray>() to null
            sequence = message.sequence
            inFlight = null
            return pump() to null
        }
        if (message.type != SonyFrames.COMMAND && message.type != SonyFrames.COMMAND_2) return emptyList<ByteArray>() to null
        val out = mutableListOf(SonyFrames.encode(SonyFrames.ACK, (1 - message.sequence) and 0xFF, emptyList()))
        val event = SonyCommands.parse(message.payload)
        if (event is SonyEvent.Ready && version == null) {
            version = event.version
            // Sent once the first request is acknowledged, with the sequence number that gives.
            if (event.version == 1) {
                queue.addLast(SonyCommands.BATTERY)
                queue.addLast(SonyCommands.NOISE)
                queue.addLast(SonyCommands.EQUALIZER)
            }
            out += pump()
        }
        return out to event
    }

    /**
     * No acknowledgement came in time: the request is sent again (the headphones sometimes
     * ignore the first one), and after three tries the next one goes.
     */
    fun timeout(): List<ByteArray> {
        val pending = inFlight ?: return emptyList()
        if (++tries < MAX_TRIES) return listOf(SonyFrames.encode(SonyFrames.COMMAND, sequence, pending))
        inFlight = null
        return pump()
    }

    /** Whether a request awaits its acknowledgement. */
    val waiting: Boolean get() = inFlight != null

    /** Asks for the version again, when the headphones acknowledged the first request without answering. */
    fun retryInit(): List<ByteArray> = if (version == null && SonyCommands.INIT !in queue) send(SonyCommands.INIT) else emptyList()

    private fun pump(): List<ByteArray> {
        if (inFlight != null) return emptyList()
        val next = queue.removeFirstOrNull() ?: return emptyList()
        inFlight = next
        tries = 0
        return listOf(SonyFrames.encode(SonyFrames.COMMAND, sequence, next))
    }

    private companion object {
        const val MAX_TRIES = 3
    }
}

/** A pair of Sony headphones paired with the phone, and what is known about them now. */
data class Headphones(
    /** Their Bluetooth address, which identifies them. */
    val address: String,
    val name: String,
    /** Connected to the phone. */
    val connected: Boolean,
    /** The app is talking to them, so the controls below are theirs. */
    val ready: Boolean = false,
    /** False when they speak a version of the protocol this app does not know. */
    val supported: Boolean = true,
    val battery: Int? = null,
    val charging: Boolean = false,
    val noise: NoiseControl? = null,
    val equalizer: SonyEqualizer? = null,
)

/** How to recognise Sony headphones among paired devices. */
object SonyHeadphonesIds {
    /** The RFCOMM services of protocol versions 1 and 2. */
    const val SERVICE_V1 = "96CC203E-5068-46AD-B32D-E316F5E069BA"
    const val SERVICE_V2 = "956C7B26-D49A-4BA8-B03F-B17D393CB6E2"

    // Sony's names for headphones and earbuds: WH-XB910N, WF-1000XM4, WI-C100, MDR-..., LinkBuds.
    private val NAME = Regex("^(WH|WF|WI|MDR)-[A-Z0-9]+.*|^LinkBuds.*", RegexOption.IGNORE_CASE)

    /** Whether a paired device is Sony headphones: it offers one of the services, or has one of their names. */
    fun isSony(name: String?, services: Collection<String>): Boolean =
        services.any { it.equals(SERVICE_V1, ignoreCase = true) || it.equals(SERVICE_V2, ignoreCase = true) } ||
            (name != null && NAME.matches(name.trim()) && !name.startsWith("LE_"))
}
