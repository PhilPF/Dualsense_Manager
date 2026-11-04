package com.philpf.dualsensemanager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.core.ColorMath
import com.philpf.dualsensemanager.core.Hsv
import com.philpf.dualsensemanager.core.LedPalette
import com.philpf.dualsensemanager.ui.art.PlusIcon
import com.philpf.dualsensemanager.ui.glass.Swatch
import com.philpf.dualsensemanager.ui.theme.LocalGlass
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val OPAQUE = 0xFF shl 24

private val Rainbow = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000),
)

/**
 * The preset colours, and a "+" that opens a colour wheel for any other colour.
 * [onPreview] fires while the wheel is dragged, [onPick] when a colour is chosen.
 */
@Composable
fun ColorChooser(
    selected: Int,
    onPreview: (Int) -> Unit,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCustom = LedPalette.nameOf(selected) == null
    var wheelOpen by remember { mutableStateOf(false) }
    Column(modifier) {
        val cells = LedPalette.colors.map { it.name to it.rgb }
        for (row in (cells + listOf("Custom" to selected)).chunked(5)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for ((name, rgb) in row) {
                    if (name == "Custom") {
                        CustomSwatch(
                            color = if (isCustom) Color(OPAQUE or selected) else null,
                            isSelected = isCustom,
                            open = wheelOpen,
                            onClick = { wheelOpen = !wheelOpen },
                        )
                    } else {
                        Swatch(
                            color = Color(OPAQUE or rgb),
                            isSelected = rgb == selected,
                            label = name,
                            onClick = {
                                wheelOpen = false
                                onPick(rgb)
                            },
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = wheelOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                ColorWheel(selected = selected, onChange = onPreview, onChangeFinished = onPick)
            }
        }
    }
}

/** The tenth circle: a rainbow ring with a plus, or the custom colour when one is in use. */
@Composable
private fun CustomSwatch(color: Color?, isSelected: Boolean, open: Boolean, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Other colour"
                this.selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(42.dp)) {
            val outer = size.minDimension / 2f
            if (isSelected || open) {
                drawCircle(glass.textPrimary, radius = outer - 1.dp.toPx(), style = Stroke(1.8.dp.toPx()))
            }
            val r = outer - 5.5.dp.toPx()
            if (color != null) {
                drawCircle(color, radius = r)
            } else {
                drawCircle(Brush.sweepGradient(Rainbow, center), radius = r)
                drawCircle(glass.backgroundBottom.copy(alpha = 0.85f), radius = r * 0.62f)
            }
        }
        if (color == null) PlusIcon(glass.textPrimary, Modifier.size(16.dp))
    }
}

/**
 * A hue and saturation wheel: hue goes round the circle, saturation grows from the white centre
 * to the edge. Brightness stays full, which is what the light bar shows best.
 */
@Composable
fun ColorWheel(
    selected: Int,
    onChange: (Int) -> Unit,
    onChangeFinished: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    val start = remember(selected) { ColorMath.toHsv(selected) }
    var hsv by remember(selected) { mutableStateOf(Hsv(start.hue, start.saturation, 1f)) }
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onChangeFinished)
    Canvas(
        modifier = modifier
            .size(236.dp)
            .semantics { contentDescription = "Colour wheel" }
            .pointerInput(Unit) {
                fun at(position: Offset): Hsv {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val d = position - c
                    val radius = size.width / 2f
                    val hue = Math.toDegrees(atan2(d.y, d.x).toDouble()).toFloat().mod(360f)
                    val saturation = (hypot(d.x, d.y) / radius).coerceIn(0f, 1f)
                    return Hsv(hue, saturation, 1f)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    hsv = at(down.position)
                    change(ColorMath.toRgb(hsv))
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            pointer.consume()
                            break
                        }
                        pointer.consume()
                        hsv = at(pointer.position)
                        change(ColorMath.toRgb(hsv))
                    }
                    finish(ColorMath.toRgb(hsv))
                }
            },
    ) {
        val radius = size.minDimension / 2f
        drawCircle(Brush.sweepGradient(Rainbow, center), radius = radius)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, radius), radius = radius)
        drawCircle(glass.textPrimary.copy(alpha = 0.15f), radius = radius, style = Stroke(1.dp.toPx()))
        val angle = Math.toRadians(hsv.hue.toDouble())
        val distance = hsv.saturation * radius
        val thumb = center + Offset((cos(angle) * distance).toFloat(), (sin(angle) * distance).toFloat())
        drawCircle(Color.Black.copy(alpha = 0.25f), radius = 16.dp.toPx(), center = thumb)
        drawCircle(Color.White, radius = 14.dp.toPx(), center = thumb)
        drawCircle(Color(OPAQUE or ColorMath.toRgb(hsv)), radius = 10.dp.toPx(), center = thumb)
    }
}
