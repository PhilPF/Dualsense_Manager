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

object BatteryText {
    /**
     * The percentage to show for a reported one. The DualSense reports its charge as a level
     * d in {0, ..., 10}, published by the kernel as p = min(10d + 5, 100) %, which only says that
     * the charge lies in [10d, 10d + 10). Its lower end 10d = p - 5 is shown: a value the
     * charge is guaranteed to reach. Values not of that form are shown as they are.
     */
    fun shown(percent: Int): Int = if (percent in 5..95 && percent % 10 == 5) percent - 5 else percent

    /** "50%" for a reported 55 %. */
    fun level(percent: Int): String = "${shown(percent)}%"

    fun level(reading: BatteryReading?): String {
        val percent = reading?.percent ?: return "—"
        return level(percent)
    }

    /** Battery-icon fill, 0..1. */
    fun fraction(percent: Int): Float = shown(percent) / 100f

    /** At most 15 % reported, i.e. the 10 % step or lower. */
    fun isLow(percent: Int): Boolean = percent <= 15
}
