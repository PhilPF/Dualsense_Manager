package com.philpf.dualsensemanager.core

data class LedColor(val name: String, val rgb: Int)

object LedPalette {
    /**
     * Light-bar colours, in the order new controllers get them: DualSense 1–4 get the PS5's own
     * player colours (blue, red, green, pink). Values are tuned for the controller's RGB LED,
     * not the screen.
     */
    val colors: List<LedColor> = listOf(
        LedColor("Blue", 0x0030FF),
        LedColor("Red", 0xFF0A14),
        LedColor("Green", 0x00FF2A),
        LedColor("Pink", 0xFF1E8C),
        LedColor("Orange", 0xFF5200),
        LedColor("Cyan", 0x00E5FF),
        LedColor("Purple", 0x9A00FF),
        LedColor("Yellow", 0xFFB000),
        LedColor("White", 0xFFFFFF),
    )

    /** The smallest positive integer not in [used]. */
    fun nextNumber(used: Iterable<Int>): Int {
        val taken = used.toHashSet()
        var n = 1
        while (n in taken) n++
        return n
    }

    /**
     * The automatic colour for controller number [number]: its own palette slot when no other
     * remembered controller uses that colour, otherwise the first unused palette colour; if all
     * are used, the slot colour anyway.
     */
    fun automaticColor(number: Int, takenColors: Iterable<Int>): Int {
        val taken = takenColors.mapTo(HashSet()) { it and 0xFFFFFF }
        val preferred = colors[(number - 1).mod(colors.size)].rgb
        if (preferred !in taken) return preferred
        return colors.firstOrNull { it.rgb !in taken }?.rgb ?: preferred
    }

    fun nameOf(rgb: Int): String? = colors.firstOrNull { it.rgb == (rgb and 0xFFFFFF) }?.name
}
