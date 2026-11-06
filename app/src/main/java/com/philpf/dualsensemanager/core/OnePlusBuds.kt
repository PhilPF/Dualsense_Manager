package com.philpf.dualsensemanager.core

/*
 * The protocol OnePlus earbuds (as OPPO's) speak with their maker's app, HeyMelody, over a
 * Bluetooth RFCOMM channel. Written from MacMelody's description of it
 * (https://github.com/Omraj09/MacMelody, MIT License, © 2026 Omraj Sawant), with facts checked
 * against the notes of QuickBuds and BudsLink (GPL; no code taken from either); the frames they
 * captured are this file's tests.
 */

/**
 * One message: its [command] (bit 15 set in a reply), [sequence] (a reply repeats the
 * request's; FF in a push) and [payload] bytes (0..255). On the wire: AA, the number of bytes
 * that follow (LEB128), 00 (a whole message; this app never needs one sliced), 00, the command
 * (2 bytes, little-endian), the sequence, the payload's length (2 bytes, little-endian) and the
 * payload. There is no checksum.
 */
data class OpoFrame(val command: Int, val sequence: Int, val payload: List<Int> = emptyList()) {
    val isReply: Boolean get() = command and REPLY != 0

    /** The command, without the reply bit. */
    val base: Int get() = command and REPLY.inv()

    fun encode(): ByteArray {
        val n = payload.size
        val body = listOf(0x00, 0x00, command and 0xFF, (command ushr 8) and 0xFF, sequence and 0xFF, n and 0xFF, (n ushr 8) and 0xFF) +
            payload.map { it and 0xFF }
        val bytes = listOf(START) + leb128(body.size) + body
        return ByteArray(bytes.size) { bytes[it].toByte() }
    }

    companion object {
        const val START = 0xAA
        const val REPLY = 0x8000
        const val PUSH_SEQUENCE = 0xFF

        /** The bytes between the length and the payload. */
        internal const val HEADER = 7

        /** [value] in 7-bit groups, low first, each but the last with bit 7 set. */
        fun leb128(value: Int): List<Int> {
            val out = ArrayList<Int>()
            var v = value
            do {
                var b = v and 0x7F
                v = v ushr 7
                if (v != 0) b = b or 0x80
                out += b
            } while (v != 0)
            return out
        }
    }
}

/**
 * Collects the bytes read into frames: from AA, its length, then that many bytes. Bytes before
 * an AA, and an AA whose length or header does not fit, are skipped. Sliced messages are read
 * past, not joined.
 */
class OpoFrameReader {
    private val buffer = ArrayList<Int>()

    fun feed(bytes: ByteArray, count: Int = bytes.size): List<OpoFrame> {
        for (i in 0 until count) buffer += bytes[i].toInt() and 0xFF
        val frames = ArrayList<OpoFrame>()
        while (buffer.isNotEmpty()) {
            val start = buffer.indexOf(OpoFrame.START)
            if (start < 0) {
                buffer.clear()
                break
            }
            if (start > 0) buffer.subList(0, start).clear()
            // The length: at most 3 bytes.
            var length = 0
            var lengthBytes = 0
            var complete = false
            while (lengthBytes < 3 && 1 + lengthBytes < buffer.size) {
                val b = buffer[1 + lengthBytes]
                length = length or ((b and 0x7F) shl (7 * lengthBytes))
                lengthBytes++
                if (b and 0x80 == 0) {
                    complete = true
                    break
                }
            }
            if (!complete) {
                if (lengthBytes >= 3) {
                    buffer.removeAt(0)
                    continue
                }
                break
            }
            if (length < OpoFrame.HEADER || length > MAX_LENGTH) {
                buffer.removeAt(0)
                continue
            }
            val h = 1 + lengthBytes
            if (buffer.size < h + OpoFrame.HEADER) break
            val whole = buffer[h] == 0x00
            if (whole && (buffer[h + 5] or (buffer[h + 6] shl 8)) != length - OpoFrame.HEADER) {
                buffer.removeAt(0)
                continue
            }
            val total = h + length
            if (buffer.size < total) break
            if (whole) frames += OpoFrame(buffer[h + 2] or (buffer[h + 3] shl 8), buffer[h + 4], buffer.subList(h + OpoFrame.HEADER, total).toList())
            buffer.subList(0, total).clear()
        }
        return frames
    }

    private companion object {
        const val MAX_LENGTH = 4096
    }
}

