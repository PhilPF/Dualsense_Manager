package com.philpf.dualsensemanager.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser

/*
 * The headphones are drawn from the illustration of a WH-1000XM4 in Cans
 * (https://github.com/unhingedpanda/cans, Resources/Assets.xcassets/HeadphonesXM4.imageset/
 * xm4.svg): its shapes, in its order and its coordinates, outlined; its shading, highlights and
 * shadows are left out.
 *
 * MIT License
 *
 * Copyright (c) 2026 Mohamed Emad
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

/**
 * Over-ear headphones in lines, in [ink], in the same box as the controller drawing so the two
 * line up, with lines as thick. [glow], when not transparent, lights the cups (the noise
 * control).
 */
@Composable
fun HeadphonesArt(ink: Color, modifier: Modifier = Modifier, glow: Color = Color.Transparent) {
    // A layer of its own, so that each part can clear the lines it hides.
    Canvas(modifier.aspectRatio(ControllerArtAspect).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val s = minOf(size.width / WIDTH, size.height / HEIGHT)
        translate((size.width - WIDTH * s) / 2f, (size.height - HEIGHT * s) / 2f) {
            scale(s, s, pivot = Offset.Zero) {
                translate(-LEFT, -TOP) { drawHeadphones(ink, glow) }
            }
        }
    }
}

// The drawing's frame in the illustration's units, with room below for the near cup's glow.
private const val LEFT = 72f
private const val TOP = -5f
private const val WIDTH = 284f
private const val HEIGHT = 330f

/** The line width: in the same box, as thick as the controller's. */
private const val LINE = 2f

/** How strongly a part is filled with ink, and the far cup's opening. */
private const val FAINT = 0.07f
private const val DEEP = 0.16f

private val Line = Stroke(width = LINE, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val Stitches = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)))

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

private val Band = svgPath("M110,104 C92,38 166,14 220,18 C284,23 332,58 318,112")
private val Pad = svgPath("M120,98 C104,46 166,32 219,35 C274,39 316,68 306,104")
private val PadSeam = svgPath("M122,92 C108,52 166,40 219,43 C270,46 308,72 300,100")
private val Yoke = svgPath("M112,142 C124,146 150,142 176,152")

/** A shape, painted in a colour, a style and a blend mode. */
private fun interface Part {
    fun paint(scope: DrawScope, color: Color, style: DrawStyle, blendMode: BlendMode)
}

/** An ellipse centred at ([cx], [cy]) with semi-axes [a] and [b], turned by [degrees]. */
private fun ellipse(cx: Float, cy: Float, a: Float, b: Float, degrees: Float) = Part { scope, color, style, blendMode ->
    scope.rotate(degrees, pivot = Offset(cx, cy)) {
        drawOval(color, Offset(cx - a, cy - b), Size(2 * a, 2 * b), style = style, blendMode = blendMode)
    }
}

/** A rounded rectangle turned by [degrees] about ([px], [py]). */
private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float, degrees: Float, px: Float, py: Float) =
    Part { scope, color, style, blendMode ->
        scope.rotate(degrees, pivot = Offset(px, py)) {
            drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r), style = style, blendMode = blendMode)
        }
    }

private fun stroked(path: Path) = Part { scope, color, style, blendMode -> scope.drawPath(path, color, style = style, blendMode = blendMode) }

/** A part in front of those drawn before it: clears what it hides, is filled faintly, lit by [lit], and outlined. */
private fun DrawScope.solid(part: Part, ink: Color, lit: Color = Color.Transparent, fill: Float = FAINT) {
    part.paint(this, Color.Black, Fill, BlendMode.Clear)
    part.paint(this, ink.copy(alpha = ink.alpha * fill), Fill, BlendMode.SrcOver)
    if (lit.alpha > 0f) part.paint(this, lit, Fill, BlendMode.SrcOver)
    part.paint(this, ink, Line, BlendMode.SrcOver)
}

/** A thick stroke along [path], [width] wide, in front of what is drawn before it: its two edges, filled faintly. */
private fun DrawScope.tube(path: Path, width: Float, ink: Color) {
    val outer = Stroke(width, cap = StrokeCap.Round)
    val inner = Stroke(width - 2 * LINE, cap = StrokeCap.Round)
    drawPath(path, Color.Black, style = outer, blendMode = BlendMode.Clear)
    drawPath(path, ink, style = outer)
    drawPath(path, Color.Black, style = inner, blendMode = BlendMode.Clear)
    drawPath(path, ink.copy(alpha = ink.alpha * FAINT), style = inner)
}

/** Stitching: a faint dashed line. */
private fun DrawScope.stitches(part: Part, ink: Color) = part.paint(this, ink.copy(alpha = ink.alpha * 0.35f), Stitches, BlendMode.SrcOver)

/** Light around a cup, the ellipse at ([cx], [cy]) with semi-axes [a] and [b]: [glow], fading out just beyond it. */
private fun DrawScope.halo(cx: Float, cy: Float, a: Float, b: Float, degrees: Float, glow: Color) {
    if (glow.alpha == 0f) return
    val center = Offset(cx, cy)
    val r = a * 1.18f
    rotate(degrees, pivot = center) {
        scale(1f, b / a, pivot = center) {
            drawCircle(
                Brush.radialGradient(0f to glow, 0.82f to glow, 1f to glow.copy(alpha = 0f), center = center, radius = r),
                radius = r,
                center = center,
            )
        }
    }
}

/** Back to front, as in the illustration. */
private fun DrawScope.drawHeadphones(ink: Color, glow: Color) {
    // The far cup, its cushion toward us, and the opening with the speaker's holes.
    halo(298f, 210f, 48f, 64f, 12f, glow)
    solid(ellipse(304f, 213f, 49f, 65f, 12f), ink, glow)
    solid(ellipse(295f, 210f, 45f, 61f, 12f), ink, glow)
    stitches(ellipse(295f, 210f, 41f, 57f, 12f), ink)
    solid(ellipse(293f, 212f, 26f, 38f, 12f), ink, glow, fill = DEEP)
    val holes = ink.copy(alpha = ink.alpha * 0.3f)
    for (i in -3..3) {
        for (j in -5..5) {
            val dx = 5f * i
            val dy = 5f * j
            if ((dx / 23f) * (dx / 23f) + (dy / 35f) * (dy / 35f) < 1f) drawCircle(holes, radius = 1f, center = Offset(293f + dx, 212f + dy))
        }
    }

    // The headband, its padded underside and the padding's stitching.
    tube(Band, 34f, ink)
    tube(Pad, 11f, ink)
    stitches(stroked(PadSeam), ink)

    // The sliders, and the yoke down to the near cup.
    solid(roundRect(104f, 104f, 12f, 40f, 2f, -8f, 110f, 104f), ink)
    solid(roundRect(312f, 112f, 12f, 30f, 2f, 12f, 318f, 112f), ink)
    tube(Yoke, 7.5f, ink)

    // The near cup, its outer side toward us: the cushion, the shell's side, and its face with
    // the round panel and a microphone.
    halo(138f, 226f, 64f, 82f, -8f, glow)
    solid(ellipse(162f, 230f, 62f, 80f, -8f), ink, glow)
    stitches(ellipse(159f, 229f, 56f, 74f, -8f), ink)
    solid(ellipse(147f, 228f, 65f, 83f, -8f), ink, glow)
    solid(ellipse(139f, 226f, 65f, 83f, -8f), ink, glow)
    drawCircle(ink, radius = 46f, center = Offset(136f, 232f), style = Line)
    solid(roundRect(160f, 155f, 16f, 5f, 2.5f, 20f, 168f, 158f), ink)
}
