package com.philpf.dualsensemanager.core

/*
 * Sony's headphone control protocol, the one its own app (Headphones Connect, now Sound
 * Connect) speaks over a Bluetooth RFCOMM channel, in its two versions: version 1 (the
 * WH-1000XM4 and older) and version 2 (newer headphones, such as the WH-XB910N and the
 * WH-1000XM5). Written from two open-source descriptions of it, Gadgetbridge's (Android, AGPL)
 * and Cans' (macOS, MIT), taking no code from either; their test vectors are this file's tests.
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
 * How a pair of headphones writes its noise control, learned from what they send. Version 1:
 * inquiry 0x02, with or without the wind-noise setting (the WH-1000XM4 has it). Version 2: the
 * inquiry byte their supported functions give (0x15, 0x17, 0x19, 0x21 or 0x22), whether there
 * is a byte for noise cancelling or ambient sound (not on headphones without noise cancelling),
 * a byte for wind-noise reduction, and trailing bytes to send back as they came.
 */
data class NoiseForm(
    val version: Int = 1,
    val inquiry: Int = 0x02,
    val ncByte: Boolean = true,
    val windByte: Boolean = false,
    val extra: List<Int> = emptyList(),
)

/**
 * The noise control: [mode]; the ambient sound level, 0 to 20, kept while noise cancelling so
 * it comes back with ambient sound; whether ambient sound focuses on voices; and the [form] the
 * headphones write it in.
 */
data class NoiseControl(
    val mode: NoiseMode,
    val ambientLevel: Int,
    val voiceFocus: Boolean,
    val form: NoiseForm = NoiseForm(),
) {
    /** The modes these headphones have, in the order their button steps through them. */
    val modes: List<NoiseMode>
        get() = if (form.ncByte) NoiseMode.entries else listOf(NoiseMode.AMBIENT, NoiseMode.OFF)

    /** The mode after this one, as their button steps. */
    fun nextMode(): NoiseMode = modes[(modes.indexOf(mode) + 1) % modes.size]
}

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
        /** Any other value (the WH-1000XM4 reports FF) is a curve of its own, shown as Manual. */
        fun fromCode(code: Int): SonyEqualizer = entries.firstOrNull { it.code == code } ?: MANUAL
    }
}

/** What the headphones said. */
sealed interface SonyEvent {
    /** The answer to the first request: the protocol version they speak (1 or 2). */
    data class Ready(val version: Int) : SonyEvent

    /** Version 2: their supported functions, giving the noise control's inquiry byte (null: none). */
    data class Functions(val noiseInquiry: Int?) : SonyEvent

    data class Battery(val level: Int, val charging: Boolean) : SonyEvent

    /** The noise control; [notified] when they changed it themselves (their button), not as an answer. */
    data class Noise(val control: NoiseControl, val notified: Boolean = false) : SonyEvent

    /** The equalizer preset; [notified] when it changed on their side, not as an answer. */
    data class Equalizer(val preset: SonyEqualizer, val notified: Boolean = false) : SonyEvent
}

/** The payloads of both versions, and what the headphones send back. */
object SonyCommands {
    val INIT = listOf(0x00, 0x00)

    /** Version 2: which functions the headphones support. */
    val FUNCTIONS = listOf(0x06, 0x00)

    fun battery(version: Int): List<Int> = if (version == 2) listOf(0x22, 0x00) else listOf(0x10, 0x00)

    fun noise(inquiry: Int): List<Int> = listOf(0x66, inquiry)

    fun equalizer(version: Int): List<Int> = listOf(0x56, equalizerInquiry(version))

    fun setEqualizer(version: Int, preset: SonyEqualizer): List<Int> = listOf(0x58, equalizerInquiry(version), preset.code, 0x00)

    fun powerOff(version: Int): List<Int> = if (version == 2) listOf(0x24, 0x03, 0x01) else listOf(0x22, 0x00, 0x01)

    private fun equalizerInquiry(version: Int) = if (version == 2) 0x00 else 0x01

    /**
     * Version 2: the noise control's inquiry byte for the [functions] a pair of headphones
     * supports, the first of these that it has (as Cans, BudsLink and MelodyLink find it); null
     * when it has none of them, as the WH-XB910N.
     */
    fun noiseInquiry(functions: Set<Int>): Int? = NOISE_FUNCTIONS.firstOrNull { it.first in functions }?.second

    private val NOISE_FUNCTIONS = listOf(0x6D to 0x19, 0x6B to 0x17, 0x68 to 0x15, 0x67 to 0x22, 0x66 to 0x21)

    /**
     * Version 2: the inquiry to ask when the functions give none. MelodyLink's (it works with the
     * WH-XB910N, whose functions give none), and Gadgetbridge's for newer headphones.
     */
    const val DEFAULT_NOISE_INQUIRY = 0x17

    /** Version 2: the other inquiries, tried one at a time when the noise control has not been read. */
    val NOISE_GUESSES = listOf(0x15, 0x13, 0x14, 0x19, 0x16, 0x18, 0x11, 0x12, 0x22, 0x21)

