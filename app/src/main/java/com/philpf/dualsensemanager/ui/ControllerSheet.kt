package com.philpf.dualsensemanager.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.core.ControllerPart
import com.philpf.dualsensemanager.core.ControllerView
import com.philpf.dualsensemanager.core.Player
import com.philpf.dualsensemanager.core.RelativeTime
import com.philpf.dualsensemanager.core.Sticks
import com.philpf.dualsensemanager.device.ControllerSource
import com.philpf.dualsensemanager.device.DisconnectResult
import com.philpf.dualsensemanager.ui.art.CloseIcon
import com.philpf.dualsensemanager.ui.art.ControllerArt
import com.philpf.dualsensemanager.ui.art.FindIcon
import com.philpf.dualsensemanager.ui.art.PlusIcon
import com.philpf.dualsensemanager.ui.art.PowerIcon
import com.philpf.dualsensemanager.ui.glass.GlassButton
import com.philpf.dualsensemanager.ui.glass.GlassChip
import com.philpf.dualsensemanager.ui.glass.GlassIconButton
import com.philpf.dualsensemanager.ui.glass.SectionLabel
import com.philpf.dualsensemanager.ui.theme.GlassType
import com.philpf.dualsensemanager.ui.theme.LocalGlass
import com.philpf.dualsensemanager.ui.theme.onGlass

private const val OPAQUE = 0xFF shl 24

/**
 * One controller: who plays with it (tap a player to give them this controller, long-press to
 * edit them, + to add one) and its light colour. Its drawing shows the buttons being pressed
 * and where the sticks are. A controller that is not connected shows when it was last seen and
 * can only be forgotten.
 */
@Composable
fun ColumnScope.ControllerSheetContent(
    view: ControllerView,
    light: Color,
    players: List<Player>,
    pressed: Set<ControllerPart>,
    sticks: () -> Sticks?,
    source: ControllerSource,
    now: Long,
    onEditPlayer: (Player) -> Unit,
    onNewPlayer: () -> Unit,
    onClose: () -> Unit,
) {
    val glass = LocalGlass.current
    val live = view.live
    val context = LocalContext.current
    // The system's "let this app manage the controller" prompt; the answer comes back to source.disconnect.
    val approval = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}

    Box(Modifier.fillMaxWidth()) {
        ControllerArt(
            light = light,
            ink = glass.ink,
            lit = live?.lightBar == true,
            pressed = pressed,
            sticks = sticks,
            modifier = Modifier.align(Alignment.Center).padding(top = 6.dp).width(220.dp),
        )
        GlassIconButton(onClick = onClose, label = "Close", size = 36.dp, modifier = Modifier.align(Alignment.TopEnd)) {
            CloseIcon(glass.textSecondary, Modifier.size(14.dp))
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(view.title, style = GlassType.largeTitle, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    view.subtitle?.let {
        Text(it, style = GlassType.caption, color = glass.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(10.dp))
    if (live != null) {
        LiveStatus(live, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassButton(
                text = "Find",
                onClick = { source.identify(view.id) },
                icon = { FindIcon(glass.textPrimary, Modifier.size(20.dp)) },
            )
            if (source.canDisconnect(view.id)) {
                // Disconnecting turns the controller off. From Android 17 the app does it (the first
                // time, Android asks to let it manage this controller); before, the controller's page
                // in the system settings opens, where "Disconnect" is one tap.
                GlassButton(
                    text = "Disconnect",
                    onClick = {
                        source.disconnect(
                            view.id,
                            askUser = { approval.launch(IntentSenderRequest.Builder(it).build()) },
                            onResult = { if (it is DisconnectResult.InSettings) openDeviceSettings(context, it.address) },
                        )
                    },
                    icon = { PowerIcon(glass.textPrimary, Modifier.size(18.dp)) },
                )
            }
        }
    } else {
        Text(
            text = RelativeTime.since(now, view.profile.lastSeen),
            style = GlassType.callout,
            color = glass.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (live == null) {
        // A controller that is not connected keeps its settings until it is back; it can only
        // be forgotten.
        Spacer(Modifier.height(22.dp))
        GlassButton(
            text = "Forget",
            onClick = {
                source.forget(view.id)
                onClose()
            },
            textColor = glass.danger,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    Spacer(Modifier.height(24.dp))
    SectionLabel("Player")
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassChip(text = "None", selected = view.player == null, onClick = { source.assignPlayer(view.id, null) })
        for (player in players) {
            GlassChip(
                text = player.name.ifBlank { "…" },
                selected = view.player?.id == player.id,
                onClick = { source.assignPlayer(view.id, player.id) },
                onLongClick = { onEditPlayer(player) },
                dot = Color(OPAQUE or player.color),
            )
        }
        GlassChip(
            text = "",
            selected = false,
            onClick = onNewPlayer,
            leading = { PlusIcon(glass.textPrimary, Modifier.size(16.dp)) },
        )
    }

    Spacer(Modifier.height(24.dp))
    SectionLabel("Colour")
    ColorChooser(
        selected = view.color,
        onPreview = { source.preview(view.id, it) },
        onPick = { source.setColor(view.id, it) },
    )
    // Always there, so the sheet keeps its size; it works while the colour was picked by hand.
    Spacer(Modifier.height(12.dp))
    GlassButton(
        text = "Automatic",
        onClick = { source.useAutomaticColor(view.id) },
        modifier = Modifier.fillMaxWidth(),
        textColor = Color(OPAQUE or view.color).onGlass(glass),
        enabled = view.player == null && view.profile.customColor,
    )
}
