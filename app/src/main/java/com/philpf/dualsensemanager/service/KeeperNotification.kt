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
import com.philpf.dualsensemanager.core.BatteryText
import com.philpf.dualsensemanager.core.ChargeStatus
import com.philpf.dualsensemanager.core.ControllerRow
import com.philpf.dualsensemanager.core.Transport

object KeeperNotification {
    const val ID = 1
    private const val CHANNEL_ID = "connected_controllers"
    private const val OPAQUE = 0xFF shl 24

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Connected controllers",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps light-bar colours on while controllers are connected and shows their battery."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, rows: List<ControllerRow>): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = when (rows.size) {
            0 -> "Waiting for controllers"
            1 -> "1 controller connected"
            else -> "${rows.size} controllers connected"
        }
        val lines = rows.map(::line)
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_controller)
            .setContentTitle(title)
            .setContentText(lines.joinTo(SpannableStringBuilder(), "    "))
            .setContentIntent(open)
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
        rows.firstOrNull()?.let { builder.setColor(OPAQUE or it.profile.color) }
        return builder.build()
    }

    /** "● Ana  50–60% ⚡ · USB" with the dot in the controller's colour. */
    private fun line(row: ControllerRow): CharSequence {
        val text = SpannableStringBuilder()
        text.append("●", ForegroundColorSpan(OPAQUE or row.profile.color), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.append(' ').append(row.profile.title)
        val battery = row.live?.battery
        val percent = battery?.percent
        if (percent != null) text.append("  ").append(BatteryText.level(percent))
        if (battery?.status == ChargeStatus.CHARGING) text.append(" ⚡")
        if (row.live?.transport == Transport.USB) text.append(" · USB")
        return text
    }
}
