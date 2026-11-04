package com.philpf.dualsensemanager.core

/** The controllers this app manages: Sony's PS5 pads. The ids are the same over USB and Bluetooth. */
enum class ControllerKind(val productId: Int, val label: String) {
    DUALSENSE(0x0CE6, "DualSense"),
    DUALSENSE_EDGE(0x0DF2, "DualSense Edge");

    companion object {
        const val SONY_VENDOR_ID = 0x054C

        fun fromIds(vendorId: Int, productId: Int): ControllerKind? =
            if (vendorId != SONY_VENDOR_ID) null else entries.firstOrNull { it.productId == productId }

        fun fromName(name: String): ControllerKind = entries.firstOrNull { it.name == name } ?: DUALSENSE
    }
}

/** A person who plays: a name and a light-bar colour, assigned to one controller at a time. */
data class Player(val id: String, val name: String, val color: Int)

/**
 * What the app remembers about one physical controller.
 *
 * [id] is Android's input-device descriptor. The kernel driver sets the controller's MAC address
 * as its unique id, so the descriptor stays the same across reconnections, reboots and both
 * transports (USB and Bluetooth).
 */
data class ControllerProfile(
    val id: String,
    val number: Int,
    val kind: ControllerKind,
    /** The controller's own light-bar colour (0xRRGGBB), used when no player is assigned. */
    val color: Int,
    /** True once the user picked the colour; false while it is the automatic one. */
    val customColor: Boolean = false,
    /** The player using this controller, if any. */
    val playerId: String? = null,
    /** Last time the controller was seen connected (epoch ms), 0 if never recorded. */
    val lastSeen: Long = 0L,
    /** Last battery percentage reported while connected, or -1 if none. */
    val lastBattery: Int = -1,
    /** How fast its battery drains while connected, learned over time, in % per hour; 0 if not yet. */
    val drainRate: Float = 0f,
    /** How fast it charges, learned over time, in % per hour; 0 if not yet. */
    val chargeRate: Float = 0f,
) {
    /** "DualSense 2", "DualSense Edge 3", ... */
    val defaultName: String get() = "${kind.label} $number"
}

enum class ChargeStatus { CHARGING, DISCHARGING, FULL, NOT_CHARGING, UNKNOWN }

/** One battery reading; [percent] is null when the controller has not reported a level. */
data class BatteryReading(val percent: Int?, val status: ChargeStatus)

enum class Transport { BLUETOOTH, USB }

/** A controller that is connected right now. */
data class LiveController(
    val id: String,
    val deviceId: Int,
    val kind: ControllerKind,
    val transport: Transport,
    /** Null when the system reports no battery for this device. */
    val battery: BatteryReading?,
    /** Whether Android lets apps set this controller's light bar. */
    val lightBar: Boolean,
    /** About how long until its battery is empty, or full while charging; null if not known yet. */
    val eta: BatteryEta? = null,
)

/** A controller as shown to the user: what is remembered, who plays with it, and its live state. */
data class ControllerView(val profile: ControllerProfile, val player: Player?, val live: LiveController?) {
    val id: String get() = profile.id

    /** The player's name when a player is assigned, otherwise "DualSense n". */
    val title: String get() = player?.name?.trim()?.takeIf { it.isNotEmpty() } ?: profile.defaultName

    /** "DualSense n" under a player's name; nothing otherwise. */
    val subtitle: String? get() = if (player != null) profile.defaultName else null

    /** The light-bar colour: the player's when one is assigned, otherwise the controller's. */
    val color: Int get() = player?.color ?: profile.color

    val isConnected: Boolean get() = live != null
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Buttons and sticks of the controller, for lighting up what is being pressed. */
enum class ControllerPart {
    CROSS, CIRCLE, SQUARE, TRIANGLE,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
    L1, R1, L2, R2,
    LEFT_STICK, RIGHT_STICK, L3, R3,
    CREATE, OPTIONS, PS, MUTE, TOUCHPAD,
}