/** What the buds said. */
sealed interface OpoEvent {
    /** Their product id, such as 062014 (the Buds Pro 2). */
    data class Product(val id: String) : OpoEvent

    /** The events they can report, to be asked for. */
    data class Offered(val events: List<Int>) : OpoEvent

    data class Battery(val parts: List<PartBattery>) : OpoEvent

    /** The noise control; [notified] when it changed on the buds, not as an answer. */
    data class Noise(val mode: NoiseMode, val strength: NoiseStrength?, val notified: Boolean = false) : OpoEvent

    /** The equalizer preset; [notified] when it changed on their side, not as an answer. */
    data class Equalizer(val id: Int, val notified: Boolean = false) : OpoEvent

    /** A gesture on the buds: a tap, a squeeze or a swipe. */
    data object Gesture : OpoEvent

    /** They refused a change: [status] 0E for the noise control while no bud is in an ear. */
    data class Refused(val command: Int, val status: Int) : OpoEvent
}

/** The commands this app sends, the buds' own requests it answers, and what it reads. */
object OpoCommands {
    const val HANDSHAKE = 0x0100
    const val PRODUCT = 0x0103
    const val BATTERY = 0x0106
    const val NOISE = 0x010C
    const val EQUALIZER = 0x010F
    const val EVENTS = 0x0200
    const val PUSH = 0x0204
    const val SUBSCRIBE = 0x0205
    const val SET_NOISE = 0x0404
    const val SET_EQUALIZER = 0x0406
    const val TIME = 0x0500
    const val EQUALIZER_CHANGED = 0x0504

    /** Pushes asked for, of those offered: battery, wearing, noise control, a gesture on the buds. */
    val WANTED_EVENTS = listOf(0x01, 0x02, 0x03, 0xF1)

    /** "The current noise control", the argument of [NOISE] and [SET_NOISE]. */
    val CURRENT_NOISE = listOf(0x01, 0x01)

    /** Subscribes to the [WANTED_EVENTS] among those [offered]: their count, then the ids; null when none. */
    fun subscribe(offered: List<Int>): List<Int>? {
        val ids = offered.filter { it in WANTED_EVENTS }.distinct()
        return if (ids.isEmpty()) null else listOf(ids.size) + ids
    }

    /** Sets the noise control to the one bit in [mask], in as many bytes as it needs, low first. */
    fun setNoise(mask: Int): List<Int> {
        val bytes = (31 - Integer.numberOfLeadingZeros(mask)) / 8 + 1
        return CURRENT_NOISE + (0 until bytes).map { (mask ushr (8 * it)) and 0xFF }
    }

    /**
     * The answer to a request of the buds, or null when it is not one: the time (Unix
     * [seconds]; some buds reset the link without it), and three questions about the phone,
     * answered as HeyMelody does.
     */
    fun answer(request: OpoFrame, seconds: Long): OpoFrame? {
        if (request.isReply || request.sequence == OpoFrame.PUSH_SEQUENCE) return null
        val payload = when (request.command) {
            TIME -> listOf(0x00) + (0 until 4).map { ((seconds ushr (8 * it)) and 0xFF).toInt() }
            0x050C -> listOf(0x00, 0x01, 0x00)
            0x051C -> listOf(0x00, 0x00, 0x00)
            0x051D -> listOf(0x01)
            else -> return null
        }
        return OpoFrame(request.command or OpoFrame.REPLY, request.sequence, payload)
    }

    fun parse(frame: OpoFrame): OpoEvent? {
        val p = frame.payload
        if (frame.isReply) {
            val ok = p.firstOrNull() == 0x00
            return when (frame.base) {
                PRODUCT -> if (ok && p.size >= 4) OpoEvent.Product(hex(p[3]) + hex(p[2]) + hex(p[1])) else null
                EVENTS -> if (ok && p.size >= 2) OpoEvent.Offered(p.drop(2).take(p[1])) else null
                BATTERY -> if (ok) battery(p)?.let(OpoEvent::Battery) else null
                NOISE -> if (ok && p.size >= 4 && p[1] == 0x01 && p[2] == 0x01) noise(p.drop(3), notified = false) else null
                EQUALIZER -> if (ok && p.size >= 2) OpoEvent.Equalizer(p[1]) else null
                SET_NOISE, SET_EQUALIZER -> if (!ok && p.isNotEmpty()) OpoEvent.Refused(frame.base, p[0]) else null
                else -> null
            }
        }
        return when (frame.command) {
            PUSH -> when (p.firstOrNull()) {
                0x01 -> battery(p)?.let(OpoEvent::Battery)
                // 03 01 01 is the current mode; 03 02 and 03 04 are other settings.
                0x03 -> if (p.size >= 4 && p[1] == 0x01 && p[2] == 0x01) noise(p.drop(3), notified = true) else null
                0xF1 -> if (p.size >= 6) OpoEvent.Gesture else null
                else -> null
            }
            EQUALIZER_CHANGED -> p.firstOrNull()?.let { OpoEvent.Equalizer(it, notified = true) }
            else -> null
        }
    }

