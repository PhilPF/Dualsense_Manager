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
import com.philpf.dualsensemanager.core.ControllerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the app's process alive while controllers are connected, so their lights stay on
 * (Android turns a light off when the app holding it goes away). Its notification lists the
 * connected controllers with their battery levels. Stops itself shortly after the last
 * controller disconnects.
 */
class LightKeeperService : Service() {

    private val scope = MainScope()
    private var stopJob: Job? = null
    private var inForeground = false
    private var shown: List<ControllerView>? = null

    private val app: DualsenseApp get() = application as DualsenseApp

    override fun onCreate() {
        super.onCreate()
        running = true
        KeeperNotification.ensureChannel(this)
        scope.launch {
            app.connected.collect { views ->
                if (inForeground && views != shown) {
                    shown = views
                    getSystemService(NotificationManager::class.java)
                        .notify(KeeperNotification.ID, KeeperNotification.build(this@LightKeeperService, views))
                }
                if (views.isEmpty()) scheduleStop() else cancelStop()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val views = app.connected.value
        return try {
            startForeground(
                KeeperNotification.ID,
                KeeperNotification.build(this, views),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
            inForeground = true
            shown = views
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
