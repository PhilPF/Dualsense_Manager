package com.philpf.dualsensemanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Icon
import android.view.View
import android.widget.RemoteViews
import com.philpf.dualsensemanager.MainActivity
import com.philpf.dualsensemanager.R
import com.philpf.dualsensemanager.core.BatteryAlert
import com.philpf.dualsensemanager.core.BatteryReading
import com.philpf.dualsensemanager.core.BatteryText
import com.philpf.dualsensemanager.core.ChargeStatus
import com.philpf.dualsensemanager.core.ControllerView
import kotlin.math.abs

private const val OPAQUE = 0xFF shl 24

internal fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/**
 * A controller as the app draws it, for a notification's picture: its lines over a glow of
 * [color], with the touchpad and light strips lit in it. Square, [size] pixels a side.
 */
internal fun controllerBitmap(context: Context, color: Int, size: Int = 192): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val tint = OPAQUE or color
    fun layer(res: Int, tinted: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val drawable = requireNotNull(context.getDrawable(res)).mutate()
        if (tinted) drawable.setTint(tint)
        drawable.setBounds(left, top, right, bottom)
        drawable.draw(canvas)
    }
    layer(R.drawable.art_glow, true, 0, 0, size, size)
    // The drawing keeps its proportions (146 by 100), centred with a margin.
    val width = (size * 0.84f).toInt()
    val height = width * 100 / 146
    val left = (size - width) / 2
    val top = (size - height) / 2
    layer(R.drawable.ic_art_touchpad, true, left, top, left + width, top + height)
    layer(R.drawable.ic_art_lines_bold, false, left, top, left + width, top + height)
    layer(R.drawable.ic_art_strips, true, left, top, left + width, top + height)
    return bitmap
}

/**
 * The colour of the circle behind the small icon in a notification's header. Android draws the
 * icon itself in the notification's background colour, and keeps the circle as given when it
 * contrasts with that background: black on a light notification (a white controller on black),
 * white on a dark one (where a black circle would hide the dark controller).
 */
internal fun headerColor(night: Boolean): Int = if (night) Color.WHITE else Color.BLACK

internal fun Context.isNight(): Boolean = resources.configuration.isNightModeActive

/** The quiet notification of the light service: each connected controller and its battery. */
object KeeperNotification {
    const val ID = 1
    private const val CHANNEL_ID = "connected_controllers"

    /** How many controllers fit side by side, collapsed and expanded. */
    private const val SMALL_ROW = 3
    private const val BIG_ROW = 4

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Connected controllers", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Keeps light colours on while controllers are connected and shows their battery."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** [night]: whether the system is dark, for the header's icon. */
    fun build(context: Context, views: List<ControllerView>, night: Boolean = context.isNight()): Notification {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_controller)
            .setColor(headerColor(night))
            .setContentTitle(if (views.isEmpty()) "No controllers" else summary(views))
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        if (views.isNotEmpty()) {
            builder
                .setStyle(Notification.DecoratedCustomViewStyle())
                .setCustomContentView(contentView(context, views, big = false))
                .setCustomBigContentView(contentView(context, views, big = true))
        }
        return builder.build()
    }

    /** "Ana 50% · DualSense 2 20%", for places that show plain text. */
    private fun summary(views: List<ControllerView>): String =
        views.joinToString(" · ") { "${it.title} ${BatteryText.level(it.live?.battery)}" }

    /**
     * A row of controllers drawn as in the app, each with its battery, and with its name when
     * expanded.
     */
    fun contentView(context: Context, views: List<ControllerView>, big: Boolean): RemoteViews {
        val layout = if (big) R.layout.notification_controllers_big else R.layout.notification_controllers
        val remote = RemoteViews(context.packageName, layout)
        for (view in views.take(if (big) BIG_ROW else SMALL_ROW)) remote.addView(R.id.n_row, item(context, view, big))
        return remote
    }

    private fun item(context: Context, view: ControllerView, big: Boolean): RemoteViews {
        val item = RemoteViews(context.packageName, if (big) R.layout.notification_controller_big else R.layout.notification_controller_small)
        val color = OPAQUE or view.color
        for (id in intArrayOf(R.id.n_glow, R.id.n_touchpad, R.id.n_strips)) item.setInt(id, "setColorFilter", color)
        val battery = view.live?.battery
        item.setTextViewText(R.id.n_level, BatteryText.level(battery))
        val charging = battery?.status == ChargeStatus.CHARGING || battery?.status == ChargeStatus.FULL
        item.setViewVisibility(R.id.n_bolt, if (charging) View.VISIBLE else View.GONE)
        if (big) item.setTextViewText(R.id.n_name, view.title)
        return item
    }
}

/** Low, very low and fully charged battery notifications, with the controller in its colour. */
object BatteryAlertNotification {
    private const val CHANNEL_ID = "battery_alerts"
    private const val ALERT_ID_BASE = 1_000

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Battery alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Low battery and fully charged controllers."
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, view: ControllerView, alert: BatteryAlert, reading: BatteryReading) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val what = when (alert) {
            BatteryAlert.LOW -> "Battery low"
            BatteryAlert.CRITICAL -> "Battery very low"
            BatteryAlert.FULL -> "Fully charged"
        }
        val level = reading.percent?.let { BatteryText.level(it) }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_controller)
            .setColor(headerColor(context.isNight()))
            .setLargeIcon(Icon.createWithBitmap(controllerBitmap(context, view.color)))
            .setContentTitle(view.title)
            .setContentText(if (level != null) "$what · $level" else what)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        // One notification per controller: a newer alert replaces the previous one.
        manager.notify(ALERT_ID_BASE + abs(view.id.hashCode() % 100_000), notification)
    }
}
