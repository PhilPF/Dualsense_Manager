package com.philpf.dualsensemanager.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.core.Headphones
import com.philpf.dualsensemanager.core.HeadphonesProblem
import com.philpf.dualsensemanager.core.NoiseMode
import com.philpf.dualsensemanager.core.SonyEqualizer
import com.philpf.dualsensemanager.device.DisconnectResult
import com.philpf.dualsensemanager.device.HeadphonesSource
import com.philpf.dualsensemanager.ui.art.BatteryGlyph
import com.philpf.dualsensemanager.ui.art.BluetoothIcon
import com.philpf.dualsensemanager.ui.art.BoltIcon
import com.philpf.dualsensemanager.ui.art.CloseIcon
import com.philpf.dualsensemanager.ui.art.HeadphonesArt
import com.philpf.dualsensemanager.ui.art.PowerIcon
import com.philpf.dualsensemanager.ui.glass.GlassButton
import com.philpf.dualsensemanager.ui.glass.GlassChip
import com.philpf.dualsensemanager.ui.glass.GlassIconButton
import com.philpf.dualsensemanager.ui.glass.GlassSlider
import com.philpf.dualsensemanager.ui.glass.GlassSwitch
import com.philpf.dualsensemanager.ui.glass.SectionLabel
import com.philpf.dualsensemanager.ui.glass.SegmentedControl
import com.philpf.dualsensemanager.ui.theme.GlassType
import com.philpf.dualsensemanager.ui.theme.LocalGlass

/**
 * A pair of headphones: battery; Disconnect and Turn off; the noise control (noise cancelling,
 * ambient sound with its level and voice focus, or off); the volume; and the equalizer's
 * presets, in one row. Every row is always there, faint while it cannot be used, so the sheet
 * keeps its size whatever is chosen.
 */
