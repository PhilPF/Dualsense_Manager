package com.philpf.dualsensemanager

import android.app.Application
import android.util.Log
import com.philpf.dualsensemanager.core.BatteryAlert
import com.philpf.dualsensemanager.core.BatteryReading
import com.philpf.dualsensemanager.core.ControllerView
import com.philpf.dualsensemanager.core.ControllerViews
import com.philpf.dualsensemanager.device.ControllerManager
import com.philpf.dualsensemanager.device.ProfileStore
import com.philpf.dualsensemanager.device.SettingsStore
import com.philpf.dualsensemanager.service.BatteryAlertNotification
import com.philpf.dualsensemanager.service.ControllersTileService
import com.philpf.dualsensemanager.service.LightKeeperService
import com.philpf.dualsensemanager.widget.ControllersIcon
import com.philpf.dualsensemanager.widget.ControllersWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DualsenseApp : Application() {

    lateinit var store: ProfileStore
        private set

    lateinit var settings: SettingsStore
        private set

    lateinit var manager: ControllerManager
        private set

    /** The connected controllers as shown everywhere (screen, notification, widget, tile). */
    lateinit var connected: StateFlow<List<ControllerView>>
        private set

    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        store = ProfileStore(this)
        settings = SettingsStore(this)
        manager = ControllerManager(
            context = this,
            store = store,
            onFirstControllerConnected = { LightKeeperService.ensureRunning(this) },
            onBatteryAlert = ::showBatteryAlert,
        )
        connected = combine(manager.live, store.profiles, store.players) { live, profiles, players ->
            ControllerViews.build(profiles.values, players, live).filter { it.isConnected }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())
        manager.start()

        scope.launch {
            connected.collect { views ->
                ControllersWidget.update(this@DualsenseApp, views)
                ControllersTileService.requestUpdate(this@DualsenseApp)
            }
        }
        // The home-screen icon only shows colours, so it is redrawn only when they change.
        scope.launch(Dispatchers.Default) {
            connected.map { views -> views.map { it.color } }.distinctUntilChanged().collect { colors ->
                try {
                    ControllersIcon.update(this@DualsenseApp, colors)
                } catch (e: IllegalStateException) {
                    Log.w("DualsenseApp", "Could not update the icon", e) // e.g. before the phone is first unlocked
                }
            }
        }
    }

    private fun showBatteryAlert(controllerId: String, alert: BatteryAlert, reading: BatteryReading) {
        val enabled = when (alert) {
            BatteryAlert.FULL -> settings.fullBatteryAlerts.value
            BatteryAlert.LOW, BatteryAlert.CRITICAL -> settings.lowBatteryAlerts.value
        }
        if (!enabled) return
        val profile = store.get(controllerId) ?: return
        val view = ControllerView(profile, profile.playerId?.let(store::player), null)
        BatteryAlertNotification.show(this, view, alert, reading)
    }
}