    /**
     * After a status or event code: the count, then part and raw value pairs (part 1 left, 2
     * right, 3 case; level raw & 7F, charging raw & 80). Level 0 is "not known" and left out.
     */
    private fun battery(p: List<Int>): List<PartBattery>? {
        val count = p.getOrNull(1) ?: return null
        if (count == 0 || p.size < 2 + 2 * count) return null
        return (0 until count).mapNotNull { i ->
            val part = when (p[2 + 2 * i]) {
                1 -> BudPart.LEFT
                2 -> BudPart.RIGHT
                3 -> BudPart.CASE
                else -> return@mapNotNull null
            }
            val raw = p[3 + 2 * i]
            val level = raw and 0x7F
            if (level == 0) null else PartBattery(part, level.coerceAtMost(100), raw and 0x80 != 0)
        }
    }

    /** The mode, 1 to 4 bytes little-endian, in the report table. */
    private fun noise(bytes: List<Int>, notified: Boolean): OpoEvent.Noise? {
        val value = bytes.take(4).foldIndexed(0) { i, acc, b -> acc or (b shl (8 * i)) }
        val (mode, strength) = OpoNoise.reported(value) ?: return null
        return OpoEvent.Noise(mode, strength, notified)
    }

    private fun hex(b: Int) = "%02X".format(b)
}

/**
 * OnePlus earbuds' noise control. It is set with one bit (Off 01, noise cancelling at its last
 * strength 02, transparency 04, Extreme 10, Moderate 20, Mild 40, Smart 80) and reported in
 * another table (Off 0008, noise cancelling 0002, its strengths 0010 to 0080 as when set,
 * transparency 0100 or 0200 with voices enhanced, adaptive 0800): QuickBuds' captures from the
 * Buds 4, the table MacMelody gives the Buds Pro 2.
 */
object OpoNoise {
    fun mask(mode: NoiseMode, strength: NoiseStrength?): Int = when (mode) {
        NoiseMode.OFF -> 0x01
        NoiseMode.AMBIENT -> 0x04
        NoiseMode.NOISE_CANCELLING -> when (strength) {
            null -> 0x02
            NoiseStrength.SMART -> 0x80
            NoiseStrength.EXTREME -> 0x10
            NoiseStrength.MODERATE -> 0x20
            NoiseStrength.MILD -> 0x40
        }
    }

    fun reported(value: Int): Pair<NoiseMode, NoiseStrength?>? = when (value) {
        0x0008 -> NoiseMode.OFF to null
        0x0100, 0x0200 -> NoiseMode.AMBIENT to null
        0x0002, 0x0800 -> NoiseMode.NOISE_CANCELLING to null
        0x0010 -> NoiseMode.NOISE_CANCELLING to NoiseStrength.EXTREME
        0x0020 -> NoiseMode.NOISE_CANCELLING to NoiseStrength.MODERATE
        0x0040 -> NoiseMode.NOISE_CANCELLING to NoiseStrength.MILD
        0x0080 -> NoiseMode.NOISE_CANCELLING to NoiseStrength.SMART
        else -> null
    }
}

/** OnePlus earbuds: how to recognise them, and their equalizer presets. */
object OnePlusBuds {
    /** The RFCOMM services HeyMelody uses, the first the one that answers. */
    const val SERVICE = "0000079A-D102-11E1-9B23-00025B00A5A5"
    const val SERVICE_2 = "00001107-D102-11E1-9B23-00025B00A5A5"

    private val NAME = Regex("^(OnePlus .*Buds.*|Buds Pro 2R?)$", RegexOption.IGNORE_CASE)

    /** Whether a paired device is OnePlus earbuds, by its name (OPPO's and realme's share the service but not the tables). */
    fun isOnePlus(name: String?): Boolean = name != null && NAME.matches(name.trim())

