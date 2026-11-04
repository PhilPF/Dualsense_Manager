package com.philpf.dualsensemanager.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser

/*
 * The controller drawing is adapted from design/dualsense.svg of daidr/dualsense-tester
 * (https://github.com/daidr/dualsense-tester): the PlayStation logo, the triggers and the
 * touch markers were left out, a plain PS button and the light strips were added.
 *
 * MIT License
 *
 * Copyright (c) 2023 Xuezhou Dai (daidr)
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
 * A DualSense seen from the front, drawn in [ink], with the light strips beside the touchpad
 * glowing in [light]. When [lit] is false the strips show the colour faintly, without the glow.
 */
@Composable
fun ControllerArt(light: Color, ink: Color, lit: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(ART_WIDTH / ART_HEIGHT)) {
        val s = size.width / ART_WIDTH
        scale(s, s, pivot = Offset.Zero) {
            translate(-ART_LEFT, -ART_TOP) { drawController(light, ink, lit) }
        }
    }
}

/** Where the light bar sits within the drawing, as fractions of its width and height. */
val LightBarCenter = Offset((559f - ART_LEFT) / ART_WIDTH, (270f - ART_TOP) / ART_HEIGHT)

private const val ART_LEFT = 2f
private const val ART_TOP = 132f
private const val ART_WIDTH = 1113f
private const val ART_HEIGHT = 758f

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

