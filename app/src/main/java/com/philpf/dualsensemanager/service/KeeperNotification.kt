package com.philpf.dualsensemanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
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

/** The controller drawing in [color], as a bitmap (for a notification's picture). */
internal fun controllerBitmap(context: Context, color: Int, width: Int = 192, height: Int = 132): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val body = requireNotNull(context.getDrawable(R.drawable.ic_controller_body)).mutate()
    body.setTint(OPAQUE or color)
    val details = requireNotNull(context.getDrawable(R.drawable.ic_controller_details))
    for (drawable in listOf(body, details)) {
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
    }
    return bitmap
}

/** The quiet notification of the light service: each connected controller and its battery. */
object KeeperNotification {
    const val ID = 1
    private const val CHANNEL_ID = "connected_controllers"
    private const val SLOTS = 4

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Connected controllers", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Keeps light colours on while controllers are connected and shows their battery."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, views: List<ControllerView>): Notification {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_controller)
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
                .setColor(OPAQUE or views.first().color)
        }
        return builder.build()
    }

    /** "Ana 50% · DualSense 2 20%", for places that show plain text. */
    private fun summary(views: List<ControllerView>): String =
        views.joinToString(" · ") { "${it.title} ${BatteryText.level(it.live?.battery)}" }

    /** A row of controllers in their colours with their battery (and names, when expanded). */
    fun contentView(context: Context, views: List<ControllerView>, big: Boolean): RemoteViews {
        val layout = if (big) R.layout.notification_controllers_big else R.layout.notification_controllers
        val remote = RemoteViews(context.packageName, layout)
        for (slot in 0 until SLOTS) {
            val view = views.getOrNull(slot)
            val item = ids("n_item", slot)
            if (view == null) {
                remote.setViewVisibility(item, View.GONE)
                continue
            }
            val battery = view.live?.battery
            remote.setViewVisibility(item, View.VISIBLE)
            remote.setInt(ids("n_body", slot), "setColorFilter", OPAQUE or view.color)
            remote.setTextViewText(ids("n_level", slot), BatteryText.level(battery))
            val charging = battery?.status == ChargeStatus.CHARGING || battery?.status == ChargeStatus.FULL
            remote.setViewVisibility(ids("n_bolt", slot), if (charging) View.VISIBLE else View.GONE)
            if (big) remote.setTextViewText(ids("n_name", slot), view.title)
        }
        return remote
    }

    private fun ids(prefix: String, slot: Int): Int = when (prefix) {
        "n_item" -> intArrayOf(R.id.n_item1, R.id.n_item2, R.id.n_item3, R.id.n_item4)[slot]
        "n_body" -> intArrayOf(R.id.n_body1, R.id.n_body2, R.id.n_body3, R.id.n_body4)[slot]
        "n_level" -> intArrayOf(R.id.n_level1, R.id.n_level2, R.id.n_level3, R.id.n_level4)[slot]
        "n_bolt" -> intArrayOf(R.id.n_bolt1, R.id.n_bolt2, R.id.n_bolt3, R.id.n_bolt4)[slot]
        else -> intArrayOf(R.id.n_name1, R.id.n_name2, R.id.n_name3, R.id.n_name4)[slot]
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
            .setLargeIcon(Icon.createWithBitmap(controllerBitmap(context, view.color)))
            .setContentTitle(view.title)
            .setContentText(if (level != null) "$what · $level" else what)
            .setColor(OPAQUE or view.color)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        // One notification per controller: a newer alert replaces the previous one.
        manager.notify(ALERT_ID_BASE + abs(view.id.hashCode() % 100_000), notification)
    }
}
