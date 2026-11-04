package com.philpf.dualsensemanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.core.BatteryText
import com.philpf.dualsensemanager.core.ColorMath
import com.philpf.dualsensemanager.core.ControllerRow
import com.philpf.dualsensemanager.core.Hsv
import com.philpf.dualsensemanager.core.LedPalette
import com.philpf.dualsensemanager.core.RelativeTime
import com.philpf.dualsensemanager.device.ControllerManager
import com.philpf.dualsensemanager.ui.art.ControllerArt
import com.philpf.dualsensemanager.ui.glass.Backdrop
import com.philpf.dualsensemanager.ui.glass.GlassButton
import com.philpf.dualsensemanager.ui.glass.GlassSlider
import com.philpf.dualsensemanager.ui.glass.GlassSurface
import com.philpf.dualsensemanager.ui.glass.GlassTextField
import com.philpf.dualsensemanager.ui.glass.SectionLabel
import com.philpf.dualsensemanager.ui.glass.Swatch
import com.philpf.dualsensemanager.ui.theme.GlassColors
import com.philpf.dualsensemanager.ui.theme.GlassType
import com.philpf.dualsensemanager.ui.theme.forDisplay
import kotlin.math.roundToInt

/**
 * Everything about one controller: nickname, light-bar colour (presets plus hue, saturation and
 * brightness sliders that update the real light bar live), battery and connection.
 */
