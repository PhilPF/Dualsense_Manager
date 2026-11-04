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
    val nickname: String = "",
    /** Light-bar colour as 0xRRGGBB. */
    val color: Int,
    /** True once the user picked the colour; false while it is the automatic one. */
    val customColor: Boolean = false,
    /** Last time the controller was seen connected (epoch ms), 0 if never recorded. */
    val lastSeen: Long = 0L,
    /** Last battery percentage reported while connected, or -1 if none. */
    val lastBattery: Int = -1,
) {
    /** "DualSense 2", "DualSense Edge 3", ... */
    val defaultName: String get() = "${kind.label} $number"

    /** The nickname when there is one, otherwise the default name. */
    val title: String get() = nickname.ifBlank { defaultName }
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
)