@Composable
fun ColumnScope.HeadphonesSheetContent(headphones: Headphones, source: HeadphonesSource, onClose: () -> Unit) {
    val glass = LocalGlass.current
    val context = LocalContext.current
    val address = headphones.address
    val noise = headphones.noise
    // The system's "let this app manage the headphones" prompt (Android 17 and later).
    val approval = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}

    Box(Modifier.fillMaxWidth()) {
        HeadphonesArt(
            ink = glass.ink,
            glow = when (noise?.mode) {
                NoiseMode.NOISE_CANCELLING -> glass.accent.copy(alpha = 0.32f)
                NoiseMode.AMBIENT -> glass.accent.copy(alpha = 0.14f)
                else -> Color.Transparent
            },
            modifier = Modifier.align(Alignment.Center).padding(top = 6.dp).width(220.dp),
        )
        GlassIconButton(onClick = onClose, label = "Close", size = 36.dp, modifier = Modifier.align(Alignment.TopEnd)) {
            CloseIcon(glass.textSecondary, Modifier.size(14.dp))
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(headphones.name, style = GlassType.largeTitle, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))

    val status = when {
        !headphones.connected -> "Not connected"
        headphones.problem == HeadphonesProblem.CHANNEL ->
            "Their control channel could not be opened. If Sony's app is running, close it; this app tries again every 10 seconds or so."
        headphones.problem == HeadphonesProblem.NO_ANSWER -> "Connected, but they did not answer. Trying again every 10 seconds or so."
        !headphones.ready -> "Connecting…"
        else -> null
    }
    if (status != null) {
        Text(status, style = GlassType.callout, color = glass.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        return
    }

    val battery = headphones.battery
    Row(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BatteryGlyph(
            fraction = battery?.let { it / 100f },
            tint = glass.textPrimary.copy(alpha = 0.75f),
            fill = if (headphones.charging) glass.charging else glass.textPrimary,
        )
        Text(battery?.let { "$it%" } ?: "–", style = GlassType.callout, color = glass.textPrimary)
        if (headphones.charging) BoltIcon(glass.charging, Modifier.size(15.dp))
    }
    Spacer(Modifier.height(14.dp))
    Row(modifier = Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // From Android 17 the app disconnects them (the first time, Android asks to let it manage
        // them); before, their page in the system settings opens, where "Disconnect" is one tap.
        GlassButton(
            text = "Disconnect",
            onClick = {
                source.disconnect(
                    address,
                    askUser = { approval.launch(IntentSenderRequest.Builder(it).build()) },
                    onResult = { if (it is DisconnectResult.InSettings) openDeviceSettings(context, it.address) },
                )
            },
            icon = { BluetoothIcon(glass.textPrimary, Modifier.size(18.dp)) },
        )
        GlassButton(
            text = "Turn off",
            onClick = {
                source.powerOff(address)
                onClose()
            },
            icon = { PowerIcon(glass.textPrimary, Modifier.size(18.dp)) },
        )
    }

    Spacer(Modifier.height(24.dp))
    SectionLabel("Noise control")
    val modes = noise?.modes ?: NoiseMode.entries
    SegmentedControl(
        options = modes.map(::modeLabel),
        selectedIndex = noise?.let { modes.indexOf(it.mode) } ?: -1,
        onSelect = { i -> noise?.let { source.setNoise(address, it.copy(mode = modes[i])) } },
        enabled = noise != null,
    )
    // The level and voice focus are for ambient sound; always shown, so nothing moves.
    val ambient = noise?.mode == NoiseMode.AMBIENT
    // The level follows the finger; the headphones get it when let go.
    var level by remember(address) { mutableIntStateOf(noise?.ambientLevel?.coerceIn(1, 20) ?: DEFAULT_LEVEL) }
    LaunchedEffect(noise?.ambientLevel) { noise?.ambientLevel?.takeIf { it > 0 }?.let { level = it.coerceIn(1, 20) } }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Level",
            style = GlassType.body,
            color = if (ambient) glass.textPrimary else glass.textTertiary,
            modifier = Modifier.padding(start = 6.dp).width(LABEL_WIDTH),
        )
        GlassSlider(
            value = level,
            range = 1..20,
            onChange = { level = it },
            onDone = { v -> noise?.let { source.setNoise(address, it.copy(ambientLevel = v)) } },
            label = "Ambient sound level",
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            enabled = ambient,
        )
        Text("$level", style = GlassType.callout, color = glass.textSecondary, modifier = Modifier.width(26.dp), textAlign = TextAlign.End)
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Voice focus",
            style = GlassType.body,
            color = if (ambient) glass.textPrimary else glass.textTertiary,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        GlassSwitch(
            checked = noise?.voiceFocus == true,
            onCheckedChange = { v -> noise?.let { source.setNoise(address, it.copy(voiceFocus = v)) } },
            label = "Voice focus",
            enabled = ambient,
        )
    }

    Spacer(Modifier.height(24.dp))
    SectionLabel("Volume")
    // The phone's media volume, theirs while they play the phone's sound; it changes as the finger moves.
    val volume = headphones.volume
    var shown by remember(address) { mutableIntStateOf(volume ?: 0) }
    LaunchedEffect(volume) { volume?.let { shown = it } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassSlider(
            value = shown.coerceIn(0, headphones.maxVolume.coerceAtLeast(1)),
            range = 0..headphones.maxVolume.coerceAtLeast(1),
            onChange = {
                shown = it
                source.setVolume(address, it)
            },
            onDone = { source.setVolume(address, it) },
            label = "Volume",
            modifier = Modifier.weight(1f).padding(end = 4.dp),
            enabled = volume != null,
        )
        Text(if (volume != null) "$shown" else "–", style = GlassType.callout, color = glass.textSecondary, modifier = Modifier.width(26.dp), textAlign = TextAlign.End)
    }

    Spacer(Modifier.height(24.dp))
    SectionLabel("Equalizer")
    // One row that scrolls sideways, opened at the preset in use.
    val equalizer = headphones.equalizer
    val presets = SonyEqualizer.entries.filter { it.selectable || it == equalizer }
    val list = rememberLazyListState()
    LaunchedEffect(equalizer != null) {
        val index = presets.indexOf(equalizer)
        if (index > 0) list.scrollToItem(index - 1)
    }
    LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(presets, key = { it.name }) { preset ->
            GlassChip(
                text = preset.label,
                selected = preset == equalizer,
                onClick = { if (preset.selectable) source.setEqualizer(address, preset) },
            )
        }
    }
}

private val LABEL_WIDTH = 64.dp
private const val DEFAULT_LEVEL = 10

private fun modeLabel(mode: NoiseMode): String = when (mode) {
    NoiseMode.NOISE_CANCELLING -> "Cancelling"
    NoiseMode.AMBIENT -> "Ambient"
    NoiseMode.OFF -> "Off"
}
