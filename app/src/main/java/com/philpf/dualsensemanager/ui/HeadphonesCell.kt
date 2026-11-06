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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.core.BudPart
import com.philpf.dualsensemanager.core.Headphones
import com.philpf.dualsensemanager.core.HeadphonesKind
import com.philpf.dualsensemanager.core.HeadphonesProblem
import com.philpf.dualsensemanager.core.NoiseControl
import com.philpf.dualsensemanager.core.NoiseMode
import com.philpf.dualsensemanager.ui.art.BatteryGlyph
import com.philpf.dualsensemanager.ui.art.BoltIcon
import com.philpf.dualsensemanager.ui.art.ControllerArtAspect
import com.philpf.dualsensemanager.ui.art.HeadphonesArt
import com.philpf.dualsensemanager.ui.art.PanelLineScale
import com.philpf.dualsensemanager.ui.glass.Backdrop
import com.philpf.dualsensemanager.ui.glass.GlassIconButton
import com.philpf.dualsensemanager.ui.glass.GlassSurface
import com.philpf.dualsensemanager.ui.theme.GlassType
import com.philpf.dualsensemanager.ui.theme.LocalGlass

/**
 * A pair of headphones, as a wide panel or a [square] card: their drawing (the cups lit while
 * noise cancelling or letting ambient sound in), their name, battery and noise control, and a
 * button that steps the noise control as their own button does. Each time [presses] grows
 * (their button or touch panel was used), a wave floods the panel from the drawing, as a
 * controller's does when pressed.
 */
@Composable
fun HeadphonesCell(
    headphones: Headphones,
    backdrop: Backdrop,
    onOpen: () -> Unit,
    onStepNoise: () -> Unit,
    modifier: Modifier = Modifier,
    square: Boolean = false,
    presses: Int = 0,
) {
    val glass = LocalGlass.current
    val waves = rememberPressWaves(presses)
    val ready = headphones.ready
    val noise = headphones.noise
    val glow = when (noise?.mode) {
        NoiseMode.NOISE_CANCELLING -> glass.accent.copy(alpha = 0.32f)
        NoiseMode.AMBIENT -> glass.accent.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    val tint = if (ready) Brush.horizontalGradient(0f to glass.accent.copy(alpha = 0.10f), 0.6f to Color.Transparent) else null
    val art = @Composable { artModifier: Modifier ->
        HeadphonesArt(
            ink = glass.ink,
            glow = glow,
            lineScale = PanelLineScale,
            modifier = artModifier.alpha(if (headphones.connected) 1f else 0.55f),
        )
    }
    val text = @Composable {
        Text(
            text = headphones.name,
            style = GlassType.title,
            color = if (headphones.connected) glass.textPrimary else glass.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(if (square) 6.dp else 8.dp))
        HeadphonesStatus(headphones)
    }
    val button = @Composable { buttonModifier: Modifier ->
        if (ready && noise != null) {
            GlassIconButton(
                onClick = onStepNoise,
                label = "Noise control: ${noiseName(noise.mode, headphones.kind)}",
                size = if (square) 38.dp else 42.dp,
                modifier = buttonModifier,
            ) {
                Text(noiseShort(noise.mode, headphones.kind), style = GlassType.caption.copy(fontWeight = FontWeight.SemiBold), color = glass.textPrimary)
            }
        }
    }
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth().then(if (square) Modifier.fillMaxSize() else Modifier.height(CellHeight)),
        shape = CellShape,
        tint = tint,
        onClick = onOpen,
    ) {
        if (square) {
            Column(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // From the middle of the drawing, below the content and inside the card.
                        val pad = 14.dp.toPx()
                        val artWidth = (size.width - 2 * pad) * 0.62f
                        drawPressWaves(waves, Offset(pad + artWidth / 2f, pad + artWidth / ControllerArtAspect / 2f), glass.accent)
                    }
                    .padding(14.dp),
            ) {
                art(Modifier.fillMaxWidth(0.62f))
                Spacer(Modifier.weight(1f))
                text()
            }
            button(Modifier.align(Alignment.TopEnd).padding(10.dp))
        } else {
            Row(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawPressWaves(waves, Offset(12.dp.toPx() + 56.dp.toPx(), size.height / 2f), glass.accent) }
                    .padding(start = 12.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                art(Modifier.width(112.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { text() }
                Spacer(Modifier.width(8.dp))
                button(Modifier)
            }
        }
    }
}

/** Battery and noise control, or why they are not known. */
@Composable
private fun HeadphonesStatus(headphones: Headphones) {
    val glass = LocalGlass.current
    val status = when {
        !headphones.connected -> "Not connected"
        headphones.problem == HeadphonesProblem.CHANNEL -> "Can't connect"
        headphones.problem == HeadphonesProblem.NO_ANSWER -> "Not answering"
        !headphones.ready -> "Connecting…"
        else -> null
    }
    if (status != null) {
        Text(status, style = GlassType.callout, color = glass.textTertiary, maxLines = 1)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Earbuds: each bud's level, the glyph showing the lower; headphones: their one battery.
        val buds = headphones.parts.filter { it.part != BudPart.CASE }
        val level = buds.minOfOrNull { it.level } ?: headphones.battery
        val charging = if (buds.isEmpty()) headphones.charging else buds.any { it.charging }
        if (level != null) {
            val low = level <= 15 && !charging
            val ink = if (low) glass.danger else glass.textPrimary
            BatteryGlyph(fraction = level / 100f, tint = ink.copy(alpha = 0.75f), fill = if (charging) glass.charging else ink)
            val text = if (buds.isEmpty()) "$level%" else buds.joinToString("  ") { "${partName(it.part)} ${it.level}%" }
            Text(text, style = GlassType.callout, color = ink, maxLines = 1)
            if (charging) BoltIcon(glass.charging, Modifier.size(15.dp))
        }
        headphones.noise?.let {
            Text(noiseLabel(it, headphones.kind), style = GlassType.callout, color = glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The mode's name: Sony's "Ambient sound" is OnePlus's "Transparency". */
fun noiseName(mode: NoiseMode, kind: HeadphonesKind): String = when (mode) {
    NoiseMode.NOISE_CANCELLING -> "Noise cancelling"
    NoiseMode.AMBIENT -> if (kind == HeadphonesKind.ONEPLUS) "Transparency" else "Ambient sound"
    NoiseMode.OFF -> "Off"
}

private fun noiseShort(mode: NoiseMode, kind: HeadphonesKind): String = when (mode) {
    NoiseMode.NOISE_CANCELLING -> "NC"
    NoiseMode.AMBIENT -> if (kind == HeadphonesKind.ONEPLUS) "TRN" else "AMB"
    NoiseMode.OFF -> "OFF"
}

/** "Noise cancelling", "Ambient 12", "Transparency", "Noise control off". */
private fun noiseLabel(control: NoiseControl, kind: HeadphonesKind): String = when (control.mode) {
    NoiseMode.NOISE_CANCELLING -> "Noise cancelling"
    NoiseMode.AMBIENT -> if (kind == HeadphonesKind.ONEPLUS) "Transparency" else "Ambient ${control.ambientLevel}"
    NoiseMode.OFF -> "Noise control off"
}

/** L, R, Case. */
fun partName(part: BudPart): String = when (part) {
    BudPart.LEFT -> "L"
    BudPart.RIGHT -> "R"
    BudPart.CASE -> "Case"
}
