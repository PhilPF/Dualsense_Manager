package com.philpf.dualsensemanager.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.philpf.dualsensemanager.DualsenseApp
import com.philpf.dualsensemanager.MainActivity
import com.philpf.dualsensemanager.R
import com.philpf.dualsensemanager.core.BatteryText

/** Quick Settings tile: the connected controllers' battery levels at a glance; opens the app. */
class ControllersTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val views = (application as DualsenseApp).connected.value
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_controller)
        tile.label = "Controllers"
        tile.state = if (views.isEmpty()) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.subtitle = if (views.isEmpty()) {
            "None"
        } else {
            views.joinToString("  ") { v -> v.live?.battery?.percent?.let { BatteryText.level(it) } ?: "—" }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(openAppIntent(this))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Asks the system to refresh the tile, if it is in the user's Quick Settings. */
        fun requestUpdate(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, ControllersTileService::class.java))
            } catch (e: RuntimeException) {
                // The tile is not added; nothing to update.
            }
        }
    }
}
