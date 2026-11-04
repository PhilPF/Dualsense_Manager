package com.philpf.dualsensemanager.ui

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
import com.philpf.dualsensemanager.core.ControllerView
import com.philpf.dualsensemanager.service.KeeperNotification
import com.philpf.dualsensemanager.widget.ControllersWidget

/**
 * Debug builds only: the home-screen widget and the notification's views, filled with sample
 * controllers, so screenshots can show them.
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
        AndroidView(
            factory = { context ->
                FrameLayout(context).also { frame -> frame.addView(ControllersWidget.remoteViews(context, views).apply(context, frame)) }
            },
            modifier = Modifier.size(width = 264.dp, height = 192.dp),
        )
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
