package com.philpf.dualsensemanager.ui

import android.appwidget.AppWidgetHostView
import android.util.SizeF
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.philpf.dualsensemanager.core.BatteryReading
import com.philpf.dualsensemanager.core.ChargeStatus
import com.philpf.dualsensemanager.core.ControllerView
import com.philpf.dualsensemanager.core.Transport
import com.philpf.dualsensemanager.service.KeeperNotification
import com.philpf.dualsensemanager.widget.ControllersWidget

/**
 * Debug builds only: the home-screen widget (a 3 × 2 widget in portrait with the sample
 * controllers, and a wider one with four) and the notification's views, for screenshots.
 */
@Composable
fun RemoteViewsPreview(views: List<ControllerView>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF59627A))
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        WidgetPreview(views, SizeF(203f, 220f))
        WidgetPreview(views + extraSamples(views), SizeF(285f, 200f))
        for (big in listOf(true, false)) {
            AndroidView(
                factory = { context ->
                    FrameLayout(context).also { frame ->
                        frame.setPadding(40, 30, 40, 30)
                        frame.addView(KeeperNotification.contentView(context, views, big).apply(context, frame))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (big) 120.dp else 64.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFF2F3F7)),
            )
        }
    }
}

@Composable
private fun WidgetPreview(views: List<ControllerView>, size: SizeF) {
    AndroidView(
        // A widget's grid only fills inside an AppWidgetHostView, as on a home screen.
        factory = { context -> AppWidgetHostView(context).apply { updateAppWidget(ControllersWidget.remoteViews(context, views, size)) } },
        modifier = Modifier.size(width = size.width.dp, height = size.height.dp),
    )
}

/** Two more controllers, made from the first sample: a green one on USB and a yellow one. */
private fun extraSamples(views: List<ControllerView>): List<ControllerView> {
    val first = views.firstOrNull() ?: return emptyList()
    val live = first.live ?: return emptyList()
    return listOf(
        Triple(3, 0x00FF2A, BatteryReading(85, ChargeStatus.DISCHARGING)) to Transport.USB,
        Triple(4, 0xFFB000, BatteryReading(5, ChargeStatus.DISCHARGING)) to Transport.BLUETOOTH,
    ).map { (sample, transport) ->
        val (number, color, battery) = sample
        ControllerView(
            profile = first.profile.copy(id = "sample-$number", number = number, color = color, playerId = null),
            player = null,
            live = live.copy(id = "sample-$number", deviceId = 100 + number, transport = transport, battery = battery),
        )
    }
}
