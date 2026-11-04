package com.philpf.dualsensemanager.ui.glass

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Content that glass surfaces can show, blurred, behind themselves.
 *
 * Android has no system glass material, so the content is recorded into a graphics layer
 * ([backdropSource]) and each [GlassSurface] draws that layer again, offset so it lines up
 * with what is really behind the surface, through a blur. The layer is shared, not copied:
 * when the content animates, every glass surface follows without redrawing.
 */
@Stable
class Backdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** Draws this content as usual and records it into [backdrop]. */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/**
 * Flat frosted glass: the [backdrop] blurred (with a little extra saturation so colours don't
 * go grey), a faint white gradient, an optional [tint] and a thin light rim. No shadows or
 * bevels. A surface must not be inside the content of the backdrop it shows.
 */
@Composable
fun GlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val blurLayer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val effect = remember(density) { glassEffect(with(density) { 24.dp.toPx() }) }
    val sheenStopPx = with(density) { 72.dp.toPx() }
    var position by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .onGloballyPositioned { position = it.positionInRoot() }
            .clip(shape)
            .drawBehind {
                val offset = backdrop.origin - position
                blurLayer.renderEffect = effect
                blurLayer.record {
                    translate(offset.x, offset.y) { drawLayer(backdrop.layer) }
                }
                drawLayer(blurLayer)
                val stop = (sheenStopPx / size.height).coerceIn(0.01f, 0.99f)
                drawRect(Brush.verticalGradient(0f to SheenTop, stop to SheenMiddle, 1f to SheenBottom))
                if (tint.alpha > 0f) drawRect(tint)
            }
            .border(1.dp, RimBrush, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

private val SheenTop = Color(0x24FFFFFF)
private val SheenMiddle = Color(0x12FFFFFF)
private val SheenBottom = Color(0x0AFFFFFF)

private val RimBrush = Brush.linearGradient(
    0f to Color(0x4DFFFFFF),
    0.4f to Color(0x14FFFFFF),
    0.65f to Color(0x0AFFFFFF),
    1f to Color(0x2EFFFFFF),
)

private fun glassEffect(radiusPx: Float): RenderEffect {
    val saturate = android.graphics.ColorMatrix().apply { setSaturation(1.35f) }
    return android.graphics.RenderEffect.createChainEffect(
        android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(saturate)),
        android.graphics.RenderEffect.createBlurEffect(radiusPx, radiusPx, android.graphics.Shader.TileMode.CLAMP),
    ).asComposeRenderEffect()
}
