package com.philpf.dualsensemanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.core.BatteryReading
import com.philpf.dualsensemanager.core.BatteryText
import com.philpf.dualsensemanager.core.ChargeStatus
import com.philpf.dualsensemanager.core.ControllerRow
import com.philpf.dualsensemanager.core.LiveController
import com.philpf.dualsensemanager.core.RelativeTime
import com.philpf.dualsensemanager.core.Transport
import com.philpf.dualsensemanager.ui.art.BatteryGlyph
import com.philpf.dualsensemanager.ui.art.BluetoothIcon
import com.philpf.dualsensemanager.ui.art.BoltIcon
import com.philpf.dualsensemanager.ui.art.ChevronIcon
import com.philpf.dualsensemanager.ui.art.ControllerArt
import com.philpf.dualsensemanager.ui.art.UsbIcon
import com.philpf.dualsensemanager.ui.glass.Backdrop
import com.philpf.dualsensemanager.ui.glass.GlassSurface
import com.philpf.dualsensemanager.ui.glass.StatusChip
import com.philpf.dualsensemanager.ui.theme.GlassColors
import com.philpf.dualsensemanager.ui.theme.GlassType

/** A connected controller: its light colour, name, battery and connection. */
@Composable
fun ControllerCard(row: ControllerRow, light: Color, backdrop: Backdrop, onClick: () -> Unit) {
    val live = row.live ?: return
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        tint = light.copy(alpha = 0.07f),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControllerArt(light = light, lit = live.lightBar, modifier = Modifier.width(100.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(row.profile.title, style = GlassType.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (row.profile.nickname.isBlank()) "Tap to add a nickname" else row.profile.defaultName,
                    style = GlassType.caption,
                    color = GlassColors.TextSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(10.dp))
                StatusChips(live)
            }
            ChevronIcon(GlassColors.TextTertiary, Modifier.size(18.dp))
        }
    }
}

/** Battery level, charging state and connection type as small capsules. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusChips(live: LiveController, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BatteryChip(live.battery)
        val status = live.battery?.status
        if (status == ChargeStatus.CHARGING || status == ChargeStatus.FULL) {
            StatusChip {
                BoltIcon(GlassColors.Charging, Modifier.size(13.dp))
                Text(
                    if (status == ChargeStatus.CHARGING) "Charging" else "Charged",
                    style = GlassType.caption,
                    color = GlassColors.Charging,
                )
            }
        }
        TransportChip(live.transport)
    }
}

@Composable
fun BatteryChip(battery: BatteryReading?) {
    val percent = battery?.percent
    val low = percent != null && BatteryText.isLow(percent) && battery?.status != ChargeStatus.CHARGING
    val tint = if (low) GlassColors.Danger else GlassColors.TextPrimary
    StatusChip {
        BatteryGlyph(
            fraction = percent?.let { it / 100f },
            tint = tint.copy(alpha = 0.8f),
            fill = if (battery?.status == ChargeStatus.CHARGING) GlassColors.Charging else tint,
            modifier = Modifier.size(width = 22.dp, height = 11.dp),
        )
        Text(
            text = when {
                battery == null -> "No battery info"
                percent == null -> "Reading…"
                else -> BatteryText.level(percent)
            },
            style = GlassType.caption,
            color = tint,
        )
    }
}

@Composable
fun TransportChip(transport: Transport) {
    StatusChip {
        when (transport) {
            Transport.BLUETOOTH -> BluetoothIcon(GlassColors.TextSecondary, Modifier.size(13.dp))
            Transport.USB -> UsbIcon(GlassColors.TextSecondary, Modifier.size(13.dp))
        }
        Text(
            if (transport == Transport.USB) "USB cable" else "Bluetooth",
            style = GlassType.caption,
            color = GlassColors.TextSecondary,
        )
    }
}

/** The remembered controllers that are not connected, grouped on one glass panel. */
@Composable
fun AwayGroup(rows: List<ControllerRow>, colorOf: (ControllerRow) -> Color, backdrop: Backdrop, now: Long, onClick: (ControllerRow) -> Unit) {
    GlassSurface(backdrop = backdrop, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Canvas(Modifier.fillMaxWidth().height(1.dp).padding(start = 58.dp)) {
                        drawRect(GlassColors.Hairline)
                    }
                }
                AwayRow(row, colorOf(row), now) { onClick(row) }
            }
        }
    }
}

@Composable
private fun AwayRow(row: ControllerRow, light: Color, now: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(26.dp).alpha(0.9f)) {
            val r = size.minDimension / 2f
            drawCircle(light.copy(alpha = 0.22f), radius = r)
            drawCircle(light, radius = r * 0.42f)
            drawCircle(Color(0x33FFFFFF), radius = r, style = Stroke(1.dp.toPx()))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(row.profile.title, style = GlassType.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val battery = row.profile.lastBattery.takeIf { it >= 0 }?.let { " · last battery ${BatteryText.level(it)}" } ?: ""
            Text(
                text = "Seen ${RelativeTime.since(now, row.profile.lastSeen)}$battery",
                style = GlassType.caption,
                color = GlassColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ChevronIcon(GlassColors.TextTertiary, Modifier.size(16.dp))
    }
}
