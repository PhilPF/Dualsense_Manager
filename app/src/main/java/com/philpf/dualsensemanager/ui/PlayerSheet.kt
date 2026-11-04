package com.philpf.dualsensemanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.core.Player
import com.philpf.dualsensemanager.device.ControllerSource
import com.philpf.dualsensemanager.ui.art.CloseIcon
import com.philpf.dualsensemanager.ui.glass.GlassButton
import com.philpf.dualsensemanager.ui.glass.GlassIconButton
import com.philpf.dualsensemanager.ui.glass.GlassTextField
import com.philpf.dualsensemanager.ui.glass.SectionLabel
import com.philpf.dualsensemanager.ui.theme.GlassType
import com.philpf.dualsensemanager.ui.theme.LocalGlass
import com.philpf.dualsensemanager.ui.theme.onGlass

private const val OPAQUE = 0xFF shl 24
private const val MAX_NAME = 24

/**
 * Edits a player's name and colour, or deletes them; with [player] null, creates a new one for
 * controller [controllerId]. The colour is tried live on [previewOn], the controller that shows it.
 */
@Composable
fun ColumnScope.PlayerSheetContent(
    player: Player?,
    controllerId: String,
    previewOn: String?,
    initialColor: Int,
    defaultName: String,
    source: ControllerSource,
    onDone: () -> Unit,
) {
    val glass = LocalGlass.current
    val key = player?.id ?: "new:$controllerId"
    var name by remember(key) {
        val text = player?.name ?: defaultName
        mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length)))
    }
    var color by remember(key) { mutableIntStateOf(player?.color ?: initialColor) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(key) { if (player == null) focus.requestFocus() }

    fun tryColor(rgb: Int) {
        if (previewOn != null) source.preview(previewOn, rgb)
    }

    Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(18.dp)) { drawCircle(Color(OPAQUE or color)) }
        Spacer(Modifier.width(12.dp))
        Text(if (player == null) "New player" else "Player", style = GlassType.title, modifier = Modifier.weight(1f))
        GlassIconButton(onClick = onDone, label = "Close", size = 36.dp) {
            CloseIcon(glass.textSecondary, Modifier.size(14.dp))
        }
    }
    Spacer(Modifier.height(18.dp))
    GlassTextField(
        value = name,
        onValueChange = { name = it.copy(text = it.text.take(MAX_NAME)) },
        placeholder = "Name",
        focusRequester = focus,
    )

    Spacer(Modifier.height(22.dp))
    SectionLabel("Colour")
    ColorChooser(
        selected = color,
        onPreview = ::tryColor,
        onPick = {
            color = it
            tryColor(it)
        },
    )

    Spacer(Modifier.height(22.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (player != null) {
            GlassButton(
                text = "Delete",
                onClick = {
                    source.deletePlayer(player.id)
                    onDone()
                },
                textColor = glass.danger,
                modifier = Modifier.weight(1f),
            )
        }
        val accent = Color(OPAQUE or color).onGlass(glass)
        GlassButton(
            text = if (player == null) "Add" else "Save",
            onClick = {
                val text = name.text.trim()
                if (player == null) {
                    source.newPlayer(controllerId, text, color)
                } else {
                    source.updatePlayer(player.id, text, color)
                }
                onDone()
            },
            primary = true,
            accent = accent,
            modifier = Modifier.weight(1f),
        )
    }
}
