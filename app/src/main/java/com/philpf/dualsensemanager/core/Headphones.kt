package com.philpf.dualsensemanager.core

/** Whose protocol a pair of headphones or earbuds speaks. */
enum class HeadphonesKind { SONY, ONEPLUS }

enum class NoiseMode { NOISE_CANCELLING, AMBIENT, OFF }

/** How strongly earbuds cancel noise (OnePlus): their own choice, or one of three levels. */
enum class NoiseStrength(val label: String) {
    SMART("Smart"),
    EXTREME("Extreme"),
    MODERATE("Moderate"),
    MILD("Mild"),
}

/**
 * The noise control: [mode]; the ambient sound level, 0 to 20, kept while noise cancelling so
 * it comes back with ambient sound, and whether ambient sound focuses on voices (Sony); the
 * [form] Sony headphones write it in; and how strongly earbuds cancel noise ([strength], OnePlus;
 * null when they do not say).
 */
data class NoiseControl(
    val mode: NoiseMode,
    val ambientLevel: Int,
    val voiceFocus: Boolean,
    val form: NoiseForm = NoiseForm(),
    val strength: NoiseStrength? = null,
) {
    /** The modes these headphones have, in the order their button steps through them. */
    val modes: List<NoiseMode>
        get() = if (form.ncByte) NoiseMode.entries else listOf(NoiseMode.AMBIENT, NoiseMode.OFF)

    /** The mode after this one, as their button steps. */
    fun nextMode(): NoiseMode = modes[(modes.indexOf(mode) + 1) % modes.size]
}

/** An equalizer preset: its [id] in the headphones' protocol, its [label], and whether it can be chosen as it is. */
data class EqPreset(val id: Int, val label: String, val selectable: Boolean = true)

enum class BudPart { LEFT, RIGHT, CASE }

/** One earbud's or the case's battery. */
data class PartBattery(val part: BudPart, val level: Int, val charging: Boolean)

/** A pair of headphones or earbuds paired with the phone, and what is known about them now. */
data class Headphones(
    /** Their Bluetooth address, which identifies them. */
    val address: String,
    val name: String,
    /** Connected to the phone. */
    val connected: Boolean,
    val kind: HeadphonesKind = HeadphonesKind.SONY,
    /** The app is talking to them, so the controls below are theirs. */
    val ready: Boolean = false,
    /** Why the app cannot talk to them, while connected; null when it can, or is trying. */
    val problem: HeadphonesProblem? = null,
    /** Headphones' one battery. */
    val battery: Int? = null,
    val charging: Boolean = false,
    /** Earbuds' batteries: each bud's and the case's, those known. */
    val parts: List<PartBattery> = emptyList(),
    val noise: NoiseControl? = null,
    /** The equalizer preset in use, and the ones that can be chosen. */
    val equalizer: EqPreset? = null,
    val presets: List<EqPreset> = emptyList(),
    /** The phone's media volume, theirs while they play the phone's sound; null otherwise. */
    val volume: Int? = null,
    /** The highest media volume. */
    val maxVolume: Int = 0,
)

/** Why the app cannot talk to a pair of connected headphones. */
enum class HeadphonesProblem {
    /** Their control channel could not be opened: their maker's app may be holding it. */
    CHANNEL,

    /** The channel opened, but they did not answer. */
    NO_ANSWER,
}
