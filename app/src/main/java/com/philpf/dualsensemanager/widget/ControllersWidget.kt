package com.philpf.dualsensemanager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.philpf.dualsensemanager.DualsenseApp
import com.philpf.dualsensemanager.MainActivity
import com.philpf.dualsensemanager.R
import com.philpf.dualsensemanager.core.BatteryText
import com.philpf.dualsensemanager.core.ChargeStatus
import com.philpf.dualsensemanager.core.ControllerView
import com.philpf.dualsensemanager.core.StackGeometry
import com.philpf.dualsensemanager.core.Transport
import com.philpf.dualsensemanager.service.openAppIntent
import kotlin.math.roundToInt

/**
 * Home-screen widget, one cell: a card per connected controller, drawn as in the app (lines over
 * a glow of its light colour) with its name, battery and connection. Swiping down snaps to the
 * next controller. The cards are a StackView placed so that its front card covers the whole
 * widget and the cards behind fall outside it (core/StackGeometry): one card at a time, edge to
 * edge. Each card has a pill on its right with a dot per controller and the thumb on its own;
 * only the front card shows, so the pill stays in place while the thumb moves.
 */
class ControllersWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = (context.applicationContext as DualsenseApp).connected.value
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, forWidget(context, appWidgetManager, id, views))
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        val views = (context.applicationContext as DualsenseApp).connected.value
        appWidgetManager.updateAppWidget(appWidgetId, forWidget(context, appWidgetManager, appWidgetId, views))
    }

    companion object {
        private const val OPAQUE = 0xFF shl 24
        private const val PX = TypedValue.COMPLEX_UNIT_PX

        /** The most dots a card's pill shows; with more controllers it shows the nearest ones. */
        private const val MAX_DOTS = 8

        /** A one-cell widget, for when the launcher has not said its size yet. */
        private val DEFAULT_SIZE = SizeF(72f, 100f)

        /** The most layouts one RemoteViews may hold. */
        private const val MAX_SIZES = 16

        /** Redraws every placed widget with [views] (the connected controllers). */
        fun update(context: Context, views: List<ControllerView>) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ControllersWidget::class.java))
            for (id in ids) manager.updateAppWidget(id, forWidget(context, manager, id, views))
        }

        /** A layout for each size the widget has (portrait and landscape); Android picks. */
        private fun forWidget(context: Context, manager: AppWidgetManager, id: Int, views: List<ControllerView>): RemoteViews {
            val sizes = sizesOf(manager.getAppWidgetOptions(id))
            return if (sizes.size == 1) {
                remoteViews(context, views, sizes.single())
            } else {
                RemoteViews(sizes.associateWith { remoteViews(context, views, it) })
            }
        }

        @Suppress("DEPRECATION") // the typed getParcelableArrayList needs Android 13
        private fun sizesOf(options: Bundle?): List<SizeF> {
            if (options == null) return listOf(DEFAULT_SIZE)
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                ?.filter { it.width > 0f && it.height > 0f }
                ?.distinct()
            if (!sizes.isNullOrEmpty()) return sizes.take(MAX_SIZES)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            if (minWidth <= 0 || maxWidth <= 0 || minHeight <= 0 || maxHeight <= 0) return listOf(DEFAULT_SIZE)
            // Portrait is the narrow and tall one, landscape the wide and short one.
            return listOf(
                SizeF(minWidth.toFloat(), maxHeight.toFloat()),
                SizeF(maxWidth.toFloat(), minHeight.toFloat()),
            ).distinct()
        }

        /** The widget at [size] (in dp) showing [views]. */
        fun remoteViews(context: Context, views: List<ControllerView>, size: SizeF): RemoteViews {
            val remote = RemoteViews(context.packageName, R.layout.widget_controllers)
            // The stack, bigger than the widget and shifted up and left, so its front card fills it.
            val density = context.resources.displayMetrics.density
            val place = StackGeometry.placement(
                width = (size.width * density).roundToInt(),
                height = (size.height * density).roundToInt(),
                framePadding = StackGeometry.framePadding(density),
            )
            remote.setViewLayoutWidth(R.id.widget_stack, place.width.toFloat(), PX)
            remote.setViewLayoutHeight(R.id.widget_stack, place.height.toFloat(), PX)
            remote.setViewLayoutMargin(R.id.widget_stack, RemoteViews.MARGIN_LEFT, place.left.toFloat(), PX)
            remote.setViewLayoutMargin(R.id.widget_stack, RemoteViews.MARGIN_TOP, place.top.toFloat(), PX)

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
            card.setImageViewResource(
                R.id.card_transport,
                if (view.live?.transport == Transport.USB) R.drawable.ic_usb else R.drawable.ic_bluetooth,
            )

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