    /** The Buds Pro 2's presets (product ids 062014 and 062414; MacMelody's list). */
    private val BUDS_PRO_2 = listOf(
        EqPreset(0, "Balanced"),
        EqPreset(1, "Bold bass"),
        EqPreset(2, "Serenade"),
        EqPreset(3, "Clear"),
    )

    fun presets(product: String?): List<EqPreset> = when (product) {
        "062014", "062414" -> BUDS_PRO_2
        else -> (0..3).map { EqPreset(it, "Preset ${it + 1}") }
    }

    /** The preset with [id] among [presets], or a curve of the buds' own. */
    fun preset(id: Int, presets: List<EqPreset>): EqPreset = presets.firstOrNull { it.id == id } ?: EqPreset(id, "Custom", selectable = false)
}

/**
 * One conversation with OnePlus earbuds: one request at a time, the next sent when its reply
 * comes (matched on the command and the sequence number it repeats) or after a few tries; the
 * buds' own requests answered at once. On connecting: the handshake, the product id and the
 * events they offer (battery, wearing, noise control and gestures are then asked for), then the
 * noise control, battery and equalizer. A change is read back, as an accepted change is not
 * always applied.
 */
class OnePlusSession {
    private var sequence = 0
    private val queue = ArrayDeque<Pair<Int, List<Int>>>()
    private var inFlight: OpoFrame? = null
    private var tries = 0

    /** Whether the buds have answered anything. */
    var ready = false
        private set

    var product: String? = null
        private set

    /** The frames to send first. */
    fun start(): List<ByteArray> {
        queue.clear()
        inFlight = null
        queue.addLast(OpoCommands.HANDSHAKE to emptyList())
        queue.addLast(OpoCommands.PRODUCT to emptyList())
        queue.addLast(OpoCommands.EVENTS to emptyList())
        queue.addLast(OpoCommands.NOISE to OpoCommands.CURRENT_NOISE)
        queue.addLast(OpoCommands.BATTERY to emptyList())
        queue.addLast(OpoCommands.EQUALIZER to emptyList())
        return pump()
    }

    /** Starts again, until the buds answer. */
    fun restart(): List<ByteArray> = if (ready) emptyList() else start()

    fun setNoise(mask: Int): List<ByteArray> {
        queue.addLast(OpoCommands.SET_NOISE to OpoCommands.setNoise(mask))
        queue.addLast(OpoCommands.NOISE to OpoCommands.CURRENT_NOISE)
        return pump()
    }

    fun setEqualizer(id: Int): List<ByteArray> {
        queue.addLast(OpoCommands.SET_EQUALIZER to listOf(id))
        queue.addLast(OpoCommands.EQUALIZER to emptyList())
        return pump()
    }

    /** A frame arrived (Unix time [seconds]): the frames to write back, and what it said. */
    fun receive(frame: OpoFrame, seconds: Long): Pair<List<ByteArray>, OpoEvent?> {
        OpoCommands.answer(frame, seconds)?.let { return listOf(it.encode()) to null }
        val event = OpoCommands.parse(frame)
        if (frame.isReply) {
            ready = true
            val asked = inFlight
            // A late reply to an earlier try still tells something, but does not answer this one.
            if (asked != null && asked.command == frame.base && asked.sequence == frame.sequence) inFlight = null
            when (event) {
                is OpoEvent.Product -> product = event.id
                // Asked for before the reads, so that changes after them are reported.
                is OpoEvent.Offered -> OpoCommands.subscribe(event.events)?.let { queue.addFirst(OpoCommands.SUBSCRIBE to it) }
                else -> Unit
            }
        }
        return pump() to event
    }

    /** No reply came in time: the request is sent again, and after three tries the next one goes. */
    fun timeout(): List<ByteArray> {
        val pending = inFlight ?: return emptyList()
        if (++tries < MAX_TRIES) return listOf(pending.encode())
        inFlight = null
        return pump()
    }

    /** Whether a request awaits its reply. */
    val waiting: Boolean get() = inFlight != null

    private fun pump(): List<ByteArray> {
        if (inFlight != null) return emptyList()
        val (command, payload) = queue.removeFirstOrNull() ?: return emptyList()
        // 1 to FE: FF marks the buds' pushes.
        sequence = if (sequence >= 0xFE) 1 else sequence + 1
        val frame = OpoFrame(command, sequence, payload)
        inFlight = frame
        tries = 0
        return listOf(frame.encode())
    }

    private companion object {
        const val MAX_TRIES = 3
    }
}
