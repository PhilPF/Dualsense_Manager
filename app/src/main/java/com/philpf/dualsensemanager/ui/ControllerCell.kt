package com.philpf.dualsensemanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.core.BatteryReading
import com.philpf.dualsensemanager.core.BatteryForecast
import com.philpf.dualsensemanager.core.BatteryText
import com.philpf.dualsensemanager.core.ChargeStatus
import com.philpf.dualsensemanager.core.ControllerView
import com.philpf.dualsensemanager.core.LiveController
import com.philpf.dualsensemanager.core.RelativeTime
import com.philpf.dualsensemanager.core.Transport
import com.philpf.dualsensemanager.ui.art.BatteryGlyph
import com.philpf.dualsensemanager.ui.art.BluetoothIcon
import com.philpf.dualsensemanager.ui.art.BoltIcon
import com.philpf.dualsensemanager.ui.art.ControllerArt
import com.philpf.dualsensemanager.ui.art.ControllerArtAspect
import com.philpf.dualsensemanager.ui.art.FindIcon
import com.philpf.dualsensemanager.ui.art.LightBarCenter
import com.philpf.dualsensemanager.ui.art.UsbIcon
import com.philpf.dualsensemanager.ui.glass.Backdrop
import com.philpf.dualsensemanager.ui.glass.GlassIconButton
import com.philpf.dualsensemanager.ui.glass.GlassSurface
import com.philpf.dualsensemanager.ui.theme.GlassType
import com.philpf.dualsensemanager.ui.theme.LocalGlass

/** Every cell has this height, whatever it shows. */
val CellHeight = 118.dp
private val CellShape = RoundedCornerShape(30.dp)
private val ArtWidth = 112.dp
private val ArtStart = 12.dp

/**
 * One controller: its drawing lit in its colour, its name (and "DualSense n" under a player's
 * name), battery, charging and connection; a button to make it rumble and blink.
 * [onGlow] reports where its light is, so the background can glow there. Each time [presses]
 * grows (something was pressed on the controller), a wave of its colour floods the cell from
 * the controller outward.
 */
@Composable
fun ControllerCell(
    view: ControllerView,
    light: Color,
    backdrop: Backdrop,
    now: Long,
    onOpen: () -> Unit,
    onFind: () -> Unit,
    onGlow: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    presses: Int = 0,
) {
    val glass = LocalGlass.current
    val live = view.live
    val waves = rememberPressWaves(presses)
    val tint = if (live != null) {
        Brush.horizontalGradient(0f to light.copy(alpha = if (glass.isDark) 0.16f else 0.12f), 0.6f to Color.Transparent)
    } else {
        null
    }
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth().height(CellHeight),
        shape = CellShape,
        tint = tint,
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // From the controller's light bar, below the content and inside the cell.
                    val artWidth = ArtWidth.toPx()
                    val artHeight = artWidth / ControllerArtAspect
                    val origin = Offset(
                        ArtStart.toPx() + artWidth * LightBarCenter.x,
                        (size.height - artHeight) / 2f + artHeight * LightBarCenter.y,
                    )
                    drawPressWaves(waves, origin, light)
                }
                .padding(start = ArtStart, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControllerArt(
                light = light,
                ink = glass.ink,
                lit = live?.lightBar == true,
                modifier = Modifier
                    .width(ArtWidth)
                    .alpha(if (live != null) 1f else 0.55f)
                    .onGloballyPositioned { coords ->
                        if (live != null) {
                            val bounds = coords.boundsInRoot()
                            onGlow(
                                Offset(
                                    bounds.left + bounds.width * LightBarCenter.x,
                                    bounds.top + bounds.height * LightBarCenter.y,
                                ),
                            )
                        }
                    },
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = view.title,
                    style = GlassType.title,
                    color = if (live != null) glass.textPrimary else glass.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                view.subtitle?.let {
                    Text(it, style = GlassType.caption, color = glass.textSecondary, maxLines = 1)
                }
                Spacer(Modifier.height(8.dp))
                if (live != null) LiveStatus(live) else AwayStatus(view, now)
            }
            if (live != null) {
                Spacer(Modifier.width(8.dp))
                GlassIconButton(onClick = onFind, label = "Find ${view.title}") {
                    FindIcon(glass.textPrimary, Modifier.size(22.dp))
                }
            }
        }
    }
}

/**
 * Battery, charging and connection of a connected controller, as icons on one line, with about
 * how long the battery lasts: after the level, or after the bolt for the time to full.
 */
@Composable
fun LiveStatus(live: LiveController, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BatteryLevel(live.battery)
        val status = live.battery?.status
        if (status == ChargeStatus.CHARGING || status == ChargeStatus.FULL) {
            BoltIcon(glass.charging, Modifier.size(15.dp))
        }
        live.eta?.let { eta ->
            Text(
                text = BatteryForecast.short(eta.minutes),
                style = GlassType.callout,
                color = glass.textSecondary,
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = BatteryForecast.spoken(eta) },
            )
        }
        when (live.transport) {
            Transport.BLUETOOTH -> BluetoothIcon(glass.textSecondary, Modifier.size(15.dp))
            Transport.USB -> UsbIcon(glass.textSecondary, Modifier.size(15.dp))
        }
    }
}

@Composable
private fun BatteryLevel(battery: BatteryReading?) {
    val glass = LocalGlass.current
    val percent = battery?.percent
    val charging = battery?.status == ChargeStatus.CHARGING || battery?.status == ChargeStatus.FULL
    val low = percent != null && BatteryText.isLow(percent) && !charging
    val tint = if (low) glass.danger else glass.textPrimary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BatteryGlyph(
            fraction = percent?.let { BatteryText.fraction(it) },
            tint = tint.copy(alpha = 0.75f),
            fill = if (charging) glass.charging else tint,
        )
        Text(BatteryText.level(battery), style = GlassType.callout, color = tint)
    }
}

@Composable
private fun AwayStatus(view: ControllerView, now: Long) {
    val glass = LocalGlass.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val last = view.profile.lastBattery
        if (last >= 0) {
            BatteryGlyph(fraction = BatteryText.fraction(last), tint = glass.textTertiary, fill = glass.textTertiary)
        }
        Text(
            text = RelativeTime.since(now, view.profile.lastSeen),
            style = GlassType.callout,
            color = glass.textTertiary,
            maxLines = 1,
        )
    }
}

/** Shown when no controller has ever connected: a dim controller and a way to pair one. */
@Composable
fun EmptyCell(backdrop: Backdrop, onBluetooth: () -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    GlassSurface(backdrop = backdrop, modifier = modifier.fillMaxWidth().height(CellHeight), shape = CellShape) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControllerArt(light = glass.textTertiary, ink = glass.ink, lit = false, modifier = Modifier.width(ArtWidth).alpha(0.55f))
            Spacer(Modifier.width(14.dp))
            Text("No controllers", style = GlassType.title, color = glass.textSecondary, modifier = Modifier.weight(1f))
            GlassIconButton(onClick = onBluetooth, label = "Bluetooth settings") {
                BluetoothIcon(glass.textPrimary, Modifier.size(20.dp))
            }
        }
    }
}
