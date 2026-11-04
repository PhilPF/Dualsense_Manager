package com.philpf.dualsensemanager.core

enum class BatteryAlert { LOW, CRITICAL, FULL }

/** What has been announced for one controller since it last started charging or discharging. */
data class AlertState(
    val lowAlerted: BatteryAlert? = null,
    val charging: Boolean = false,
    val fullAlerted: Boolean = false,
)

object BatteryAlerts {
    /** Reported 15 %: the 10–20 % step. */
    const val LOW_PERCENT = 15

    /** Reported 5 %: the 0–10 % step. */
    const val CRITICAL_PERCENT = 5

    /**
     * Advances [state] with a new [reading] and returns the alert to show, if any:
     *
     * - LOW once the level is at most [LOW_PERCENT] while not charging, then CRITICAL once it is
     *   at most [CRITICAL_PERCENT]. Each is announced once per discharge; charging resets them.
     *   (Readings can flicker between neighbouring steps, so going back up does not reset them.)
     * - FULL when the controller reports full after charging was seen, once until it runs on
     *   battery again (so topping-up between "charging" and "full" is not announced twice). A
     *   controller that is already full when it connects is not announced.
     */
    fun step(state: AlertState, reading: BatteryReading?): Pair<AlertState, BatteryAlert?> {
        if (reading == null) return state to null
        return when (reading.status) {
            ChargeStatus.CHARGING ->
                AlertState(lowAlerted = null, charging = true, fullAlerted = state.fullAlerted) to null
            ChargeStatus.FULL -> {
                val alert = if (state.charging && !state.fullAlerted) BatteryAlert.FULL else null
                AlertState(lowAlerted = null, charging = state.charging, fullAlerted = state.fullAlerted || alert != null) to alert
            }
            else -> {
                // No level (not reported yet, or an error state): nothing to decide.
                val percent = reading.percent ?: return state to null
                val alert = when {
                    percent <= CRITICAL_PERCENT && state.lowAlerted != BatteryAlert.CRITICAL -> BatteryAlert.CRITICAL
                    percent <= LOW_PERCENT && state.lowAlerted == null -> BatteryAlert.LOW
                    else -> null
                }
                AlertState(lowAlerted = alert ?: state.lowAlerted, charging = false, fullAlerted = false) to alert
            }
        }
    }
}
