package com.philpf.dualsensemanager.core

import kotlin.math.roundToInt

object BatteryRules {
    /**
     * Converts the raw values of `android.hardware.BatteryState` ([capacity] in 0..1 or NaN) into a
     * reading, dropping levels that carry no information:
     *
     * - Linux's hid-playstation driver starts every controller at 100 % with an unknown status
     *   until the first input report arrives, so that pair means "not reported yet".
     * - For its error states (temperature or voltage out of range, charging error) the driver
     *   reports 0 % with status "not charging" or "unknown"; that 0 is not a measurement.
     */
    fun reading(capacity: Float, status: ChargeStatus): BatteryReading {
        val raw = if (capacity.isNaN()) null else (capacity * 100f).roundToInt().coerceIn(0, 100)
        val percent = when {
            raw == null -> null
            status == ChargeStatus.UNKNOWN && raw == 100 -> null
            (status == ChargeStatus.UNKNOWN || status == ChargeStatus.NOT_CHARGING) && raw == 0 -> null
            else -> raw
        }
        return BatteryReading(percent, status)
    }
}

/** A half-open percentage interval [low, high). */
data class PercentRange(val low: Int, val high: Int)

object BatteryText {
    /**
     * The DualSense reports its charge as a level d in {0, ..., 10}, and the kernel publishes
     * p = min(10d + 5, 100) %. So a reported p with p = 5 (mod 10) and 5 <= p <= 95 only says
     * that the charge lies in [p - 5, p + 5). Returns that interval, or null when [percent] is
     * not of this form (then it is taken at face value).
     */
    fun range(percent: Int): PercentRange? =
        if (percent in 5..95 && percent % 10 == 5) PercentRange(percent - 5, percent + 5) else null

    /** "50–60%" for a 10 %-step reading, "100%" otherwise. */
    fun level(percent: Int): String = range(percent)?.let { "${it.low}–${it.high}%" } ?: "$percent%"

    fun level(reading: BatteryReading?): String {
        val percent = reading?.percent ?: return "—"
        return level(percent)
    }

    fun status(status: ChargeStatus): String = when (status) {
        ChargeStatus.CHARGING -> "Charging"
        ChargeStatus.DISCHARGING -> "On battery"
        ChargeStatus.FULL -> "Fully charged"
        ChargeStatus.NOT_CHARGING -> "Not charging"
        ChargeStatus.UNKNOWN -> "Status unknown"
    }

    /** At most 15 % reported, i.e. at most the 10–20 % step. */
    fun isLow(percent: Int): Boolean = percent <= 15
}
