package com.philpf.dualsensemanager.core

data class ControllerRow(val profile: ControllerProfile, val live: LiveController?) {
    val id: String get() = profile.id
    val isConnected: Boolean get() = live != null
}

object ControllerRows {
    /** Connected controllers ordered by number, then remembered ones, most recently seen first. */
    fun build(profiles: Collection<ControllerProfile>, live: List<LiveController>): List<ControllerRow> {
        val liveById = live.associateBy { it.id }
        val connected = profiles
            .filter { it.id in liveById }
            .sortedBy { it.number }
            .map { ControllerRow(it, liveById[it.id]) }
        val away = profiles
            .filter { it.id !in liveById }
            .sortedWith(compareByDescending<ControllerProfile> { it.lastSeen }.thenBy { it.number })
            .map { ControllerRow(it, null) }
        return connected + away
    }
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