    fun setNoise(control: NoiseControl): List<Int> {
        val form = control.form
        val level = control.ambientLevel.coerceIn(0, 20)
        if (form.version == 1) {
            val modeByte = when (control.mode) {
                NoiseMode.NOISE_CANCELLING -> if (form.windByte) 0x02 else 0x01
                else -> 0x00
            }
            return listOf(
                0x68,
                0x02,
                if (control.mode == NoiseMode.OFF) 0x00 else 0x11,
                if (form.windByte) 0x02 else 0x00,
                modeByte,
                0x01,
                if (control.voiceFocus) 0x01 else 0x00,
                if (control.mode == NoiseMode.NOISE_CANCELLING) 0 else level,
            )
        }
        return buildList {
            add(0x68)
            add(form.inquiry)
            add(0x01)
            add(if (control.mode == NoiseMode.OFF) 0x00 else 0x01)
            if (form.ncByte) add(if (control.mode == NoiseMode.AMBIENT) 0x01 else 0x00)
            if (form.windByte) add(0x02)
            add(if (control.voiceFocus) 0x01 else 0x00)
            add(level.coerceAtLeast(1))
            addAll(form.extra)
        }
    }

    fun parse(payload: List<Int>): SonyEvent? {
        if (payload.isEmpty()) return null
        return when (payload[0]) {
            // 4 bytes (version 1) or 8 (version 2), the only lengths Gadgetbridge has seen.
            0x01 -> when {
                payload.size == 4 -> SonyEvent.Ready(1)
                payload.size > 4 -> SonyEvent.Ready(2)
                else -> null
            }
            0x07 -> parseFunctions(payload)
            // A single battery (headphones), not earbuds or their case: 11 and 13 in version 1,
            // 23 and 25 in version 2.
            0x11, 0x13, 0x23, 0x25 -> if (payload.size >= 4 && payload[1] == 0x00) {
                SonyEvent.Battery(payload[2].coerceIn(0, 100), payload[3] == 1)
            } else {
                null
            }
            0x67, 0x69 -> {
                val control = if (payload.size > 1 && payload[1] == 0x02) parseNoiseV1(payload) else parseNoiseV2(payload)
                control?.let { SonyEvent.Noise(it, notified = payload[0] == 0x69) }
            }
            0x57, 0x59 -> if (payload.size >= 3 && payload[1] <= 0x01) {
                SonyEvent.Equalizer(SonyEqualizer.fromCode(payload[2]), notified = payload[0] == 0x59)
            } else {
                null
            }
            else -> null
        }
    }

    /** 07, then a byte, the number of functions, and each function with a byte after it. */
    private fun parseFunctions(p: List<Int>): SonyEvent.Functions {
        val count = if (p.size > 2) p[2] else 0
        val functions = (0 until count).mapNotNull { p.getOrNull(3 + 2 * it) }.toSet()
        return SonyEvent.Functions(noiseInquiry(functions))
    }

    private fun parseNoiseV1(p: List<Int>): NoiseControl? {
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
        return NoiseControl(mode, p[7], p[6] == 1, NoiseForm(1, 0x02, windByte = wind))
    }

    /**
     * Version 2: 67, inquiry, a byte, on or off, then a byte for noise cancelling (0) or ambient
     * sound (1) unless the headphones have no noise cancelling, a wind byte on some (3 or 5:
     * wind-noise reduction), voice focus, the level, and two more bytes after inquiry 19. Read
     * by length, as the two descriptions differ on which inquiry carries the wind byte.
     */
    private fun parseNoiseV2(p: List<Int>): NoiseControl? {
        if (p.size !in 6..9) return null
        val extra = if (p[1] == 0x19) 2 else 0
        val tail = 2 + extra
        val middle = p.size - 4 - tail
        if (middle !in 0..2) return null
        val mode = when {
            p[3] == 0x00 -> NoiseMode.OFF
            middle == 0 -> NoiseMode.AMBIENT
            middle == 2 && (p[5] == 0x03 || p[5] == 0x05) -> NoiseMode.NOISE_CANCELLING
            p[4] == 0x00 -> NoiseMode.NOISE_CANCELLING
            else -> NoiseMode.AMBIENT
        }
        val focus = p[p.size - tail]
        val level = p[p.size - tail + 1]
        val form = NoiseForm(
            version = 2,
            inquiry = p[1],
            ncByte = middle >= 1,
            windByte = middle == 2,
            extra = p.takeLast(extra),
        )
        return NoiseControl(mode, if (level in 0..20) level else 10, focus == 1, form)
    }
}

/**
 * One conversation with a pair of headphones: the sequence number (0 or 1, the next one given
 * by each acknowledgement), acknowledging what they send, and one request at a time, each sent
 * when the previous one has been acknowledged. The first request asks which version they speak.
 * With version 1, the battery, noise control and equalizer are asked for next. With version 2,
 * the supported functions, battery and equalizer; the noise control is asked for in the form
 * these headphones answered last time ([knownNoise]), the one their functions give or else
 * [SonyCommands.DEFAULT_NOISE_INQUIRY], and, if none answers, in the others in turn
 * ([guessNoise]).
 */
