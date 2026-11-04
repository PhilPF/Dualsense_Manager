package com.philpf.dualsensemanager.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.philpf.dualsensemanager.DualsenseApp
import com.philpf.dualsensemanager.Permissions
import com.philpf.dualsensemanager.core.ControllerRow
import com.philpf.dualsensemanager.core.ControllerRows
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the app's process alive while controllers are connected, so their light-bar colours
 * stay on (Android turns a light off when the app holding it goes away). Its notification
 * lists the connected controllers with their battery levels. Stops itself shortly after the
 * last controller disconnects.
 */
class LightKeeperService : Service() {

    private val scope = MainScope()
    private var stopJob: Job? = null
    private var inForeground = false
    private var shown: List<ControllerRow>? = null

    private val app: DualsenseApp get() = application as DualsenseApp

    override fun onCreate() {
        super.onCreate()
        running = true
        KeeperNotification.ensureChannel(this)
        scope.launch {
            combine(app.manager.live, app.store.profiles) { live, profiles ->
                ControllerRows.build(profiles.values, live).filter { it.isConnected }
            }.collect { rows ->
                if (inForeground && rows != shown) {
                    shown = rows
                    getSystemService(NotificationManager::class.java)
                        .notify(KeeperNotification.ID, KeeperNotification.build(this@LightKeeperService, rows))
                }
                if (rows.isEmpty()) scheduleStop() else cancelStop()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rows = ControllerRows.build(app.store.profiles.value.values, app.manager.live.value)
            .filter { it.isConnected }
        return try {
            startForeground(
                KeeperNotification.ID,
                KeeperNotification.build(this, rows),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
            inForeground = true
            shown = rows
            START_STICKY
        } catch (e: RuntimeException) {
            // Only reachable after a restart by the system that is not allowed to go foreground.
            Log.w(TAG, "Could not run in the foreground", e)
            stopSelf()
            START_NOT_STICKY
        }
    }

    private fun scheduleStop() {
        if (stopJob != null) return
        stopJob = scope.launch {
            delay(STOP_DELAY_MS)
            stopSelf()
        }
    }

    private fun cancelStop() {
        stopJob?.cancel()
        stopJob = null
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LightKeeperService"
        private const val STOP_DELAY_MS = 20_000L

        @Volatile
        var running = false
            private set

        /**
         * Starts the service unless it is running. Android only allows this while the app is
         * visible or right after a Bluetooth connection broadcast; other attempts are ignored.
         */
        fun ensureRunning(context: Context) {
            if (running) return
            // From Android 14 a "connected device" service needs the Nearby devices permission;
            // starting without it would crash, so wait until it is granted.
            if (Build.VERSION.SDK_INT >= 34 && !Permissions.hasBluetoothConnect(context)) return
            try {
                context.startForegroundService(Intent(context, LightKeeperService::class.java))
            } catch (e: RuntimeException) {
                Log.i(TAG, "Not allowed to start the service now", e)
            }
        }
    }
}
