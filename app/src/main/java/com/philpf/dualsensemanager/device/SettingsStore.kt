package com.philpf.dualsensemanager.device

import android.content.Context
import com.philpf.dualsensemanager.core.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App settings: theme, font, which battery alerts to show and the app icon. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM,
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _monospace = MutableStateFlow(prefs.getBoolean(KEY_MONOSPACE, false))

    /** Whether all text uses a monospaced font. */
    val monospace: StateFlow<Boolean> = _monospace.asStateFlow()

    private val _grid = MutableStateFlow(prefs.getBoolean(KEY_GRID, false))

    /** Whether the main screen shows square cards in two columns, rather than a list of wide panels. */
    val grid: StateFlow<Boolean> = _grid.asStateFlow()

    private val _iconFollowsControllers = MutableStateFlow(prefs.getBoolean(KEY_ICON, false))

    /** Whether the launcher icon takes the connected controllers' colours. */
    val iconFollowsControllers: StateFlow<Boolean> = _iconFollowsControllers.asStateFlow()

    private val _lowBatteryAlerts = MutableStateFlow(prefs.getBoolean(KEY_LOW, true))
    val lowBatteryAlerts: StateFlow<Boolean> = _lowBatteryAlerts.asStateFlow()

    private val _fullBatteryAlerts = MutableStateFlow(prefs.getBoolean(KEY_FULL, true))
    val fullBatteryAlerts: StateFlow<Boolean> = _fullBatteryAlerts.asStateFlow()

    /** Whether the permissions prompt was already shown once, automatically. */
    var askedForPermissions: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ASKED, value).apply()
        }

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setMonospace(on: Boolean) {
        _monospace.value = on
        prefs.edit().putBoolean(KEY_MONOSPACE, on).apply()
    }

    fun setGrid(on: Boolean) {
        _grid.value = on
        prefs.edit().putBoolean(KEY_GRID, on).apply()
    }

    fun setIconFollowsControllers(on: Boolean) {
        _iconFollowsControllers.value = on
        prefs.edit().putBoolean(KEY_ICON, on).apply()
    }

    fun setLowBatteryAlerts(on: Boolean) {
        _lowBatteryAlerts.value = on
        prefs.edit().putBoolean(KEY_LOW, on).apply()
    }

    fun setFullBatteryAlerts(on: Boolean) {
        _fullBatteryAlerts.value = on
        prefs.edit().putBoolean(KEY_FULL, on).apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_MONOSPACE = "monospace"
        const val KEY_GRID = "grid"
        const val KEY_ICON = "icon_follows_controllers"
        const val KEY_LOW = "low_battery_alerts"
        const val KEY_FULL = "full_battery_alerts"
        const val KEY_ASKED = "asked_for_permissions"
    }
}
