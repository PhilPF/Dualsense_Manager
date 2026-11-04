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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.hypot
import com.philpf.dualsensemanager.core.ControllerPart
import com.philpf.dualsensemanager.core.Sticks

/*
 * The controller drawing is adapted from design/dualsense.svg of daidr/dualsense-tester
 * (https://github.com/daidr/dualsense-tester): the PlayStation logo, the triggers and the
 * touch markers were left out and a plain PS button was added.
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
 * A DualSense seen from the front, drawn in [ink], its touchpad glowing in [light] while the
 * controller's light is on ([lit]).
 * Buttons in [pressed] light up in the controller's colour, and the sticks follow [sticks]
 * (read while drawing, so moving them only redraws).
 */
@Composable
fun ControllerArt(
    light: Color,
    ink: Color,
    lit: Boolean,
    modifier: Modifier = Modifier,
    pressed: Set<ControllerPart> = emptySet(),
    sticks: (() -> Sticks?)? = null,
) {
    Canvas(modifier.aspectRatio(ControllerArtAspect)) {
        val s = size.width / ART_WIDTH
        scale(s, s, pivot = Offset.Zero) {
            translate(-ART_LEFT, -ART_TOP) { drawController(light, ink, lit, pressed, sticks?.invoke()) }
        }
    }
}

/** Where the light bar sits within the drawing, as fractions of its width and height. */
val LightBarCenter = Offset((559f - ART_LEFT) / ART_WIDTH, (270f - ART_TOP) / ART_HEIGHT)

private const val ART_LEFT = 0f
private const val ART_TOP = 128f
private const val ART_WIDTH = 1117f
private const val ART_HEIGHT = 764f

/** The drawing's width over its height. */
const val ControllerArtAspect = ART_WIDTH / ART_HEIGHT

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

private class PartShape(val lines: List<Path>, val marks: List<Path>, val area: Path)

/** The whole controller's outline, for a faint body fill. */
private val Body = svgPath(
    "M227.8,134.2 L245.8,135.2 L260.2,139.8 L264.8,142.8 L269.8,148.2 L272.8,155.8 L272.8,159.8 L283." +
    "2,166.2 L321.8,160.2 L331.8,156.2 L348.2,152.8 L380.8,148.2 L446.2,143.2 L520.8,140.8 L618.8,141" +
    ".2 L704.8,145.2 L760.8,151.2 L786.2,156.2 L795.8,160.2 L834.2,166.2 L845.2,159.8 L846.2,152.8 L8" +
    "48.2,148.2 L853.2,142.8 L857.8,139.8 L871.8,135.2 L893.2,134.2 L908.2,135.8 L926.8,139.2 L948.2," +
    "145.2 L960.2,149.8 L973.2,155.8 L987.8,164.8 L994.8,171.8 L998.2,179.2 L1011.8,183.8 L1023.2,191" +
    ".2 L1030.2,201.8 L1053.8,256.8 L1065.8,289.8 L1078.8,331.2 L1088.8,369.2 L1098.2,414.2 L1104.8,4" +
    "54.2 L1109.2,492.2 L1112.8,539.8 L1113.8,570.8 L1113.8,608.8 L1111.2,664.8 L1107.2,709.2 L1101.8" +
    ",752.2 L1093.8,800.8 L1084.2,847.2 L1078.2,861.8 L1069.8,872.2 L1059.2,879.8 L1040.8,887.2 L1027" +
    ".2,888.2 L1017.2,884.2 L997.2,885.8 L971.8,885.8 L956.8,884.2 L948.2,881.2 L933.2,860.2 L915.2,8" +
    "25.2 L896.2,779.8 L872.8,714.8 L859.8,679.2 L854.2,670.2 L846.8,662.2 L837.8,655.8 L827.2,650.8 " +
    "L812.2,646.2 L800.2,644.2 L795.2,644.2 L777.8,651.2 L759.8,654.8 L732.8,653.8 L700.2,647.8 L417." +
    "8,647.8 L385.8,653.8 L359.2,654.8 L338.8,650.8 L322.8,644.2 L310.8,645.2 L290.8,650.8 L278.8,656" +
    ".8 L267.8,665.8 L260.2,675.8 L254.2,689.8 L224.8,772.2 L206.2,817.8 L183.8,862.2 L175.8,874.2 L1" +
    "69.2,881.8 L161.2,884.2 L146.2,885.8 L119.8,885.8 L99.8,884.2 L92.2,887.8 L82.2,888.2 L65.2,883." +
    "2 L54.8,877.8 L48.2,872.8 L39.2,861.8 L33.2,847.8 L22.2,793.2 L15.2,749.8 L9.8,705.8 L4.2,630.2 " +
    "L3.8,563.8 L7.8,496.2 L15.2,436.8 L28.8,368.8 L41.2,322.2 L56.8,275.2 L78.8,220.2 L92.2,193.2 L1" +
    "00.8,186.2 L111.8,181.2 L120.2,178.8 L124.2,170.8 L131.2,164.2 L143.2,156.8 L154.8,151.2 L176.8," +
    "143.2 L194.2,138.8 L214.8,135.2 Z",
)

