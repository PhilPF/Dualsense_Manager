package com.philpf.dualsensemanager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object Permissions {
    /** "Nearby devices": needed to hear about Bluetooth connections and to run the light service on Android 14+. */
    fun hasBluetoothConnect(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** The runtime permissions the app asks for, in one prompt. */
    val requested: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        }
}
