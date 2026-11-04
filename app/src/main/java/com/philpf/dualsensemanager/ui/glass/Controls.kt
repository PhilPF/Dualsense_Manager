package com.philpf.dualsensemanager.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.ui.theme.GlassColors
import com.philpf.dualsensemanager.ui.theme.GlassType
import com.philpf.dualsensemanager.ui.theme.contentColorOn

/** Flat capsule button: a solid [accent] fill when [primary], translucent white otherwise. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    accent: Color = GlassColors.Accent,
    textColor: Color? = null,
) {
    val fill = if (primary) accent else GlassColors.ControlFill
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 46.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = GlassType.headline,
            color = textColor ?: if (primary) contentColorOn(fill) else GlassColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Small translucent capsule for status (battery, connection). */
@Composable
fun StatusChip(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(GlassColors.ControlFill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = GlassType.section,
        color = GlassColors.TextTertiary,
        modifier = modifier.padding(start = 6.dp, bottom = 8.dp),
    )
}

/**
 * Flat slider: a [track] (any brush, e.g. a hue rainbow) with a white capsule thumb.
 * [onValueChange] fires while dragging, [onValueChangeFinished] once the finger lifts.
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    track: Brush,
    label: String,
    modifier: Modifier = Modifier,
) {
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { target ->
                    onChange(target.coerceIn(0f, 1f))
                    onFinished()
                    true
                }
            }
            .pointerInput(Unit) {
                val thumbWidth = ThumbWidth.toPx()
                fun valueAt(x: Float) =
                    ((x - thumbWidth / 2f) / (size.width - thumbWidth).coerceAtLeast(1f)).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    onChange(valueAt(down.position.x))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                        onChange(valueAt(change.position.x))
                    }
                    onFinished()
                }
            },
    ) {
        val thumbW = ThumbWidth.toPx()
        val thumbH = ThumbHeight.toPx()
        val trackH = TrackHeight.toPx()
        val cy = size.height / 2f
        drawRoundRect(
            brush = track,
            topLeft = Offset(0f, cy - trackH / 2f),
            size = Size(size.width, trackH),
            cornerRadius = CornerRadius(trackH / 2f),
        )
        drawRoundRect(
            color = Color(0x33FFFFFF),
            topLeft = Offset(0f, cy - trackH / 2f),
            size = Size(size.width, trackH),
            cornerRadius = CornerRadius(trackH / 2f),
            style = Stroke(1.dp.toPx()),
        )
        val left = (size.width - thumbW) * value.coerceIn(0f, 1f)
        // A soft dark halo keeps the white thumb visible over light track colours.
        drawRoundRect(
            color = Color(0x40000000),
            topLeft = Offset(left - 1.dp.toPx(), cy - thumbH / 2f - 1.dp.toPx()),
            size = Size(thumbW + 2.dp.toPx(), thumbH + 2.dp.toPx()),
            cornerRadius = CornerRadius(thumbH / 2f + 1.dp.toPx()),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, cy - thumbH / 2f),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbH / 2f),
        )
    }
}

private val ThumbWidth = 32.dp
private val ThumbHeight = 22.dp
private val TrackHeight = 10.dp

/** A colour dot; the selected one gets a thin white ring. */
@Composable
fun Swatch(
    color: Color,
    isSelected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(42.dp)) {
            val outer = size.minDimension / 2f
            if (isSelected) {
                drawCircle(Color(0xF5FFFFFF), radius = outer - 1.dp.toPx(), style = Stroke(1.8.dp.toPx()))
            }
            val r = outer - (if (isSelected) 5.5.dp else 4.dp).toPx()
            drawCircle(color, radius = r)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                    center = center - Offset(r * 0.35f, r * 0.35f),
                    radius = r,
                ),
                radius = r,
            )
            drawCircle(Color(0x33FFFFFF), radius = r, style = Stroke(1.dp.toPx()))
        }
    }
}

/** Single-line text field on a translucent capsule. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = GlassType.body.copy(color = GlassColors.TextPrimary),
        cursorBrush = SolidColor(GlassColors.Accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(GlassColors.ControlFill)
                    .border(1.dp, Color(0x14FFFFFF), shape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = GlassType.body, color = GlassColors.TextTertiary)
                }
                inner()
            }
        },
    )
}