private val OutlinePaths: List<Path> = listOf(
    svgPath(
        "M917.682,411.683l32.795,32.795m-0,-32.795l-32.795,32.795",
    ),
    svgPath(
        "M847.313,341.648h32.865v32.865h-32.865Z",
    ),
    svgPath(
        "M934.079,271.403l20.206,33.355l-40.411,-0l20.205,-33.355Z",
    ),
    svgPath(
        "M984.98,358.08a19.1,19.1 0 1,0 38.2,0a19.1,19.1 0 1,0 -38.2,0Z",
    ),
    svgPath(
        "M213.487,271.996c0,-11.214 -9.091,-20.305 -20.305,-20.305l-28.888,-0c-11.215,-0 -20.306,9.091 -2" +
        "0.306,20.305l0,24.336c0,6.286 2.397,12.335 6.703,16.915c6.856,7.293 17.331,18.436 23.351,24.838c" +
        "1.218,1.296 2.917,2.031 4.696,2.031c1.778,-0 3.478,-0.735 4.696,-2.031c6.019,-6.402 16.494,-17.5" +
        "45 23.35,-24.838c4.306,-4.58 6.703,-10.629 6.703,-16.915c0,-6.766 0,-16.013 0,-24.336Z",
    ),
    svgPath(
        "M213.487,443.093c0,11.215 -9.091,20.306 -20.305,20.306l-28.888,-0c-11.215,-0 -20.306,-9.091 -20." +
        "306,-20.306l0,-24.125c0,-6.412 2.494,-12.572 6.953,-17.178c6.884,-7.11 17.23,-17.796 23.166,-23." +
        "927c1.214,-1.254 2.885,-1.962 4.631,-1.962c1.745,0 3.416,0.708 4.63,1.962c5.936,6.131 16.282,16." +
        "817 23.166,23.927c4.46,4.606 6.953,10.766 6.953,17.178c0,6.746 0,15.888 0,24.125Z",
    ),
    svgPath(
        "M263.75,322.259c11.215,0 20.306,9.091 20.306,20.306l-0,28.888c-0,11.214 -9.091,20.305 -20.306,20" +
        ".305l-24.349,0c-6.278,0 -12.32,-2.391 -16.899,-6.687c-7.081,-6.644 -17.749,-16.654 -23.907,-22.4" +
        "33c-1.274,-1.195 -2.008,-2.855 -2.035,-4.601c-0.027,-1.746 0.656,-3.428 1.892,-4.661c6.106,-6.09" +
        "4 16.81,-16.778 23.946,-23.901c4.63,-4.621 10.905,-7.216 17.446,-7.216c6.724,0 15.758,0 23.906,0" +
        "Z",
    ),
    svgPath(
        "M93.725,322.259c-11.214,0 -20.306,9.091 -20.306,20.306l0,28.888c0,11.214 9.092,20.305 20.306,20." +
        "305l24.349,0c6.278,0 12.321,-2.391 16.899,-6.687c7.081,-6.644 17.749,-16.654 23.908,-22.433c1.27" +
        "3,-1.195 2.007,-2.855 2.034,-4.601c0.027,-1.746 -0.656,-3.428 -1.891,-4.661c-6.106,-6.094 -16.81" +
        ",-16.778 -23.947,-23.901c-4.63,-4.621 -10.904,-7.216 -17.446,-7.216c-6.724,0 -15.757,0 -23.906,0" +
        "Z",
    ),
    svgPath(
        "M899.122,428.08a34.957,34.957 0 1,0 69.914,0a34.957,34.957 0 1,0 -69.914,0Z",
    ),
    svgPath(
        "M829.122,358.08a34.957,34.957 0 1,0 69.914,0a34.957,34.957 0 1,0 -69.914,0Z",
    ),
    svgPath(
        "M969.123,358.08a34.957,34.957 0 1,0 69.914,0a34.957,34.957 0 1,0 -69.914,0Z",
    ),
    svgPath(
        "M899.122,288.08a34.957,34.957 0 1,0 69.914,0a34.957,34.957 0 1,0 -69.914,0Z",
    ),
    svgPath(
        "M100.97,881.749c8.551,1.607 62.356,4.094 68.38,-3.153c33.93,-40.827 69.521,-154.237 85.416,-196." +
        "14c9.791,-25.813 35.4,-37.881 67.491,-40.687c30.597,13.569 45.149,13.982 96.708,3.594l280.228,-0" +
        "c51.559,10.388 66.111,9.975 96.708,-3.594c32.091,2.806 57.701,14.874 67.492,40.687c15.894,41.903" +
        " 51.486,155.313 85.416,196.14c6.023,7.247 59.828,4.76 68.38,3.153",
    ),
    svgPath(
        "M282.527,168.559c13.841,1.794 27.682,-1.16 41.522,-6.511",
    ),
    svgPath(
        "M590.061,591.266c-0,-3.699 -2.999,-6.698 -6.698,-6.698c-11.914,0 -36.653,0 -48.567,0c-3.7,0 -6.6" +
        "98,2.999 -6.698,6.698c-0,0.001 -0,0.001 -0,0.002c-0,3.699 2.998,6.698 6.698,6.698c11.914,-0 36.6" +
        "53,-0 48.567,-0c3.699,-0 6.698,-2.999 6.698,-6.698c-0,-0.001 -0,-0.001 -0,-0.002Z",
    ),
    svgPath(
        "M835.33,168.559c-13.84,1.794 -27.681,-1.16 -41.522,-6.511",
    ),
    svgPath(
        "M820.718,193.129c5.479,-25.983 25.744,-37.755 62.283,-33.901c40.742,4.297 79.814,11.918 115.424," +
        "22.309c11.905,3.474 23.486,8.273 28.39,18.67c91.01,192.963 106.846,408.796 55.912,643.084c-2.947" +
        ",13.555 -9.046,24.221 -21.05,32.104c-5.842,3.836 -12.586,7.06 -21.885,9.445c-21.563,5.529 -31.04" +
        "1,-9.866 -33.637,-23.758c-33.415,-178.821 -88.678,-342.348 -191.662,-450.233c-9.318,-9.761 -18.1" +
        "81,-31.681 -20.635,-51.733c-0.97,-7.926 -0.451,-15.333 0.462,-22.036c6.052,-44.414 16.477,-96.90" +
        "1 26.398,-143.951Z",
    ),
    svgPath(
        "M296.668,193.129c-5.479,-25.983 -25.743,-37.755 -62.282,-33.901c-40.742,4.297 -79.814,11.918 -11" +
        "5.424,22.309c-11.905,3.474 -23.487,8.273 -28.39,18.67c-91.01,192.963 -106.846,408.796 -55.913,64" +
        "3.084c2.947,13.555 9.046,24.221 21.05,32.104c5.843,3.836 12.587,7.06 21.886,9.445c21.562,5.529 3" +
        "1.041,-9.866 33.636,-23.758c33.416,-178.821 88.679,-342.348 191.663,-450.233c9.318,-9.761 18.181" +
        ",-31.681 20.635,-51.733c0.969,-7.926 0.451,-15.333 -0.463,-22.036c-6.051,-44.414 -16.477,-96.901" +
        " -26.398,-143.951Z",
    ),
    svgPath(
        "M559.079,143.015c0,0 158.534,-0.805 226.555,15.497c12.437,2.981 21.237,14.507 19.467,24.644c-8.9" +
        "42,51.221 -20.354,109.033 -30.53,160.023c-8.029,40.224 -40.893,53.816 -68.431,53.692c-27.538,-0." +
        "124 -147.061,-0.559 -147.061,-0.559c0,0 -119.522,0.435 -147.06,0.559c-27.538,0.124 -60.403,-13.4" +
        "68 -68.431,-53.692c-10.177,-50.99 -21.589,-108.802 -30.531,-160.023c-1.77,-10.137 7.031,-21.663 " +
        "19.467,-24.644c68.021,-16.302 226.555,-15.497 226.555,-15.497Z",
    ),
    svgPath(
        "M995.675,180.421c-0.736,-29.244 -151.654,-71.59 -148.099,-18.465c9.518,-3.397 21.562,-4.367 36.2" +
        "27,-2.82c39.392,4.154 77.223,11.417 111.872,21.285Z",
    ),
    svgPath(
        "M122.514,180.513c0.736,-29.244 151.654,-71.59 148.099,-18.465c-9.518,-3.397 -21.562,-4.367 -36.2" +
        "27,-2.82c-39.392,4.154 -77.223,11.417 -111.872,21.285Z",
    ),
    svgPath(
        "M839.082,206.321l-6.369,31.916c-0,-0 -2.557,11.875 11.724,14.725c14.281,2.849 16.478,-9.098 16.4" +
        "78,-9.098l6.4,-32.075c0,-0 2.559,-11.876 -11.74,-14.645c-14.298,-2.77 -16.493,9.177 -16.493,9.17" +
        "7Z",
    ),
    svgPath(
        "M281.064,206.321l6.369,31.916c-0,-0 2.557,11.875 -11.724,14.725c-14.281,2.849 -16.478,-9.098 -16" +
        ".478,-9.098l-6.4,-32.075c-0,-0 -2.559,-11.876 11.739,-14.645c14.299,-2.77 16.494,9.177 16.494,9." +
        "177Z",
    ),
    svgPath(
        "M262.694,188.058l-4.528,-7.079l4.528,7.079Z",
    ),
    svgPath(
        "M269.717,185.341l-0,-8.725l-0,8.725Z",
    ),
    svgPath(
        "M275.535,188.058l5.186,-7.079",
    ),
    svgPath(
        "M856.893,174.616l-13.987,0l13.987,0Z",
    ),
    svgPath(
        "M856.893,180.337l-13.987,0l13.987,0Z",
    ),
    svgPath(
        "M856.893,186.058l-13.987,-0",
    ),
)

