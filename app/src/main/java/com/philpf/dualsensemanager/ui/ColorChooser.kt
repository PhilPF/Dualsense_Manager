package com.philpf.dualsensemanager.ui

import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.philpf.dualsensemanager.core.ColorMath
import com.philpf.dualsensemanager.core.Hsv
import com.philpf.dualsensemanager.core.LedPalette
import com.philpf.dualsensemanager.ui.art.PlusIcon
import com.philpf.dualsensemanager.ui.glass.GlassButton
import com.philpf.dualsensemanager.ui.glass.Swatch
import com.philpf.dualsensemanager.ui.glass.SwatchDisc
import com.philpf.dualsensemanager.ui.glass.SwatchSize
import com.philpf.dualsensemanager.ui.glass.drawGlassRim
import com.philpf.dualsensemanager.ui.glass.drawSelectionGlow
import com.philpf.dualsensemanager.ui.theme.LocalGlass
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val OPAQUE = 0xFF shl 24

/**
 * The wheel's colours, worked out pixel by pixel with the same conversion that sets the light
 * bar: hue is the angle, saturation the distance from the centre (clamped to 1 outside the
 * circle, so the edge blends cleanly when scaled), brightness full. Drawing it as an image
 * also avoids the seam a sweep gradient leaves at 0°.
 */
private val WheelImage: ImageBitmap by lazy {
    val n = 256
    val half = n / 2f
    val pixels = IntArray(n * n)
    for (y in 0 until n) {
        for (x in 0 until n) {
            val dx = x + 0.5f - half
            val dy = y + 0.5f - half
            val hue = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat().mod(360f)
            val saturation = (sqrt(dx * dx + dy * dy) / half).coerceAtMost(1f)
            pixels[y * n + x] = OPAQUE or ColorMath.toRgb(Hsv(hue, saturation, 1f))
        }
    }
    android.graphics.Bitmap.createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** The hue circle drawn on the "+" button, so it reads as "any colour". */
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
 * The preset colours, and a "+" that opens a colour wheel for any other colour. The "+" always
 * stays a "+"; while a colour of the wheel is in use, it lights up in that colour.
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
        val cells: List<Pair<String, Int>?> = LedPalette.colors.map { it.name to it.rgb } + listOf(null)
        for (row in cells.chunked(5)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (cell in row) {
                    if (cell == null) {
                        AddColorButton(
                            custom = if (isCustom) Color(OPAQUE or selected) else null,
                            onClick = { wheelOpen = true },
                        )
                    } else {
                        val (name, rgb) = cell
                        Swatch(color = Color(OPAQUE or rgb), isSelected = rgb == selected, label = name, onClick = { onPick(rgb) })
                    }
                }
            }
        }
    }
    if (wheelOpen) {
        ColorWheelPopup(selected = selected, onPreview = onPreview, onPick = onPick, onDismiss = { wheelOpen = false })
    }
}

/** The "+": a glass disc with a hue ring. While [custom] is the colour in use, it glows in it. */
@Composable
private fun AddColorButton(custom: Color?, onClick: () -> Unit) {
    val glass = LocalGlass.current
    val lit by animateFloatAsState(if (custom != null) 1f else 0f, tween(220), label = "custom")
    Box(
        modifier = Modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "Other colour"
                selected = custom != null
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(SwatchSize)) {
            custom?.let { drawSelectionGlow(it, lit, glass.textPrimary) }
            val r = SwatchDisc.toPx() / 2f
            drawCircle(glass.controlFill, radius = r)
            drawCircle(Brush.sweepGradient(Rainbow, center), radius = r - 1.25.dp.toPx(), style = Stroke(2.5.dp.toPx()))
            drawGlassRim(r, glass.isDark)
        }
        PlusIcon(glass.textPrimary, Modifier.size(12.dp))
    }
}

/**
 * The colour wheel in a glass card over everything else (blurring it, where the phone allows).
 * Dragging previews on the light bar; lifting the finger picks the colour.
 */
@Composable
private fun ColorWheelPopup(selected: Int, onPreview: (Int) -> Unit, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val glass = LocalGlass.current
    val density = LocalDensity.current
    // The wheel starts from the colour in use when it opened and then follows the finger.
    val initial = remember { selected }
    var current by remember { mutableIntStateOf(selected) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        var blurred by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
            window.setDimAmount(if (glass.isDark) 0.35f else 0.2f)
            if (window.windowManager.isCrossWindowBlurEnabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = with(density) { 28.dp.roundToPx() } }
                blurred = true
            }
        }
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }
        val shape = RoundedCornerShape(34.dp)
        val rim = remember(glass) {
            Brush.linearGradient(0f to glass.rim[0], 0.4f to glass.rim[1], 0.65f to glass.rim[2], 1f to glass.rim[3])
        }
        Column(
            modifier = Modifier
                // The window fades in by itself; the card only settles from slightly smaller.
                .graphicsLayer {
                    val scale = 0.94f + 0.06f * appear.value
                    scaleX = scale
                    scaleY = scale
                }
                .padding(24.dp)
                .clip(shape)
                // Without the blur behind it, the card is less see-through so the wheel stays clear.
                .background(if (blurred) glass.frost else glass.frost.copy(alpha = 0.94f))
                .drawBehind {
                    drawRect(Brush.verticalGradient(0f to glass.sheenTop, 0.3f to glass.sheenMiddle, 1f to glass.sheenBottom))
                }
                .border(1.5.dp, rim, shape)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ColorWheel(
                selected = initial,
                onChange = {
                    current = it
                    onPreview(it)
                },
                onChangeFinished = {
                    current = it
                    onPick(it)
                },
            )
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(SwatchSize)) {
                    val color = Color(OPAQUE or current)
                    drawSelectionGlow(color, 1f, Color.Transparent)
                    drawCircle(color, radius = SwatchDisc.toPx() / 2f)
                    drawGlassRim(SwatchDisc.toPx() / 2f, glass.isDark)
                }
                Spacer(Modifier.width(18.dp))
                GlassButton(text = "Done", onClick = onDismiss, modifier = Modifier.width(140.dp))
            }
        }
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
    val circle = remember { Path() }
    Canvas(
        modifier = modifier
            .size(256.dp)
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
        circle.reset()
        circle.addOval(Rect(center, radius))
        clipPath(circle) {
            drawImage(
                WheelImage,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.High,
            )
        }
        drawCircle(glass.textPrimary.copy(alpha = 0.15f), radius = radius, style = Stroke(1.dp.toPx()))
        val angle = Math.toRadians(hsv.hue.toDouble())
        val distance = hsv.saturation * radius
        val thumb = center + Offset((cos(angle) * distance).toFloat(), (sin(angle) * distance).toFloat())
        drawCircle(Color.Black.copy(alpha = 0.25f), radius = 16.dp.toPx(), center = thumb)
        drawCircle(Color.White, radius = 14.dp.toPx(), center = thumb)
        drawCircle(Color(OPAQUE or ColorMath.toRgb(hsv)), radius = 10.dp.toPx(), center = thumb)
    }
}
