package com.philpf.dualsensemanager.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.philpf.dualsensemanager.Permissions

/**
 * Wakes the app when a DualSense connects over Bluetooth, so its colour is applied even when
 * the app is closed. Starting the light service from here is allowed because this broadcast
 * requires the Nearby devices permission. (Waking the process also starts the controller
 * manager, which applies the colour as soon as Android registers the controller.)
 */
class ConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        if (!Permissions.hasBluetoothConnect(context)) return
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val name = try {
            device?.name
        } catch (e: SecurityException) {
            null
        }
        // Skip devices that are clearly something else (headphones, watches, ...).
        if (name != null && !looksLikeController(name)) return
        LightKeeperService.ensureRunning(context)
    }

    private fun looksLikeController(name: String) =
        name.contains("DualSense", ignoreCase = true) || name.contains("Wireless Controller", ignoreCase = true)
}