private val FilledPaths: List<Path> = listOf(
    svgPath(
        "M168.337,270.016l18.658,0l-9.081,-9.08l-9.577,9.08Z",
    ),
    svgPath(
        "M168.337,445.073l18.658,0l-9.081,9.08l-9.577,-9.08Z",
    ),
    svgPath(
        "M265.73,367.409l0,-18.657l9.081,9.08l-9.081,9.577Z",
    ),
    svgPath(
        "M91.745,367.409l0,-18.657l-9.08,9.08l9.08,9.577Z",
    ),
)

private val Strips: List<Path> = listOf(
    svgPath("M300,200 C306,240 318,300 330,345 C338,380 368,409 405,411"),
    svgPath("M818.2,200 C812.2,240 800.2,300 788.2,345 C780.2,380 750.2,409 713.2,411"),
)

private val Sticks = listOf(Offset(351.764f, 528.548f), Offset(763.456f, 528.548f))
private val PsButton = Offset(559.1f, 516f)
private val HaloCenter = Offset(559f, 270f)

private fun DrawScope.drawController(light: Color, ink: Color, lit: Boolean) {
    val line = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (lit) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(light.copy(alpha = 0.42f), light.copy(alpha = 0.12f), Color.Transparent),
                center = HaloCenter,
                radius = 440f,
            ),
            radius = 440f,
            center = HaloCenter,
        )
    }
    for (path in OutlinePaths) drawPath(path, ink, style = line)
    for (path in FilledPaths) drawPath(path, ink)
    for (center in Sticks) {
        drawCircle(ink, radius = 87.3f, center = center, style = line)
        drawCircle(ink.copy(alpha = ink.alpha * 0.14f), radius = 57.2f, center = center)
        drawCircle(ink, radius = 57.2f, center = center, style = line)
    }
    drawCircle(ink, radius = 22f, center = PsButton, style = line)

    for (strip in Strips) {
        if (lit) {
            drawPath(strip, light.copy(alpha = 0.16f), style = Stroke(40f, cap = StrokeCap.Round))
            drawPath(strip, light.copy(alpha = 0.38f), style = Stroke(20f, cap = StrokeCap.Round))
            drawPath(strip, light, style = Stroke(9f, cap = StrokeCap.Round))
            drawPath(strip, lerp(light, Color.White, 0.75f), style = Stroke(3f, cap = StrokeCap.Round))
        } else {
            drawPath(strip, light.copy(alpha = 0.45f), style = Stroke(7f, cap = StrokeCap.Round))
        }
    }
}
