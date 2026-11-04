package com.philpf.dualsensemanager.core

/**
 * Plain-text storage for controllers and players: a header line, then one line per record with
 * tab-separated fields. Backslash, tab and line breaks inside text are escaped. Unreadable lines
 * are skipped rather than failing the whole list.
 */
object ProfileCodec {
    private const val HEADER = "dsm2"

    fun encode(profiles: Collection<ControllerProfile>): String = Lines.encode(HEADER, profiles) { p ->
        listOf(
            p.id,
            p.number.toString(),
            p.kind.name,
            Lines.hex(p.color),
            if (p.customColor) "1" else "0",
            p.playerId.orEmpty(),
            p.lastSeen.toString(),
            p.lastBattery.toString(),
            p.drainRate.toString(),
            p.chargeRate.toString(),
        )
    }

    fun decode(text: String?): List<ControllerProfile> {
        if (text?.startsWith(HEADER_V1) == true) return decodeV1(text)
        return decodeV2(text)
    }

    /** The first release stored a nickname in place of the player; keep numbers and colours. */
    private const val HEADER_V1 = "dsm1"

    private fun decodeV1(text: String): List<ControllerProfile> = Lines.decode(HEADER_V1, text, fields = 8) parse@{ f ->
        val number = f[1].toIntOrNull() ?: return@parse null
        val color = f[4].toIntOrNull(16) ?: return@parse null
        if (f[0].isEmpty() || number < 1) return@parse null
        ControllerProfile(
            id = f[0],
            number = number,
            kind = ControllerKind.fromName(f[2]),
            color = color and 0xFFFFFF,
            customColor = f[5] == "1",
            lastSeen = f[6].toLongOrNull() ?: 0L,
            lastBattery = f[7].toIntOrNull() ?: -1,
        )
    }

    private fun decodeV2(text: String?): List<ControllerProfile> = Lines.decode(HEADER, text, fields = 8) parse@{ f ->
        val number = f[1].toIntOrNull() ?: return@parse null
        val color = f[3].toIntOrNull(16) ?: return@parse null
        if (f[0].isEmpty() || number < 1) return@parse null
        ControllerProfile(
            id = f[0],
            number = number,
            kind = ControllerKind.fromName(f[2]),
            color = color and 0xFFFFFF,
            customColor = f[4] == "1",
            playerId = f[5].ifEmpty { null },
            lastSeen = f[6].toLongOrNull() ?: 0L,
            lastBattery = f[7].toIntOrNull() ?: -1,
            // Added later: absent in older data.
            drainRate = rate(f.getOrNull(8)),
            chargeRate = rate(f.getOrNull(9)),
        )
    }

    private fun rate(field: String?): Float = field?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: 0f
}

object PlayerCodec {
    private const val HEADER = "dsp1"

    fun encode(players: Collection<Player>): String = Lines.encode(HEADER, players) { p ->
        listOf(p.id, p.name, Lines.hex(p.color))
    }

    fun decode(text: String?): List<Player> = Lines.decode(HEADER, text, fields = 3) parse@{ f ->
        val color = f[2].toIntOrNull(16) ?: return@parse null
        if (f[0].isEmpty()) return@parse null
        Player(id = f[0], name = f[1], color = color and 0xFFFFFF)
    }
}

internal object Lines {
    fun hex(rgb: Int): String = (rgb and 0xFFFFFF).toString(16).padStart(6, '0')

    fun <T> encode(header: String, items: Collection<T>, fields: (T) -> List<String>): String = buildString {
        append(header)
        for (item in items) {
            append('\n')
            append(fields(item).joinToString("\t") { escape(it) })
        }
    }

    fun <T : Any> decode(header: String, text: String?, fields: Int, parse: (List<String>) -> T?): List<T> {
        if (text.isNullOrEmpty()) return emptyList()
        val lines = text.split('\n')
        if (lines.first() != header) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val f = line.split('\t').map(::unescape)
            if (f.size < fields) null else parse(f)
        }
    }

    fun escape(s: String): String = buildString(s.length) {
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
    }

    fun unescape(s: String): String = buildString(s.length) {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> append('\t')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    else -> append(s[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
