package com.philpf.dualsensemanager.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.ui.theme.GlassType
import com.philpf.dualsensemanager.ui.theme.LocalGlass
import com.philpf.dualsensemanager.ui.theme.contentColorOn

/** Flat capsule button: a solid [accent] fill when [primary], translucent otherwise. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    accent: Color = LocalGlass.current.accent,
    textColor: Color? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val glass = LocalGlass.current
    val fill = if (primary) accent else glass.controlFill
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.invoke()
        Text(
            text = text,
            style = GlassType.headline,
            color = textColor ?: if (primary) contentColorOn(fill) else glass.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Round translucent button holding an icon. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(LocalGlass.current.controlFill)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = GlassType.section,
        color = LocalGlass.current.textTertiary,
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
    val glass = LocalGlass.current
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
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
        val trackTopLeft = Offset(0f, cy - trackH / 2f)
        val trackSize = Size(size.width, trackH)
        drawRoundRect(brush = track, topLeft = trackTopLeft, size = trackSize, cornerRadius = CornerRadius(trackH / 2f))
        drawRoundRect(
            color = glass.outline.takeIf { it.alpha > 0f } ?: Color(0x33FFFFFF),
            topLeft = trackTopLeft,
            size = trackSize,
            cornerRadius = CornerRadius(trackH / 2f),
            style = Stroke(1.dp.toPx()),
        )
        val left = (size.width - thumbW) * value.coerceIn(0f, 1f)
        val halo = 1.5.dp.toPx()
        drawRoundRect(
            color = Color(0x33000000),
            topLeft = Offset(left - halo, cy - thumbH / 2f - halo),
            size = Size(thumbW + 2 * halo, thumbH + 2 * halo),
            cornerRadius = CornerRadius(thumbH / 2f + halo),
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

/** A colour dot; the selected one gets a thin ring. */
@Composable
fun Swatch(
    color: Color,
    isSelected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
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
                drawCircle(glass.textPrimary, radius = outer - 1.dp.toPx(), style = Stroke(1.8.dp.toPx()))
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
            drawCircle(glass.outline.takeIf { it.alpha > 0f } ?: Color(0x33FFFFFF), radius = r, style = Stroke(1.dp.toPx()))
        }
    }
}

/** A selectable capsule with an optional colour dot, e.g. a player. */
@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val glass = LocalGlass.current
    val fill by animateColorAsState(if (selected) glass.controlFillSelected else glass.controlFill, tween(180), label = "chip")
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(fill)
            .then(if (selected) Modifier.border(1.dp, glass.textPrimary.copy(alpha = 0.18f), CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dot != null) {
            Canvas(Modifier.size(10.dp)) { drawCircle(dot) }
        }
        leading?.invoke()
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = GlassType.callout,
                color = glass.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Capsule segmented control; a lighter pill slides to the selected option. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    val position by animateFloatAsState(selectedIndex.toFloat(), tween(240), label = "segment")
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(glass.controlFill)
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size
        Box(
            Modifier
                .offset(x = segment * position)
                .width(segment)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(glass.controlFillSelected),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, option ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(role = Role.Tab) { onSelect(index) }
                        .semantics { selected = index == selectedIndex },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = GlassType.callout,
                        color = if (index == selectedIndex) glass.textPrimary else glass.textSecondary,
                    )
                }
            }
        }
    }
}

/** Flat switch: a translucent track that fills with the accent when on, white capsule thumb. */
@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val progress by animateFloatAsState(if (checked) 1f else 0f, tween(200), label = "switch")
    val track by animateColorAsState(if (checked) glass.accent else glass.track, tween(200), label = "switchTrack")
    Canvas(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(CircleShape)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .semantics {
                contentDescription = label
                stateDescription = if (checked) "On" else "Off"
            },
    ) {
        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2f))
        val inset = 3.dp.toPx()
        val thumbH = size.height - 2 * inset
        val thumbW = thumbH * 1.25f
        val left = inset + (size.width - 2 * inset - thumbW) * progress
        drawRoundRect(
            Color.White,
            topLeft = Offset(left, inset),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbH / 2f),
        )
    }
}

/** A row with a label on the left and a control on the right, inside a grouped panel. */
@Composable
fun SettingRow(label: String, modifier: Modifier = Modifier, control: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = GlassType.body, color = LocalGlass.current.textPrimary, modifier = Modifier.weight(1f))
        control()
    }
}

/** Single-line text field on a translucent capsule. */
@Composable
fun GlassTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val glass = LocalGlass.current
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = GlassType.body.copy(color = glass.textPrimary),
        cursorBrush = SolidColor(glass.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(glass.controlFill)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (value.text.isEmpty()) {
                    Text(placeholder, style = GlassType.body, color = glass.textTertiary)
                }
                inner()
            }
        },
    )
}
