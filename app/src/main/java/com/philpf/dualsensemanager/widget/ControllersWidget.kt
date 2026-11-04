package com.philpf.dualsensemanager.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.philpf.dualsensemanager.DualsenseApp
import com.philpf.dualsensemanager.R
import com.philpf.dualsensemanager.core.BatteryText
import com.philpf.dualsensemanager.core.ChargeStatus
import com.philpf.dualsensemanager.core.ControllerView
import com.philpf.dualsensemanager.service.openAppIntent

/** Home-screen widget: each connected controller's colour, name and battery. */
class ControllersWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = (context.applicationContext as DualsenseApp).connected.value
        render(context, views, appWidgetManager, appWidgetIds)
    }

    companion object {
        private const val OPAQUE = 0xFF shl 24

        private class Row(val root: Int, val dot: Int, val name: Int, val bolt: Int, val level: Int)

        private val ROWS = listOf(
            Row(R.id.row1, R.id.dot1, R.id.name1, R.id.bolt1, R.id.level1),
            Row(R.id.row2, R.id.dot2, R.id.name2, R.id.bolt2, R.id.level2),
            Row(R.id.row3, R.id.dot3, R.id.name3, R.id.bolt3, R.id.level3),
            Row(R.id.row4, R.id.dot4, R.id.name4, R.id.bolt4, R.id.level4),
        )

        /** Redraws every placed widget with [views] (the connected controllers). */
        fun update(context: Context, views: List<ControllerView>) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ControllersWidget::class.java))
            if (ids.isEmpty()) return
            render(context, views, manager, ids)
        }

        private fun render(context: Context, views: List<ControllerView>, manager: AppWidgetManager, ids: IntArray) {
            val remote = RemoteViews(context.packageName, R.layout.widget_controllers)
            remote.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            remote.setViewVisibility(R.id.widget_empty, if (views.isEmpty()) View.VISIBLE else View.GONE)
            ROWS.forEachIndexed { index, row ->
                val view = views.getOrNull(index)
                if (view == null) {
                    remote.setViewVisibility(row.root, View.GONE)
                    return@forEachIndexed
                }
                val battery = view.live?.battery
                remote.setViewVisibility(row.root, View.VISIBLE)
                remote.setInt(row.dot, "setColorFilter", OPAQUE or view.color)
                remote.setTextViewText(row.name, view.title)
                remote.setTextViewText(row.level, battery?.percent?.let { BatteryText.level(it) } ?: "—")
                val charging = battery?.status == ChargeStatus.CHARGING || battery?.status == ChargeStatus.FULL
                remote.setViewVisibility(row.bolt, if (charging) View.VISIBLE else View.GONE)
            }
            manager.updateAppWidget(ids, remote)
        }
    }
}