private val Lines: List<Path> = listOf(
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
        "M835.33,168.559c-13.84,1.794 -27.681,-1.16 -41.522,-6.511",
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
        "M820.718,193.129c5.479,-25.983 25.744,-37.755 62.283,-33.901c40.742,4.297 79.814,11.918 115.424," +
        "22.309c11.905,3.474 23.486,8.273 28.39,18.67c91.01,192.963 106.846,408.796 55.912,643.084c-2.947" +
        ",13.555 -9.046,24.221 -21.05,32.104c-5.842,3.836 -12.586,7.06 -21.885,9.445c-21.563,5.529 -31.04" +
        "1,-9.866 -33.637,-23.758c-33.415,-178.821 -88.678,-342.348 -191.662,-450.233c-9.318,-9.761 -18.1" +
        "81,-31.681 -20.635,-51.733c-0.97,-7.926 -0.451,-15.333 0.462,-22.036c6.052,-44.414 16.477,-96.90" +
        "1 26.398,-143.951Z",
    ),
)

private val Parts: Map<ControllerPart, PartShape> = mapOf(
    ControllerPart.CROSS to PartShape(
        lines = listOf(
            svgPath(
                "M899.122,428.08a34.957,34.957 0 1,0 69.914,0a34.957,34.957 0 1,0 -69.914,0Z",
            ),
            svgPath(
                "M917.682,411.683l32.795,32.795m-0,-32.795l-32.795,32.795",
            ),
        ),
        marks = listOf(
        ),
        area = svgPath(
            "M899.122,428.08a34.957,34.957 0 1,0 69.914,0a34.957,34.957 0 1,0 -69.914,0Z",
        ),
    ),
    ControllerPart.CIRCLE to PartShape(
        lines = listOf(
            svgPath(
                "M969.123,358.08a34.957,34.957 0 1,0 69.914,0a34.957,34.957 0 1,0 -69.914,0Z",
            ),
            svgPath(
                "M984.98,358.08a19.1,19.1 0 1,0 38.2,0a19.1,19.1 0 1,0 -38.2,0Z",
            ),
        ),
        marks = listOf(
        ),
        area = svgPath(
            "M969.123,358.08a34.957,34.957 0 1,0 69.914,0a34.957,34.957 0 1,0 -69.914,0Z",
        ),
    ),
    ControllerPart.SQUARE to PartShape(
        lines = listOf(
            svgPath(
                "M829.122,358.08a34.957,34.957 0 1,0 69.914,0a34.957,34.957 0 1,0 -69.914,0Z",
            ),
            svgPath(
                "M847.313,341.648h32.865v32.865h-32.865Z",
            ),
        ),
        marks = listOf(
        ),
        area = svgPath(
            "M829.122,358.08a34.957,34.957 0 1,0 69.914,0a34.957,34.957 0 1,0 -69.914,0Z",
        ),
    ),
    ControllerPart.TRIANGLE to PartShape(
        lines = listOf(
            svgPath(
                "M899.122,288.08a34.957,34.957 0 1,0 69.914,0a34.957,34.957 0 1,0 -69.914,0Z",
            ),
            svgPath(
                "M934.079,271.403l20.206,33.355l-40.411,-0l20.205,-33.355Z",
            ),
        ),
        marks = listOf(
        ),
        area = svgPath(
            "M899.122,288.08a34.957,34.957 0 1,0 69.914,0a34.957,34.957 0 1,0 -69.914,0Z",
        ),
    ),
    ControllerPart.DPAD_UP to PartShape(
        lines = listOf(
            svgPath(
                "M213.487,271.996c0,-11.214 -9.091,-20.305 -20.305,-20.305l-28.888,-0c-11.215,-0 -20.306,9.091 -2" +
                "0.306,20.305l0,24.336c0,6.286 2.397,12.335 6.703,16.915c6.856,7.293 17.331,18.436 23.351,24.838c" +
                "1.218,1.296 2.917,2.031 4.696,2.031c1.778,-0 3.478,-0.735 4.696,-2.031c6.019,-6.402 16.494,-17.5" +
                "45 23.35,-24.838c4.306,-4.58 6.703,-10.629 6.703,-16.915c0,-6.766 0,-16.013 0,-24.336Z",
            ),
        ),
        marks = listOf(
            svgPath(
                "M168.337,270.016l18.658,0l-9.081,-9.08l-9.577,9.08Z",
            ),
        ),
        area = svgPath(
            "M213.487,271.996c0,-11.214 -9.091,-20.305 -20.305,-20.305l-28.888,-0c-11.215,-0 -20.306,9.091 -2" +
            "0.306,20.305l0,24.336c0,6.286 2.397,12.335 6.703,16.915c6.856,7.293 17.331,18.436 23.351,24.838c" +
            "1.218,1.296 2.917,2.031 4.696,2.031c1.778,-0 3.478,-0.735 4.696,-2.031c6.019,-6.402 16.494,-17.5" +
            "45 23.35,-24.838c4.306,-4.58 6.703,-10.629 6.703,-16.915c0,-6.766 0,-16.013 0,-24.336Z",
        ),
    ),
    ControllerPart.DPAD_DOWN to PartShape(
        lines = listOf(
            svgPath(
                "M213.487,443.093c0,11.215 -9.091,20.306 -20.305,20.306l-28.888,-0c-11.215,-0 -20.306,-9.091 -20." +
                "306,-20.306l0,-24.125c0,-6.412 2.494,-12.572 6.953,-17.178c6.884,-7.11 17.23,-17.796 23.166,-23." +
                "927c1.214,-1.254 2.885,-1.962 4.631,-1.962c1.745,0 3.416,0.708 4.63,1.962c5.936,6.131 16.282,16." +
                "817 23.166,23.927c4.46,4.606 6.953,10.766 6.953,17.178c0,6.746 0,15.888 0,24.125Z",
            ),
        ),
        marks = listOf(
            svgPath(
                "M168.337,445.073l18.658,0l-9.081,9.08l-9.577,-9.08Z",
            ),
        ),
        area = svgPath(
            "M213.487,443.093c0,11.215 -9.091,20.306 -20.305,20.306l-28.888,-0c-11.215,-0 -20.306,-9.091 -20." +
            "306,-20.306l0,-24.125c0,-6.412 2.494,-12.572 6.953,-17.178c6.884,-7.11 17.23,-17.796 23.166,-23." +
            "927c1.214,-1.254 2.885,-1.962 4.631,-1.962c1.745,0 3.416,0.708 4.63,1.962c5.936,6.131 16.282,16." +
            "817 23.166,23.927c4.46,4.606 6.953,10.766 6.953,17.178c0,6.746 0,15.888 0,24.125Z",
        ),
    ),
    ControllerPart.DPAD_LEFT to PartShape(
        lines = listOf(
            svgPath(
                "M93.725,322.259c-11.214,0 -20.306,9.091 -20.306,20.306l0,28.888c0,11.214 9.092,20.305 20.306,20." +
                "305l24.349,0c6.278,0 12.321,-2.391 16.899,-6.687c7.081,-6.644 17.749,-16.654 23.908,-22.433c1.27" +
                "3,-1.195 2.007,-2.855 2.034,-4.601c0.027,-1.746 -0.656,-3.428 -1.891,-4.661c-6.106,-6.094 -16.81" +
                ",-16.778 -23.947,-23.901c-4.63,-4.621 -10.904,-7.216 -17.446,-7.216c-6.724,0 -15.757,0 -23.906,0" +
                "Z",
            ),
        ),
        marks = listOf(
            svgPath(
                "M91.745,367.409l0,-18.657l-9.08,9.08l9.08,9.577Z",
            ),
        ),
        area = svgPath(
            "M93.725,322.259c-11.214,0 -20.306,9.091 -20.306,20.306l0,28.888c0,11.214 9.092,20.305 20.306,20." +
            "305l24.349,0c6.278,0 12.321,-2.391 16.899,-6.687c7.081,-6.644 17.749,-16.654 23.908,-22.433c1.27" +
            "3,-1.195 2.007,-2.855 2.034,-4.601c0.027,-1.746 -0.656,-3.428 -1.891,-4.661c-6.106,-6.094 -16.81" +
            ",-16.778 -23.947,-23.901c-4.63,-4.621 -10.904,-7.216 -17.446,-7.216c-6.724,0 -15.757,0 -23.906,0" +
            "Z",
        ),
    ),
    ControllerPart.DPAD_RIGHT to PartShape(
        lines = listOf(
            svgPath(
                "M263.75,322.259c11.215,0 20.306,9.091 20.306,20.306l-0,28.888c-0,11.214 -9.091,20.305 -20.306,20" +
                ".305l-24.349,0c-6.278,0 -12.32,-2.391 -16.899,-6.687c-7.081,-6.644 -17.749,-16.654 -23.907,-22.4" +
                "33c-1.274,-1.195 -2.008,-2.855 -2.035,-4.601c-0.027,-1.746 0.656,-3.428 1.892,-4.661c6.106,-6.09" +
                "4 16.81,-16.778 23.946,-23.901c4.63,-4.621 10.905,-7.216 17.446,-7.216c6.724,0 15.758,0 23.906,0" +
                "Z",
            ),
        ),
        marks = listOf(
            svgPath(
                "M265.73,367.409l0,-18.657l9.081,9.08l-9.081,9.577Z",
            ),
        ),
        area = svgPath(
            "M263.75,322.259c11.215,0 20.306,9.091 20.306,20.306l-0,28.888c-0,11.214 -9.091,20.305 -20.306,20" +
            ".305l-24.349,0c-6.278,0 -12.32,-2.391 -16.899,-6.687c-7.081,-6.644 -17.749,-16.654 -23.907,-22.4" +
            "33c-1.274,-1.195 -2.008,-2.855 -2.035,-4.601c-0.027,-1.746 0.656,-3.428 1.892,-4.661c6.106,-6.09" +
            "4 16.81,-16.778 23.946,-23.901c4.63,-4.621 10.905,-7.216 17.446,-7.216c6.724,0 15.758,0 23.906,0" +
            "Z",
        ),
    ),
    ControllerPart.L1 to PartShape(
        lines = listOf(
            svgPath(
                "M122.514,180.513c0.736,-29.244 151.654,-71.59 148.099,-18.465c-9.518,-3.397 -21.562,-4.367 -36.2" +
                "27,-2.82c-39.392,4.154 -77.223,11.417 -111.872,21.285Z",
            ),
        ),
        marks = listOf(
        ),
        area = svgPath(
            "M122.514,180.513c0.736,-29.244 151.654,-71.59 148.099,-18.465c-9.518,-3.397 -21.562,-4.367 -36.2" +
            "27,-2.82c-39.392,4.154 -77.223,11.417 -111.872,21.285Z",
        ),
    ),
    ControllerPart.R1 to PartShape(
        lines = listOf(
            svgPath(
                "M995.675,180.421c-0.736,-29.244 -151.654,-71.59 -148.099,-18.465c9.518,-3.397 21.562,-4.367 36.2" +
                "27,-2.82c39.392,4.154 77.223,11.417 111.872,21.285Z",
            ),
        ),
        marks = listOf(
        ),
        area = svgPath(
            "M995.675,180.421c-0.736,-29.244 -151.654,-71.59 -148.099,-18.465c9.518,-3.397 21.562,-4.367 36.2" +
            "27,-2.82c39.392,4.154 77.223,11.417 111.872,21.285Z",
        ),
    ),
    ControllerPart.CREATE to PartShape(
        lines = listOf(
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
        ),
        marks = listOf(
        ),
        area = svgPath(
            "M281.064,206.321l6.369,31.916c-0,-0 2.557,11.875 -11.724,14.725c-14.281,2.849 -16.478,-9.098 -16" +
            ".478,-9.098l-6.4,-32.075c-0,-0 -2.559,-11.876 11.739,-14.645c14.299,-2.77 16.494,9.177 16.494,9." +
            "177Z",
        ),
    ),
    ControllerPart.OPTIONS to PartShape(
        lines = listOf(
            svgPath(
                "M839.082,206.321l-6.369,31.916c-0,-0 -2.557,11.875 11.724,14.725c14.281,2.849 16.478,-9.098 16.4" +
                "78,-9.098l6.4,-32.075c0,-0 2.559,-11.876 -11.74,-14.645c-14.298,-2.77 -16.493,9.177 -16.493,9.17" +
                "7Z",
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
        ),
        marks = listOf(
        ),
        area = svgPath(
            "M839.082,206.321l-6.369,31.916c-0,-0 -2.557,11.875 11.724,14.725c14.281,2.849 16.478,-9.098 16.4" +
            "78,-9.098l6.4,-32.075c0,-0 2.559,-11.876 -11.74,-14.645c-14.298,-2.77 -16.493,9.177 -16.493,9.17" +
            "7Z",
        ),
    ),
    ControllerPart.PS to PartShape(
        lines = listOf(
            svgPath(
                "M537.1,516a22,22 0 1,0 44,0a22,22 0 1,0 -44,0Z",
            ),
        ),
        marks = listOf(
        ),
        area = svgPath(
            "M537.1,516a22,22 0 1,0 44,0a22,22 0 1,0 -44,0Z",
        ),
    ),
    ControllerPart.MUTE to PartShape(
        lines = listOf(
            svgPath(
                "M590.061,591.266c-0,-3.699 -2.999,-6.698 -6.698,-6.698c-11.914,0 -36.653,0 -48.567,0c-3.7,0 -6.6" +
                "98,2.999 -6.698,6.698c-0,0.001 -0,0.001 -0,0.002c-0,3.699 2.998,6.698 6.698,6.698c11.914,-0 36.6" +
                "53,-0 48.567,-0c3.699,-0 6.698,-2.999 6.698,-6.698c-0,-0.001 -0,-0.001 -0,-0.002Z",
            ),
        ),
        marks = listOf(
        ),
        area = svgPath(
            "M590.061,591.266c-0,-3.699 -2.999,-6.698 -6.698,-6.698c-11.914,0 -36.653,0 -48.567,0c-3.7,0 -6.6" +
            "98,2.999 -6.698,6.698c-0,0.001 -0,0.001 -0,0.002c-0,3.699 2.998,6.698 6.698,6.698c11.914,-0 36.6" +
            "53,-0 48.567,-0c3.699,-0 6.698,-2.999 6.698,-6.698c-0,-0.001 -0,-0.001 -0,-0.002Z",
        ),
    ),
    ControllerPart.TOUCHPAD to PartShape(
        lines = listOf(
            svgPath(
                "M559.079,143.015c0,0 158.534,-0.805 226.555,15.497c12.437,2.981 21.237,14.507 19.467,24.644c-8.9" +
                "42,51.221 -20.354,109.033 -30.53,160.023c-8.029,40.224 -40.893,53.816 -68.431,53.692c-27.538,-0." +
                "124 -147.061,-0.559 -147.061,-0.559c0,0 -119.522,0.435 -147.06,0.559c-27.538,0.124 -60.403,-13.4" +
                "68 -68.431,-53.692c-10.177,-50.99 -21.589,-108.802 -30.531,-160.023c-1.77,-10.137 7.031,-21.663 " +
                "19.467,-24.644c68.021,-16.302 226.555,-15.497 226.555,-15.497Z",
            ),
        ),
        marks = listOf(
        ),
        area = svgPath(
            "M559.079,143.015c0,0 158.534,-0.805 226.555,15.497c12.437,2.981 21.237,14.507 19.467,24.644c-8.9" +
            "42,51.221 -20.354,109.033 -30.53,160.023c-8.029,40.224 -40.893,53.816 -68.431,53.692c-27.538,-0." +
            "124 -147.061,-0.559 -147.061,-0.559c0,0 -119.522,0.435 -147.06,0.559c-27.538,0.124 -60.403,-13.4" +
            "68 -68.431,-53.692c-10.177,-50.99 -21.589,-108.802 -30.531,-160.023c-1.77,-10.137 7.031,-21.663 " +
            "19.467,-24.644c68.021,-16.302 226.555,-15.497 226.555,-15.497Z",
        ),
    ),
)

private val TouchpadArea = Parts.getValue(ControllerPart.TOUCHPAD).area
private val TouchpadCenter = Offset(559f, 265f)
private val StickCenters = mapOf(
    ControllerPart.LEFT_STICK to Offset(351.764f, 528.548f),
    ControllerPart.RIGHT_STICK to Offset(763.456f, 528.548f),
)
private val StickClicks = mapOf(ControllerPart.L3 to ControllerPart.LEFT_STICK, ControllerPart.R3 to ControllerPart.RIGHT_STICK)
private val TriggerToBumper = mapOf(ControllerPart.L2 to ControllerPart.L1, ControllerPart.R2 to ControllerPart.R1)

/** How far a stick's cap moves at full tilt, in drawing units (its well has room for about 30). */
private const val STICK_TRAVEL = 27f
private const val STICK_RING = 87.3f
private const val STICK_CAP = 57.2f

private fun DrawScope.drawController(light: Color, ink: Color, lit: Boolean, pressed: Set<ControllerPart>, sticks: Sticks?) {
    val line = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val lightUp = pressed.map { TriggerToBumper[it] ?: it }.toSet()

    drawPath(Body, ink.copy(alpha = ink.alpha * 0.07f))
    if (lit) {
        // The touchpad glows in the light's colour.
        clipPath(TouchpadArea) {
            drawRect(
                brush = Brush.radialGradient(
                    listOf(light.copy(alpha = 0.34f), light.copy(alpha = 0.10f)),
                    center = TouchpadCenter,
                    radius = 260f,
                ),
                topLeft = Offset(300f, 130f),
                size = androidx.compose.ui.geometry.Size(520f, 280f),
            )
        }
    }

    // Each stick: where its cap is, how far it is tilted (0 to 1) and whether it is clicked in.
    // Without positions, a stick pushed past half way shows fully tilted in place.
    class Stick(val center: Offset, val cap: Offset, val tilt: Float, val clicked: Boolean)
    val stickStates = StickCenters.map { (part, center) ->
        val (x, y) = when {
            sticks == null -> 0f to 0f
            part == ControllerPart.LEFT_STICK -> sticks.leftX to sticks.leftY
            else -> sticks.rightX to sticks.rightY
        }
        val tilt = if (sticks == null) (if (part in pressed) 1f else 0f) else hypot(x, y).coerceAtMost(1f)
        val clicked = StickClicks.entries.any { it.value == part && it.key in pressed }
        Stick(center, center + Offset(x, y) * STICK_TRAVEL, tilt, clicked)
    }

    // Pressed parts glow under every line: a soft halo, so that even the thin bumpers show at
    // cell size, and a fill.
    for ((part, shape) in Parts) {
        if (part !in lightUp) continue
        drawPath(shape.area, light.copy(alpha = 0.22f), style = Stroke(64f, join = StrokeJoin.Round))
        drawPath(shape.area, light.copy(alpha = 0.42f), style = Stroke(30f, join = StrokeJoin.Round))
        drawPath(shape.area, light)
    }
    for (stick in stickStates) {
        if (stick.clicked) {
            drawCircle(light.copy(alpha = 0.22f), radius = STICK_RING + 32f, center = stick.center)
            drawCircle(light.copy(alpha = 0.42f), radius = STICK_RING + 15f, center = stick.center)
            drawCircle(light, radius = STICK_RING, center = stick.center)
        } else if (stick.tilt > 0f) {
            // The cap glows more the further it is tilted.
            drawCircle(light.copy(alpha = 0.22f * stick.tilt), radius = STICK_CAP + 30f, center = stick.cap)
            drawCircle(light.copy(alpha = 0.42f * stick.tilt), radius = STICK_CAP + 14f, center = stick.cap)
            drawCircle(light.copy(alpha = 0.35f + 0.65f * stick.tilt), radius = STICK_CAP, center = stick.cap)
        }
    }

    for (path in Lines) drawPath(path, ink, style = line)
    for (shape in Parts.values) {
        for (path in shape.lines) drawPath(path, ink, style = line)
        for (path in shape.marks) drawPath(path, ink)
    }
    for (stick in stickStates) {
        drawCircle(ink, radius = STICK_RING, center = stick.center, style = line)
        if (!stick.clicked && stick.tilt == 0f) drawCircle(ink.copy(alpha = ink.alpha * 0.14f), radius = STICK_CAP, center = stick.cap)
        drawCircle(ink, radius = STICK_CAP, center = stick.cap, style = line)
    }
}
