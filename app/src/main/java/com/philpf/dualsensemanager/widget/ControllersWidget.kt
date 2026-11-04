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
import com.philpf.dualsensemanager.core.Transport
import com.philpf.dualsensemanager.core.WidgetGrid
import com.philpf.dualsensemanager.service.openAppIntent

/**
 * Home-screen widget: a square tile per connected controller, its drawing in its light colour
 * with the name and battery underneath. Tiles are as large as the widget allows while showing
 * every controller; when there are too many, the grid scrolls.
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
        private const val DIP = TypedValue.COMPLEX_UNIT_DIP

        /** The padding of the widget's root in widget_controllers.xml, in dp. */
        private const val PADDING = 8f

        /** A 3 × 2 widget in portrait, for when the launcher has not said its size yet. */
        private val DEFAULT_SIZE = SizeF(203f, 220f)

        /** The most layouts one RemoteViews may hold. */
        private const val MAX_SIZES = 16

        /** Redraws every placed widget with [views] (the connected controllers). */
        fun update(context: Context, views: List<ControllerView>) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ControllersWidget::class.java))
            for (id in ids) manager.updateAppWidget(id, forWidget(context, manager, id, views))
        }

        /** A layout for each size the widget can have (portrait and landscape); Android picks. */
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
            val area = SizeF(maxOf(size.width - 2 * PADDING, 1f), maxOf(size.height - 2 * PADDING, 1f))
            val grid = WidgetGrid.layout(views.size, area.width, area.height)
            remote.setInt(R.id.widget_grid, "setNumColumns", grid.columns)
            remote.setIntDimen(R.id.widget_grid, "setColumnWidth", grid.tile, DIP)
            remote.setViewLayoutWidth(R.id.widget_grid, grid.width, DIP)
            remote.setViewLayoutHeight(R.id.widget_grid, grid.height, DIP)

            val items = RemoteViews.RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1)
            for (view in views) items.addItem(view.id.hashCode().toLong(), tile(context, view, grid.tile))
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

        private fun tile(context: Context, view: ControllerView, side: Float): RemoteViews {
            val tile = RemoteViews(context.packageName, R.layout.widget_tile)
            val battery = view.live?.battery
            val color = OPAQUE or view.color
            tile.setViewLayoutHeight(R.id.tile_root, side, DIP)
            tile.setInt(R.id.tile_glow, "setColorFilter", color)
            tile.setInt(R.id.tile_body, "setColorFilter", color)
            tile.setTextViewText(R.id.tile_name, view.title)
            tile.setTextViewText(R.id.tile_level, BatteryText.level(battery))
            // Larger tiles get larger text; the drawing stays the largest part either way.
            val large = side >= 120f
            tile.setTextViewTextSize(R.id.tile_name, TypedValue.COMPLEX_UNIT_SP, if (large) 14f else 12f)
            tile.setTextViewTextSize(R.id.tile_level, TypedValue.COMPLEX_UNIT_SP, if (large) 12f else 11f)
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
