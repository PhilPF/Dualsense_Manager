package com.philpf.dualsensemanager.core

/**
 * Which of the app's launcher icons shows the connected controllers' colours. The app ships one
 * icon per set of 1 to 4 preset colours (tools/app_icons.py), named by a key listing the
 * colours' indices in [LedPalette.colors], ascending.
 */
object AppIconChoice {
    /** Blue, red, green and pink, the PS players' colours: the icon without controllers. */
    const val DEFAULT_KEY = "0123"

    /** The most colours one icon shows. */
    const val MAX_COLORS = 4

    /**
     * The icon for [colors], the connected controllers' light colours in their order: each
     * colour's nearest preset, repeats dropped, the first [MAX_COLORS], in palette order.
     */
    fun key(colors: List<Int>): String {
        if (colors.isEmpty()) return DEFAULT_KEY
        return colors.map(::nearestPreset).distinct().take(MAX_COLORS).sorted().joinToString("")
    }

    /** The index of the preset nearest to [rgb] (squared distance in RGB; ties go to the first). */
    fun nearestPreset(rgb: Int): Int = LedPalette.colors.indices.minBy { distance(rgb, LedPalette.colors[it].rgb) }

    /** The preset colours an icon [key] shows, in its order. */
    fun colors(key: String): List<Int> = key.map { LedPalette.colors[it.digitToInt()].rgb }

    private fun distance(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }
}