@Composable
fun ControllerSheet(
    row: ControllerRow,
    light: Color,
    backdrop: Backdrop,
    manager: ControllerManager,
    now: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val id = row.id
    val profile = row.profile
    val live = row.live

    var nickname by remember(id) { mutableStateOf(profile.nickname) }
    var hsv by remember(id) { mutableStateOf(ColorMath.toHsv(profile.color)) }
    // Follow colour changes made elsewhere (preset, automatic colour) without disturbing the hue
    // of the colour being edited (greys have no hue of their own).
    LaunchedEffect(id, profile.color) {
        if (ColorMath.toRgb(hsv) != profile.color) hsv = ColorMath.toHsv(profile.color)
    }
    val currentRgb = ColorMath.toRgb(hsv)
    val accent = light.forDisplay()
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp

    GlassSurface(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight),
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 20.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 38.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(Color(0x40FFFFFF)),
            )
            Spacer(Modifier.height(12.dp))
            ControllerArt(
                light = light,
                lit = live?.lightBar == true,
                modifier = Modifier.align(Alignment.CenterHorizontally).width(220.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = profile.title,
                style = GlassType.largeTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (profile.nickname.isBlank()) profile.kind.label else profile.defaultName,
                style = GlassType.caption,
                color = GlassColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (live != null) {
                StatusChips(live, Modifier.align(Alignment.CenterHorizontally))
            } else {
                Text(
                    text = "Not connected · seen ${RelativeTime.since(now, profile.lastSeen)}",
                    style = GlassType.caption,
                    color = GlassColors.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Nickname")
            GlassTextField(
                value = nickname,
                onValueChange = { value ->
                    nickname = value.take(MAX_NICKNAME)
                    manager.setNickname(id, nickname.trim())
                },
                placeholder = "e.g. Ana, Guest, Player 2",
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("Light bar colour")
            if (live != null && !live.lightBar) {
                Text(
                    text = "This phone doesn't let apps change this controller's light bar, so the colour is only used in the app.",
                    style = GlassType.caption,
                    color = GlassColors.TextSecondary,
                    modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
                )
            }
            PresetSwatches(currentRgb) { rgb ->
                hsv = ColorMath.toHsv(rgb)
                manager.setColor(id, rgb)
            }
            Spacer(Modifier.height(10.dp))
            ColorSliders(
                hsv = hsv,
                onChange = {
                    hsv = it
                    manager.preview(id, ColorMath.toRgb(it))
                },
                onFinished = { manager.setColor(id, ColorMath.toRgb(hsv)) },
            )
            Text(
                text = (LedPalette.nameOf(currentRgb)?.let { "$it · " } ?: "Custom · ") + ColorMath.hex(currentRgb) +
                    if (profile.customColor) "" else " · automatic",
                style = GlassType.caption,
                color = GlassColors.TextTertiary,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp),
            )

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (profile.customColor) {
                    GlassButton("Automatic colour", onClick = { manager.useAutomaticColor(id) }, modifier = Modifier.weight(1f))
                }
                GlassButton("Done", onClick = onClose, primary = true, accent = accent, modifier = Modifier.weight(1f))
            }

            val percent = live?.battery?.percent
            if (percent != null && BatteryText.range(percent) != null) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "The controller reports its battery in 10% steps, so ${BatteryText.level(percent)} " +
                        "means the charge is somewhere in that range.",
                    style = GlassType.caption,
                    color = GlassColors.TextTertiary,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            if (live == null) {
                Spacer(Modifier.height(12.dp))
                GlassButton(
                    text = "Forget this controller",
                    onClick = {
                        manager.forget(id)
                        onClose()
                    },
                    textColor = GlassColors.Danger,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Frees its number and colour. It gets new ones the next time it connects.",
                    style = GlassType.caption,
                    color = GlassColors.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

private const val MAX_NICKNAME = 24
private const val OPAQUE = 0xFF shl 24

/** The palette in two rows, plus the current colour when it is a custom one. */
@Composable
private fun PresetSwatches(selectedRgb: Int, onPick: (Int) -> Unit) {
    val presets = LedPalette.colors.map { it.name to it.rgb }
    val custom = if (LedPalette.nameOf(selectedRgb) == null) listOf("Custom" to selectedRgb) else emptyList()
    val all = presets + custom
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (chunk in all.chunked(5)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (slot in 0 until 5) {
                    val entry = chunk.getOrNull(slot)
                    if (entry == null) {
                        Spacer(Modifier.size(46.dp))
                    } else {
                        Swatch(
                            color = Color(OPAQUE or entry.second),
                            isSelected = entry.second == selectedRgb,
                            label = entry.first,
                            onClick = { onPick(entry.second) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSliders(hsv: Hsv, onChange: (Hsv) -> Unit, onFinished: () -> Unit) {
    fun color(h: Float, s: Float, v: Float) = Color(OPAQUE or ColorMath.toRgb(Hsv(h, s, v)))

    SliderCaption("Hue", "${hsv.hue.roundToInt()}°")
    GlassSlider(
        value = hsv.hue / 360f,
        onValueChange = { onChange(hsv.copy(hue = it * 359.9f)) },
        onValueChangeFinished = onFinished,
        track = HueBrush,
        label = "Hue",
    )
    SliderCaption("Saturation", "${(hsv.saturation * 100).roundToInt()}%")
    GlassSlider(
        value = hsv.saturation,
        onValueChange = { onChange(hsv.copy(saturation = it)) },
        onValueChangeFinished = onFinished,
        track = Brush.horizontalGradient(listOf(color(hsv.hue, 0f, hsv.value), color(hsv.hue, 1f, hsv.value))),
        label = "Saturation",
    )
    SliderCaption("Brightness", "${(hsv.value * 100).roundToInt()}%")
    GlassSlider(
        value = hsv.value,
        onValueChange = { onChange(hsv.copy(value = it)) },
        onValueChangeFinished = onFinished,
        track = Brush.horizontalGradient(listOf(Color.Black, color(hsv.hue, hsv.saturation, 1f))),
        label = "Brightness",
    )
}

@Composable
private fun SliderCaption(name: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).padding(top = 8.dp)) {
        Text(name, style = GlassType.caption, color = GlassColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = GlassType.caption, color = GlassColors.TextTertiary)
    }
}

private val HueBrush = Brush.horizontalGradient(
    listOf(
        Color(0xFFFF0000),
        Color(0xFFFFFF00),
        Color(0xFF00FF00),
        Color(0xFF00FFFF),
        Color(0xFF0000FF),
        Color(0xFFFF00FF),
        Color(0xFFFF0000),
    ),
)
