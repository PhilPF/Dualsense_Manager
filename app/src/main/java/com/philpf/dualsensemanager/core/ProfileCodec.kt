package com.philpf.dualsensemanager.core

/**
 * Plain-text storage format for [ControllerProfile]s: a header line, then one line per profile
 * with tab-separated fields. Backslash, tab and line breaks inside text fields are escaped.
 * Unreadable lines are skipped rather than failing the whole list.
 */
object ProfileCodec {
    private const val HEADER = "dsm1"

    fun encode(profiles: Collection<ControllerProfile>): String = buildString {
        append(HEADER)
        for (p in profiles) {
            append('\n')
            append(
                listOf(
                    escape(p.id),
                    p.number.toString(),
                    p.kind.name,
                    escape(p.nickname),
                    (p.color and 0xFFFFFF).toString(16).padStart(6, '0'),
                    if (p.customColor) "1" else "0",
                    p.lastSeen.toString(),
                    p.lastBattery.toString(),
                ).joinToString("\t"),
            )
        }
    }

    fun decode(text: String?): List<ControllerProfile> {
        if (text.isNullOrEmpty()) return emptyList()
        val lines = text.split('\n')
        if (lines.first() != HEADER) return emptyList()
        return lines.drop(1).mapNotNull(::decodeLine)
    }

    private fun decodeLine(line: String): ControllerProfile? {
        val f = line.split('\t')
        if (f.size < 8) return null
        val id = unescape(f[0])
        val number = f[1].toIntOrNull() ?: return null
        val color = f[4].toIntOrNull(16) ?: return null
        if (id.isEmpty() || number < 1) return null
        return ControllerProfile(
            id = id,
            number = number,
            kind = ControllerKind.fromName(f[2]),
            nickname = unescape(f[3]),
            color = color and 0xFFFFFF,
            customColor = f[5] == "1",
            lastSeen = f[6].toLongOrNull() ?: 0L,
            lastBattery = f[7].toIntOrNull() ?: -1,
        )
    }

    internal fun escape(s: String): String = buildString(s.length) {
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

    internal fun unescape(s: String): String = buildString(s.length) {
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