class SonySession(private val knownNoise: Int? = null) {
    private var sequence = 0
    private val queue = ArrayDeque<List<Int>>()
    private var inFlight: List<Int>? = null
    private var tries = 0
    private val noiseAsked = HashSet<Int>()
    private var restarts = 0

    /** Whether the noise control has been read (then no other form is tried). */
    var noiseKnown = false
        private set

    var version: Int? = null
        private set

    /** The frames to send first. */
    fun start(): List<ByteArray> = send(SonyCommands.INIT)

    /**
     * Asks for the version again, as headphones often ignore the first requests for a few
     * seconds after connecting. Sent with each sequence number in turn: whether they take a
     * repeated number for a repeat is not known (Cans sends 0 each time; an acknowledgement
     * would say 1), and asking twice does no harm.
     */
    fun restart(): List<ByteArray> {
        if (version != null) return emptyList()
        restarts++
        sequence = restarts % 2
        queue.clear()
        inFlight = null
        return start()
    }

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
        // The second type has a table of its own (sound and button settings), none of it read here.
        val event = if (message.type == SonyFrames.COMMAND) SonyCommands.parse(message.payload) else null
        when {
            event is SonyEvent.Ready && version == null -> {
                version = event.version
                // Sent once the first request is acknowledged, with the sequence number that gives.
                if (event.version == 1) {
                    queue.addLast(SonyCommands.battery(1))
                    askNoise(0x02)
                    queue.addLast(SonyCommands.equalizer(1))
                } else {
                    queue.addLast(SonyCommands.FUNCTIONS)
                    queue.addLast(SonyCommands.battery(2))
                    queue.addLast(SonyCommands.equalizer(2))
                    knownNoise?.let(::askNoise)
                }
            }
            event is SonyEvent.Functions && !noiseKnown -> askNoise(event.noiseInquiry ?: SonyCommands.DEFAULT_NOISE_INQUIRY)
            event is SonyEvent.Noise -> {
                noiseKnown = true
                // Read: the other forms still waiting to be tried are not needed.
                val inquiry = event.control.form.inquiry
                queue.removeAll { it.size == 2 && it[0] == NOISE_GET && it[1] != inquiry }
            }
        }
        out += pump()
        return out to event
    }

    /**
     * Version 2, when the noise control has not been read yet: asks in the forms not tried yet,
     * the default first, one at a time; the first answer ends it.
     */
    fun guessNoise(): List<ByteArray> {
        if (version != 2 || noiseKnown) return emptyList()
        askNoise(SonyCommands.DEFAULT_NOISE_INQUIRY)
        SonyCommands.NOISE_GUESSES.forEach(::askNoise)
        return pump()
    }

    /**
     * Sets the noise control, then reads it back: some headphones (the WH-XB910N, says
     * MelodyLink) do not report a change they were asked for.
     */
    fun setNoise(control: NoiseControl): List<ByteArray> {
        queue.addLast(SonyCommands.setNoise(control))
        queue.addLast(SonyCommands.noise(control.form.inquiry))
        return pump()
    }

    private fun askNoise(inquiry: Int) {
        if (noiseAsked.add(inquiry)) queue.addLast(SonyCommands.noise(inquiry))
    }

    /**
     * No acknowledgement came in time: the request is sent again, and after three tries the
     * next one goes.
     */
    fun timeout(): List<ByteArray> {
        val pending = inFlight ?: return emptyList()
        if (++tries < MAX_TRIES) return listOf(SonyFrames.encode(SonyFrames.COMMAND, sequence, pending))
        inFlight = null
        return pump()
    }

    /** Whether a request awaits its acknowledgement. */
    val waiting: Boolean get() = inFlight != null

    private fun pump(): List<ByteArray> {
        if (inFlight != null) return emptyList()
        val next = queue.removeFirstOrNull() ?: return emptyList()
        inFlight = next
        tries = 0
        return listOf(SonyFrames.encode(SonyFrames.COMMAND, sequence, next))
    }

    private companion object {
        const val MAX_TRIES = 3
        const val NOISE_GET = 0x66
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
    /** Why the app cannot talk to them, while connected; null when it can, or is trying. */
    val problem: HeadphonesProblem? = null,
    val battery: Int? = null,
    val charging: Boolean = false,
    val noise: NoiseControl? = null,
    val equalizer: SonyEqualizer? = null,
    /** The phone's media volume, theirs while they play the phone's sound; null otherwise. */
    val volume: Int? = null,
    /** The highest media volume. */
    val maxVolume: Int = 0,
)

/** Why the app cannot talk to a pair of connected headphones. */
enum class HeadphonesProblem {
    /** Their control channel could not be opened: Sony's app may be holding it. */
    CHANNEL,

    /** The channel opened, but they did not answer. */
    NO_ANSWER,
}

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
