package com.philpf.dualsensemanager.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.core.ColorMath
import com.philpf.dualsensemanager.core.ControllerView
import com.philpf.dualsensemanager.core.Hsv
import com.philpf.dualsensemanager.core.LedPalette
import com.philpf.dualsensemanager.core.Player
import com.philpf.dualsensemanager.core.RelativeTime
import com.philpf.dualsensemanager.device.ControllerSource
import com.philpf.dualsensemanager.ui.art.BinIcon
import com.philpf.dualsensemanager.ui.art.CloseIcon
import com.philpf.dualsensemanager.ui.art.ControllerArt
import com.philpf.dualsensemanager.ui.art.FindIcon
import com.philpf.dualsensemanager.ui.art.PlusIcon
import com.philpf.dualsensemanager.ui.glass.GlassButton
import com.philpf.dualsensemanager.ui.glass.GlassChip
import com.philpf.dualsensemanager.ui.glass.GlassIconButton
import com.philpf.dualsensemanager.ui.glass.GlassSlider
import com.philpf.dualsensemanager.ui.glass.GlassTextField
import com.philpf.dualsensemanager.ui.glass.SectionLabel
import com.philpf.dualsensemanager.ui.glass.Swatch
import com.philpf.dualsensemanager.ui.theme.GlassType
import com.philpf.dualsensemanager.ui.theme.LocalGlass
import com.philpf.dualsensemanager.ui.theme.onGlass

private const val OPAQUE = 0xFF shl 24
private const val MAX_NAME = 24

/** One controller: who plays with it and its light colour. */
@Composable
fun ColumnScope.ControllerSheetContent(
    view: ControllerView,
    light: Color,
    players: List<Player>,
    source: ControllerSource,
    now: Long,
    onClose: () -> Unit,
) {
    val glass = LocalGlass.current
    val live = view.live
    var justCreated by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxWidth()) {
        ControllerArt(
            light = light,
            ink = glass.ink,
            lit = live?.lightBar == true,
            modifier = Modifier.align(Alignment.Center).padding(top = 6.dp).width(210.dp),
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
        GlassButton(
            text = "Find",
            onClick = { source.identify(view.id) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            icon = { FindIcon(glass.textPrimary, Modifier.size(20.dp)) },
        )
    } else {
        Text(
            text = RelativeTime.since(now, view.profile.lastSeen),
            style = GlassType.callout,
            color = glass.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
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
                dot = Color(OPAQUE or player.color),
            )
        }
        GlassChip(
            text = "",
            selected = false,
            onClick = { justCreated = source.newPlayer(view.id) },
            leading = { PlusIcon(glass.textPrimary, Modifier.size(16.dp)) },
        )
    }
    view.player?.let { player ->
        Spacer(Modifier.height(10.dp))
        PlayerNameRow(player, justCreated == player.id, source)
    }

    Spacer(Modifier.height(24.dp))
    SectionLabel("Colour")
    ColorEditor(view, source)

    if (live == null) {
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
    }
}

@Composable
private fun PlayerNameRow(player: Player, justCreated: Boolean, source: ControllerSource) {
    val glass = LocalGlass.current
    var field by remember(player.id) {
        mutableStateOf(
            TextFieldValue(
                text = player.name,
                selection = if (justCreated) TextRange(0, player.name.length) else TextRange(player.name.length),
            ),
        )
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(player.id, justCreated) {
        if (justCreated) focus.requestFocus()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassTextField(
            value = field,
            onValueChange = { value ->
                field = value.copy(text = value.text.take(MAX_NAME))
                source.renamePlayer(player.id, field.text.trim())
            },
            placeholder = "Name",
            modifier = Modifier.weight(1f),
            focusRequester = focus,
        )
        Spacer(Modifier.width(10.dp))
        GlassIconButton(onClick = { source.deletePlayer(player.id) }, label = "Delete ${player.name}") {
            BinIcon(glass.danger, Modifier.size(20.dp))
        }
    }
}

/** Preset swatches plus hue, saturation and brightness sliders that update the light bar live. */
@Composable
private fun ColorEditor(view: ControllerView, source: ControllerSource) {
    val glass = LocalGlass.current
    val saved = view.color
    var hsv by remember(view.id) { mutableStateOf(ColorMath.toHsv(saved)) }
    // Follow changes made elsewhere (a preset, another player) without disturbing the hue of the
    // colour being edited (greys have no hue of their own).
    LaunchedEffect(view.id, saved) {
        if (ColorMath.toRgb(hsv) != saved) hsv = ColorMath.toHsv(saved)
    }
    val current = ColorMath.toRgb(hsv)

    Swatches(current) { rgb ->
        hsv = ColorMath.toHsv(rgb)
        source.setColor(view.id, rgb)
    }
    Spacer(Modifier.height(6.dp))

    fun color(h: Float, s: Float, v: Float) = Color(OPAQUE or ColorMath.toRgb(Hsv(h, s, v)))
    val onChange: (Hsv) -> Unit = {
        hsv = it
        source.preview(view.id, ColorMath.toRgb(it))
    }
    val onFinished = { source.setColor(view.id, ColorMath.toRgb(hsv)) }
    GlassSlider(
        value = hsv.hue / 360f,
        onValueChange = { onChange(hsv.copy(hue = it * 359.9f)) },
        onValueChangeFinished = onFinished,
        track = HueBrush,
        label = "Hue",
    )
    GlassSlider(
        value = hsv.saturation,
        onValueChange = { onChange(hsv.copy(saturation = it)) },
        onValueChangeFinished = onFinished,
        track = Brush.horizontalGradient(listOf(color(hsv.hue, 0f, hsv.value), color(hsv.hue, 1f, hsv.value))),
        label = "Saturation",
    )
    GlassSlider(
        value = hsv.value,
        onValueChange = { onChange(hsv.copy(value = it)) },
        onValueChangeFinished = onFinished,
        track = Brush.horizontalGradient(listOf(Color.Black, color(hsv.hue, hsv.saturation, 1f))),
        label = "Brightness",
    )
    if (view.player == null && view.profile.customColor) {
        Spacer(Modifier.height(10.dp))
        GlassButton(
            text = "Automatic",
            onClick = { source.useAutomaticColor(view.id) },
            modifier = Modifier.fillMaxWidth(),
            textColor = Color(OPAQUE or saved).onGlass(glass),
        )
    }
}

/** The palette in rows of five, plus the current colour when it is a custom one. */
@Composable
private fun Swatches(selectedRgb: Int, onPick: (Int) -> Unit) {
    val presets = LedPalette.colors.map { it.name to it.rgb }
    val custom = if (LedPalette.nameOf(selectedRgb) == null) listOf("Custom" to selectedRgb) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (chunk in (presets + custom).chunked(5)) {
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
