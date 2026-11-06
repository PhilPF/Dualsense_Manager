package com.philpf.dualsensemanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.core.Headphones
import com.philpf.dualsensemanager.core.HeadphonesProblem
import com.philpf.dualsensemanager.core.NoiseMode
import com.philpf.dualsensemanager.core.SonyEqualizer
import com.philpf.dualsensemanager.device.HeadphonesSource
import com.philpf.dualsensemanager.ui.art.BatteryGlyph
import com.philpf.dualsensemanager.ui.art.BoltIcon
import com.philpf.dualsensemanager.ui.art.CloseIcon
import com.philpf.dualsensemanager.ui.art.HeadphonesArt
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
 * A pair of headphones: battery, the noise control (noise cancelling, ambient sound with its
 * level and voice focus, or off), the equalizer's presets, and a button to turn them off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.HeadphonesSheetContent(headphones: Headphones, source: HeadphonesSource, onClose: () -> Unit) {
    val glass = LocalGlass.current
    val noise = headphones.noise

    Box(Modifier.fillMaxWidth()) {
        HeadphonesArt(
            ink = glass.ink,
            glow = if (noise?.mode == NoiseMode.NOISE_CANCELLING) glass.accent.copy(alpha = 0.32f) else androidx.compose.ui.graphics.Color.Transparent,
            modifier = Modifier.align(Alignment.Center).padding(top = 6.dp).width(200.dp),
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
            "Their control channel could not be opened. If Sony's app is running, close it; this app tries again every few seconds."
        headphones.problem == HeadphonesProblem.NO_ANSWER -> "Connected, but they did not answer. Trying again every few seconds."
        !headphones.ready -> "Connecting…"
        else -> null
    }
    if (status != null) {
        Text(status, style = GlassType.callout, color = glass.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        return
    }

    headphones.battery?.let { battery ->
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BatteryGlyph(fraction = battery / 100f, tint = glass.textPrimary.copy(alpha = 0.75f), fill = if (headphones.charging) glass.charging else glass.textPrimary)
            Text("$battery%", style = GlassType.callout, color = glass.textPrimary)
            if (headphones.charging) BoltIcon(glass.charging, Modifier.size(15.dp))
        }
    }

    if (noise != null) {
        Spacer(Modifier.height(24.dp))
        SectionLabel("Noise control")
        val modes = noise.modes
        SegmentedControl(
            options = modes.map(::modeLabel),
            selectedIndex = modes.indexOf(noise.mode),
            onSelect = { source.setNoise(headphones.address, noise.copy(mode = modes[it])) },
        )
        if (noise.mode == NoiseMode.AMBIENT) {
            // The level follows the finger; the headphones get it when let go.
            var level by remember(headphones.address) { mutableIntStateOf(noise.ambientLevel) }
            LaunchedEffect(noise.ambientLevel) { level = noise.ambientLevel }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Level", style = GlassType.body, color = glass.textPrimary, modifier = Modifier.padding(start = 6.dp))
                GlassSlider(
                    value = level,
                    range = 1..20,
                    onChange = { level = it },
                    onDone = { source.setNoise(headphones.address, noise.copy(ambientLevel = it)) },
                    label = "Ambient sound level",
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("$level", style = GlassType.callout, color = glass.textSecondary, modifier = Modifier.width(26.dp), textAlign = TextAlign.End)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voice focus", style = GlassType.body, color = glass.textPrimary, modifier = Modifier.weight(1f).padding(start = 6.dp))
                GlassSwitch(
                    checked = noise.voiceFocus,
                    onCheckedChange = { source.setNoise(headphones.address, noise.copy(voiceFocus = it)) },
                    label = "Voice focus",
                )
            }
        }
    }

    val equalizer = headphones.equalizer
    if (equalizer != null) {
        Spacer(Modifier.height(24.dp))
        SectionLabel("Equalizer")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val shown = SonyEqualizer.entries.filter { it.selectable || it == equalizer }
            for (preset in shown) {
                GlassChip(
                    text = preset.label,
                    selected = preset == equalizer,
                    onClick = { if (preset.selectable) source.setEqualizer(headphones.address, preset) },
                )
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    GlassButton(
        text = "Turn off",
        onClick = {
            source.powerOff(headphones.address)
            onClose()
        },
        textColor = glass.danger,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun modeLabel(mode: NoiseMode): String = when (mode) {
    NoiseMode.NOISE_CANCELLING -> "Cancelling"
    NoiseMode.AMBIENT -> "Ambient"
    NoiseMode.OFF -> "Off"
}
