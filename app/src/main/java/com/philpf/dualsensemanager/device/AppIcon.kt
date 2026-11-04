package com.philpf.dualsensemanager.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Switches the app's launcher icon. Every icon is an activity-alias of MainActivity in the
 * manifest ("icon.Lights" + its key, see core/AppIconChoice); exactly one is enabled at a time.
 *
 * Disabling the alias a window was opened from closes that window, so callers switch only while
 * the app is not on screen. Launchers may take a moment to show the new icon.
 */
class AppIcon(private val context: Context) {

    private val packageManager: PackageManager get() = context.packageManager

    /** Shows the icon with [key]; does nothing when it is already the only one enabled. */
    fun show(key: String) {
        val target = ComponentName(context.packageName, "${context.packageName}.icon.Lights$key")
        val enabled = enabledLauncherAliases()
        if (enabled == listOf(target)) return
        // Enable the new icon before disabling the old one, so there is always one to tap.
        val changes = listOf(target to PackageManager.COMPONENT_ENABLED_STATE_ENABLED) +
            enabled.filter { it != target }.map { it to PackageManager.COMPONENT_ENABLED_STATE_DISABLED }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.setComponentEnabledSettings(
                changes.map { (component, state) -> PackageManager.ComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) },
            )
        } else {
            for ((component, state) in changes) {
                packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            }
        }
    }

    /** The app's icons that the launcher shows now (normally one). */
    private fun enabledLauncherAliases(): List<ComponentName> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        val found = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        return found.map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
    }
}
