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
import com.philpf.dualsensemanager.service.openAppIntent

/**
 * Home-screen widget, one cell: a stack with a card per connected controller, drawn as in the
 * app (lines over a glow of its light colour) with its name and battery. Swiping down snaps to
 * the next controller. Each card has a pill on its side with a dot per connected controller,
 * the thumb on its own, so the pill stays in place while the thumb moves from card to card.
 * (A widget is not told which card is showing, so the pill cannot live outside the cards.)
 */
class ControllersWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = (context.applicationContext as DualsenseApp).connected.value
        appWidgetManager.updateAppWidget(appWidgetIds, remoteViews(context, views))
    }

    companion object {
        private const val OPAQUE = 0xFF shl 24

        /** The most dots a card's pill shows; with more controllers it shows the nearest ones. */
        private const val MAX_DOTS = 8

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
            for (index in views.indices) items.addItem(views[index].id.hashCode().toLong(), card(context, views, index))
            remote.setRemoteAdapter(R.id.widget_stack, items.build())
            remote.setEmptyView(R.id.widget_stack, R.id.widget_empty)
            remote.setPendingIntentTemplate(
                R.id.widget_stack,
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

        private fun card(context: Context, views: List<ControllerView>, index: Int): RemoteViews {
            val view = views[index]
            val card = RemoteViews(context.packageName, R.layout.widget_card)
            val color = OPAQUE or view.color
            for (id in intArrayOf(R.id.card_glow, R.id.card_touchpad, R.id.card_strips)) card.setInt(id, "setColorFilter", color)
            card.setTextViewText(R.id.card_name, view.title)
            val battery = view.live?.battery
            card.setTextViewText(R.id.card_level, BatteryText.level(battery))
            val charging = battery?.status == ChargeStatus.CHARGING || battery?.status == ChargeStatus.FULL
            card.setViewVisibility(R.id.card_bolt, if (charging) View.VISIBLE else View.GONE)

            // The pill: a window of at most MAX_DOTS controllers around this one.
            val first = (index - MAX_DOTS / 2).coerceIn(0, maxOf(0, views.size - MAX_DOTS))
            for (i in first until minOf(views.size, first + MAX_DOTS)) {
                val dot = RemoteViews(context.packageName, R.layout.widget_card_dot)
                dot.setInt(R.id.dot_color, "setColorFilter", OPAQUE or views[i].color)
                dot.setViewVisibility(R.id.dot_thumb, if (i == index) View.VISIBLE else View.GONE)
                card.addView(R.id.card_dots, dot)
            }
            card.setOnClickFillInIntent(R.id.card_root, Intent())
            return card
        }
    }
}
