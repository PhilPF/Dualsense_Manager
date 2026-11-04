package com.philpf.dualsensemanager.core

object ControllerViews {
    /** Connected controllers ordered by number, then remembered ones, most recently seen first. */
    fun build(
        profiles: Collection<ControllerProfile>,
        players: Map<String, Player>,
        live: List<LiveController>,
    ): List<ControllerView> {
        val liveById = live.associateBy { it.id }
        fun view(p: ControllerProfile) = ControllerView(p, p.playerId?.let { players[it] }, liveById[p.id])
        val connected = profiles.filter { it.id in liveById }.sortedBy { it.number }.map(::view)
        val away = profiles
            .filter { it.id !in liveById }
            .sortedWith(compareByDescending<ControllerProfile> { it.lastSeen }.thenBy { it.number })
            .map(::view)
        return connected + away
    }
}

object Players {
    private val DEFAULT_NAME = Regex("^Player (\\d+)$")

    /** "Player n" with the smallest n that no existing player's name uses. */
    fun defaultName(existing: Collection<String>): String {
        val used = existing.mapNotNull { DEFAULT_NAME.find(it.trim())?.groupValues?.get(1)?.toIntOrNull() }
        return "Player ${LedPalette.nextNumber(used)}"
    }

    /**
     * Gives [playerId] (or nobody, when null) to [controllerId]. A player uses one controller at
     * a time, so any other controller the player had loses them.
     */
    fun assign(
        profiles: Map<String, ControllerProfile>,
        controllerId: String,
        playerId: String?,
    ): Map<String, ControllerProfile> = profiles.mapValues { (id, p) ->
        when {
            id == controllerId -> p.copy(playerId = playerId)
            playerId != null && p.playerId == playerId -> p.copy(playerId = null)
            else -> p
        }
    }

    /** Removes [playerId] from every controller. */
    fun unassign(profiles: Map<String, ControllerProfile>, playerId: String): Map<String, ControllerProfile> =
        profiles.mapValues { (_, p) -> if (p.playerId == playerId) p.copy(playerId = null) else p }
}

object PlayerLeds {
    private val NAME = Regex("player-?(\\d+)$", RegexOption.IGNORE_CASE)

    /**
     * The PS5's player-indicator patterns over the five LEDs (positions 1–5, left to right),
     * as Linux's hid-playstation draws them: 1 = centre, 2 = inner pair, 3 = centre and outer
     * pair, 4 = all but the centre, 5 = all. Larger numbers repeat the cycle.
     */
    fun pattern(number: Int): Set<Int> = when ((number - 1).mod(5) + 1) {
        1 -> setOf(3)
        2 -> setOf(2, 4)
        3 -> setOf(1, 3, 5)
        4 -> setOf(1, 2, 4, 5)
        else -> setOf(1, 2, 3, 4, 5)
    }

    /** The LED position (1–5) a light controls, from its name, e.g. "input12:white:player-3" -> 3. */
    fun position(lightName: String): Int? =
        NAME.find(lightName.trim())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..5 }
}

object RelativeTime {
    /** "just now", "5 min ago", "3 h ago", "yesterday", "4 days ago", ... */
    fun since(nowMs: Long, thenMs: Long): String {
        if (thenMs <= 0L) return "a while ago"
        val minutes = (nowMs - thenMs).coerceAtLeast(0L) / 60_000L
        val day = 24L * 60L
        return when {
            minutes < 1L -> "just now"
            minutes < 60L -> "$minutes min ago"
            minutes < day -> "${minutes / 60L} h ago"
            minutes < 2L * day -> "yesterday"
            minutes < 30L * day -> "${minutes / day} days ago"
            else -> "over a month ago"
        }
    }
}
