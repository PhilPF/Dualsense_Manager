package com.philpf.dualsensemanager.ui.art

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import com.philpf.dualsensemanager.core.BudPart

/*
 * OnePlus Buds Pro 2 and their case, traced from a product photograph (800 x 800 pixels, whose
 * units these are): each part's outline, the ear tips' rings, the stems' joints, the sensors,
 * and the case's lid edge, seam, slot and light.
 */

/**
 * Earbuds and their case in lines, in [ink], in the same box as the controller drawing: only
 * the parts in [shown] (all when it is empty), centred together and at one scale whatever is
 * shown, each fading in or out as it comes and goes. [glow] lights the buds (the noise
 * control). [lineScale] thickens the lines; thickened, the case's lid edge is left out, as it
 * would crowd its seam.
 */
@Composable
fun BudsArt(
    ink: Color,
    shown: Set<BudPart>,
    modifier: Modifier = Modifier,
    glow: Color = Color.Transparent,
    lineScale: Float = 1f,
) {
    val visible = shown.ifEmpty { BudPart.entries.toSet() }
    val left by animateFloatAsState(if (BudPart.LEFT in visible) 1f else 0f, tween(FADE_MS), label = "left")
    val right by animateFloatAsState(if (BudPart.RIGHT in visible) 1f else 0f, tween(FADE_MS), label = "right")
    val case by animateFloatAsState(if (BudPart.CASE in visible) 1f else 0f, tween(FADE_MS), label = "case")
    val centre by animateOffsetAsState(centreOf(visible), tween(FADE_MS), label = "centre")
    Canvas(modifier.aspectRatio(ControllerArtAspect)) {
        val s = minOf(size.width / WIDTH, size.height / HEIGHT)
        translate(size.width / 2f, size.height / 2f) {
            scale(s, s, pivot = Offset.Zero) {
                translate(-centre.x, -centre.y) {
                    drawBud(LeftBud, ink, glow, left, lineScale)
                    drawBud(RightBud, ink, glow, right, lineScale)
                    drawCase(ink, case, lineScale)
                }
            }
        }
    }
}

// The drawing's scale: the whole set with room for the glow, in the photograph's units.
private const val WIDTH = 520f
private const val HEIGHT = 840f

/** The outlines' width in the sheets: in the same box, as thick as the controller's lines. */
private const val LINE = 5.4f

/** The lines inside a part, thinner than its outline. */
private const val INNER = 0.75f

private const val FAINT = 0.07f
private const val FADE_MS = 300

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

private fun stroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** An ellipse centred at ([cx], [cy]) with semi-axes [rx] and [ry], turned by [degrees]. */
private class Oval(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val degrees: Float)

private class Bud(val outline: Path, val tip: Path, val stem: Path, val sensor: Oval, val halo: Oval)

/** Each part's bounds, to centre those shown. */
private val Bounds = mapOf(
    BudPart.LEFT to Rect(207f, 8f, 393f, 257f),
    BudPart.RIGHT to Rect(440f, 131f, 618f, 393f),
    BudPart.CASE to Rect(158f, 427f, 641f, 784f),
)

private fun centreOf(parts: Set<BudPart>): Offset {
    val r = parts.map(Bounds::getValue).reduce { a, b ->
        Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
    }
    return r.center
}

/** The left bud: outline, the ear tip's ring, the stem's joint, the sensor and its glow. */
private val LeftBud = Bud(
    outline = svgPath(
        "M263,8.4C282.1,5.3 302.1,13.6 318,23.4C321.9,25.8 325.8,29.7 330,31.5C333.4,33 334.7,26.9 337.1," +
        "25.6C341,23.4 347.9,25.4 352,26.4C379.8,32.9 396.9,49.5 392.2,79C391,86.2 391.4,92.4 387.6,99C37" +
        "6,113.2 356.1,125.1 337,122.9C331.5,122.3 332.9,115.6 328.9,113.5C326.7,112.3 316.9,119.5 314,12" +
        "0.6C300.4,125.9 281.6,126 280.8,128.1C279.8,130.6 285.4,144.1 286.7,147C294.1,162.5 298.6,179.5 " +
        "306,195C309,202.3 322.3,230.8 320.8,237C317.8,249.4 285.3,262.5 275.2,253.4C270.1,248.7 266.2,23" +
        "4.6 263.6,228C255.6,207.6 247.3,187.4 239.2,167C234.6,157.3 231.4,146.9 227.2,137C209.9,95.8 189" +
        ".6,45.8 238,16.4C246,11.5 254.2,10.4 263,8.4Z",
    ),
    tip = svgPath(
        "M334,26C334.8,27.3 336,28.3 336.8,29.5C339.4,33.7 335,51 334.5,56.5C333.4,69.8 333.1,82.8 " +
        "333.9,96.1C334.4,104.8 335.9,113.4 335,122",
    ),
    stem = svgPath("M230,139C247,145.5 268,144.5 284,134.5"),
    sensor = Oval(225f, 101f, 7f, 5f, -20f),
    halo = Oval(292f, 72f, 100f, 78f, 15f),
)

