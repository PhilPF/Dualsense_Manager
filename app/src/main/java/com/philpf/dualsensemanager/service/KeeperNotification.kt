package com.philpf.dualsensemanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.philpf.dualsensemanager.MainActivity
import com.philpf.dualsensemanager.R
import com.philpf.dualsensemanager.core.BatteryAlert
import com.philpf.dualsensemanager.core.BatteryReading
import com.philpf.dualsensemanager.core.BatteryText
import com.philpf.dualsensemanager.core.ChargeStatus
import com.philpf.dualsensemanager.core.ControllerView
import com.philpf.dualsensemanager.core.Transport
import kotlin.math.abs

private const val OPAQUE = 0xFF shl 24

internal fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/** The quiet notification of the light service: connected controllers and their batteries. */
object KeeperNotification {
    const val ID = 1
    private const val CHANNEL_ID = "connected_controllers"

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
        val title = when (views.size) {
            0 -> "No controllers"
            1 -> "1 controller"
            else -> "${views.size} controllers"
        }
        val lines = views.map(::line)
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_controller)
            .setContentTitle(title)
            .setContentText(lines.joinTo(SpannableStringBuilder(), "    "))
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        if (lines.size > 1) {
            val style = Notification.InboxStyle()
            lines.forEach { style.addLine(it) }
            builder.setStyle(style)
        }
        views.firstOrNull()?.let { builder.setColor(OPAQUE or it.color) }
        return builder.build()
    }

    /** "● Ana  50–60% ⚡ · USB" with the dot in the controller's colour. */
    private fun line(view: ControllerView): CharSequence {
        val text = SpannableStringBuilder()
        text.append("●", ForegroundColorSpan(OPAQUE or view.color), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.append(' ').append(view.title)
        val battery = view.live?.battery
        val percent = battery?.percent
        if (percent != null) text.append("  ").append(BatteryText.level(percent))
        if (battery?.status == ChargeStatus.CHARGING) text.append(" ⚡")
        if (view.live?.transport == Transport.USB) text.append(" · USB")
        return text
    }
}

/** Low, very low and fully charged battery notifications. */
object BatteryAlertNotification {
    private const val CHANNEL_ID = "battery_alerts"

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
        val title = when (alert) {
            BatteryAlert.LOW -> "${view.title}: battery low"
            BatteryAlert.CRITICAL -> "${view.title}: battery very low"
            BatteryAlert.FULL -> "${view.title}: fully charged"
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_controller)
            .setContentTitle(title)
            .setContentText(reading.percent?.let { BatteryText.level(it) } ?: "")
            .setColor(OPAQUE or view.color)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        // One notification per controller: a newer alert replaces the previous one.
        manager.notify(ALERT_ID_BASE + abs(view.id.hashCode() % 100_000), notification)
    }

    private const val ALERT_ID_BASE = 1_000
}
