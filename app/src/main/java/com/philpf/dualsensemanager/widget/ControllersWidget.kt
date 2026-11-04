package com.philpf.dualsensemanager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.philpf.dualsensemanager.DualsenseApp
import com.philpf.dualsensemanager.MainActivity
import com.philpf.dualsensemanager.R
import com.philpf.dualsensemanager.core.BatteryText
import com.philpf.dualsensemanager.core.ChargeStatus
import com.philpf.dualsensemanager.core.ControllerView
import com.philpf.dualsensemanager.core.Transport
import com.philpf.dualsensemanager.service.openAppIntent

/**
 * Home-screen widget: a tile per connected controller, its drawing in its light colour, with
 * the name and battery underneath. More controllers than fit scroll within the widget.
 */
class ControllersWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = (context.applicationContext as DualsenseApp).connected.value
        appWidgetManager.updateAppWidget(appWidgetIds, remoteViews(context, views))
    }

    companion object {
        private const val OPAQUE = 0xFF shl 24

        /** Redraws every placed widget with [views] (the connected controllers). */
        fun update(context: Context, views: List<ControllerView>) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ControllersWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, remoteViews(context, views))
        }

        fun remoteViews(context: Context, views: List<ControllerView>): RemoteViews {
            val remote = RemoteViews(context.packageName, R.layout.widget_controllers)
            val items = RemoteViews.RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1)
            for (view in views) items.addItem(view.id.hashCode().toLong(), tile(context, view))
            remote.setRemoteAdapter(R.id.widget_grid, items.build())
            remote.setEmptyView(R.id.widget_grid, R.id.widget_empty)
            remote.setPendingIntentTemplate(
                R.id.widget_grid,
                PendingIntent.getActivity(
                    context,
                    1,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            remote.setOnClickPendingIntent(R.id.widget_empty, openAppIntent(context))
            return remote
        }

        private fun tile(context: Context, view: ControllerView): RemoteViews {
            val tile = RemoteViews(context.packageName, R.layout.widget_tile)
            val battery = view.live?.battery
            tile.setInt(R.id.tile_body, "setColorFilter", OPAQUE or view.color)
            tile.setTextViewText(R.id.tile_name, view.title)
            tile.setTextViewText(R.id.tile_level, BatteryText.level(battery))
            val charging = battery?.status == ChargeStatus.CHARGING || battery?.status == ChargeStatus.FULL
            tile.setViewVisibility(R.id.tile_bolt, if (charging) View.VISIBLE else View.GONE)
            tile.setImageViewResource(
                R.id.tile_transport,
                if (view.live?.transport == Transport.USB) R.drawable.ic_usb else R.drawable.ic_bluetooth,
            )
            tile.setOnClickFillInIntent(R.id.tile_root, Intent())
            return tile
        }
    }
}