private val RightBud = Bud(
    outline = svgPath(
        "M544,131.4C589.6,124.1 633.8,163.4 613.2,210C601.1,237.3 585.2,236.7 577.2,247C574.9,250 573.9,2" +
        "54.5 572.4,258C561.9,287.8 552.1,317.8 542.7,348C539.6,357.8 534.9,380.8 528.8,388C519,399.8 493" +
        ".6,389.1 485.2,380C479,370.7 484.9,360.5 488,351C495.2,328.8 501.7,306.2 509,284C510.4,279.6 518" +
        ".9,255.6 516.8,253.2C511.9,247.6 505.6,252.5 500,252.9C490.2,253.7 478.8,251.1 470,246.8C437,230" +
        ".1 428.7,186.6 458,162.2C465.7,155.7 477,151.8 487,151.1C489.9,150.8 493.1,151.5 496,150.8C501.9" +
        ",149.3 510,142.1 516,139.3C524.8,135 534.6,133.6 544,131.4Z",
    ),
    tip = svgPath(
        "M478,152C479.7,154.3 482.1,154.5 481.8,157.2C481.4,160.8 475.5,167.5 473.9,171.6C471.1,178" +
        ".5 468.3,191.8 468.2,199.2C468.2,202.1 469.6,204.6 470,207.4C470.8,212.1 471.1,216.6 472.6" +
        ",221.3C475.1,228.6 483.8,238.9 483.5,246.1C483.4,249.1 482.1,248.6 480,252",
    ),
    stem = svgPath("M516.5,268C534,274.5 556,274 576,262.5"),
    sensor = Oval(562f, 229f, 13f, 9f, -15f),
    halo = Oval(540f, 214f, 100f, 80f, -10f),
)

private val CaseOutline = svgPath(
    "M272,427.4C286.3,425.1 355.2,427 375,427C434.5,427 494.8,424.4 554,429.1C586.2,431.6 619.6,446.4" +
    " 632,478C642.3,508.3 639.1,552.4 640,585C640.6,608.4 643.5,664.3 640.2,685C639.2,690.8 640.1,697" +
    ".1 640,703C639.6,718.1 635.6,737 625.8,749C607.4,771.7 572.4,777.9 545,780.1C539,780.5 533,782 5" +
    "27,782C514.3,782 501.6,783.6 489,784C453.7,784.9 418.3,784 383,784C342.7,784 302.1,785.2 262,782" +
    ".1C234.6,779.9 185.1,770.3 169.8,744.6C167.9,741.4 166.6,737.5 165.2,734C157.1,714.8 158,683.9 1" +
    "58,663C158,638.7 157.4,614.3 158,590C158.5,570.8 160,551.2 160,532C160,505.9 161.6,476.5 180.2,4" +
    "56.1C199.7,434.7 230.9,430.2 258,428.1C262.7,427.7 267.4,428.4 272,427.4Z",
)

/** The lid's front edge, and the seam between lid and base, on either side of the slot. */
private val CaseLid = svgPath(
    "M166,688C182,699.2 180.4,707.5 205.3,717.6C235.7,729.9 280.4,728.1 313,728.1C371,728.1 429,728.8" +
    " 487,728.1C506.7,727.8 528.1,730.5 547.5,726.7C551.7,725.8 554.8,722.7 558.9,721.8C563.9,720.7 5" +
    "73.2,722.7 581,721C602.9,716.4 618.6,702.9 634,688",
)
private val CaseSeamLeft = svgPath("M168,722C178.6,727.6 183.2,736.2 200.3,743.7C225.2,754.6 254.4,754.9 281,755")
private val CaseSeamRight = svgPath("M519,755C560.5,755.9 600,750.6 632,722")

private fun DrawScope.drawBud(bud: Bud, ink: Color, glow: Color, fade: Float, lineScale: Float) {
    if (fade <= 0f) return
    if (glow.alpha > 0f) halo(bud.halo, glow.copy(alpha = glow.alpha * fade))
    val solid = ink.copy(alpha = ink.alpha * fade)
    drawPath(bud.outline, ink.copy(alpha = ink.alpha * FAINT * fade))
    drawPath(bud.outline, solid, style = stroke(LINE * lineScale))
    val inner = stroke(LINE * INNER * lineScale)
    drawPath(bud.tip, solid, style = inner)
    drawPath(bud.stem, solid, style = inner)
    val sensor = bud.sensor
    rotate(sensor.degrees, pivot = Offset(sensor.cx, sensor.cy)) {
        drawOval(
            ink.copy(alpha = ink.alpha * 0.75f * fade),
            Offset(sensor.cx - sensor.rx, sensor.cy - sensor.ry),
            Size(2 * sensor.rx, 2 * sensor.ry),
        )
    }
}

private fun DrawScope.drawCase(ink: Color, fade: Float, lineScale: Float) {
    if (fade <= 0f) return
    val solid = ink.copy(alpha = ink.alpha * fade)
    drawPath(CaseOutline, ink.copy(alpha = ink.alpha * FAINT * fade))
    drawPath(CaseOutline, solid, style = stroke(LINE * lineScale))
    val inner = stroke(LINE * INNER * lineScale)
    if (lineScale <= 1.5f) drawPath(CaseLid, solid, style = inner)
    drawPath(CaseSeamLeft, solid, style = inner)
    drawPath(CaseSeamRight, solid, style = inner)
    // The slot along the front, and the light under it.
    drawRoundRect(solid, Offset(281f, 749f), Size(238f, 12f), CornerRadius(6f), style = inner)
    drawCircle(ink.copy(alpha = ink.alpha * 0.75f * fade), radius = 2.6f * lineScale, center = Offset(400f, 771f))
}

/** Light around a bud: [glow] over its ellipse, fading out just beyond it. */
private fun DrawScope.halo(oval: Oval, glow: Color) {
    val center = Offset(oval.cx, oval.cy)
    val r = oval.rx * 1.18f
    rotate(oval.degrees, pivot = center) {
        scale(1f, oval.ry / oval.rx, pivot = center) {
            val light = Brush.radialGradient(
                0f to glow,
                0.82f to glow,
                1f to glow.copy(alpha = 0f),
                center = center,
                radius = r,
            )
            drawCircle(light, radius = r, center = center)
        }
    }
}
