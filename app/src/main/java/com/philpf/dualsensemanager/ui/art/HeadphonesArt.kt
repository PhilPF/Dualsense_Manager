package com.philpf.dualsensemanager.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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

/*
 * A WH-XB910N from the front, traced from a photograph of it (1483 x 1500 pixels, whose units
 * these are): its outline, the band's joints, screws and top edge, the cushions' edges, the
 * grooves in the shells and, on the right cup, the microphone and the jack. The photograph is
 * symmetric about x = 740.5, so its left half was traced and mirrored.
 */

/**
 * Over-ear headphones in lines, in [ink], in the same box as the controller drawing so the two
 * line up, with lines as thick. [glow], when not transparent, lights the cups (the noise
 * control). [lineScale] thickens the lines, as for the controller.
 */
@Composable
fun HeadphonesArt(ink: Color, modifier: Modifier = Modifier, glow: Color = Color.Transparent, lineScale: Float = 1f) {
    Canvas(modifier.aspectRatio(ControllerArtAspect)) {
        val s = minOf(size.width / WIDTH, size.height / HEIGHT)
        translate((size.width - WIDTH * s) / 2f, (size.height - HEIGHT * s) / 2f) {
            scale(s, s, pivot = Offset.Zero) {
                translate(-LEFT, -TOP) { drawHeadphones(ink, glow, lineScale) }
            }
        }
    }
}

// The drawing's frame in the photograph's units, with room below for the cups' glow.
private const val LEFT = 0f
private const val TOP = -20f
private const val WIDTH = 1483f
private const val HEIGHT = 1560f

/** The line width in the sheets: in the same box, as thick as the controller's. */
private const val LINE = 10f

/** How strongly the headphones are filled with ink. */
private const val FAINT = 0.07f

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

/** The whole outline. */
private val Outline = svgPath(
    "M704,2.4C714.1,1.1 724.8,2 735,2C788.1,2 840.1,7.2 892,18.6C1164.8,78.6 1368.1,297.2 1446.7,560C" +
    "1448.9,567.2 1452.3,573.8 1454.3,581C1457.9,594.3 1459.5,607.7 1461.9,621C1463.3,628.3 1466.3,63" +
    "5.5 1467.6,643C1469.7,655 1470.5,670.7 1472,680C1481.7,737.2 1483.1,796.9 1470.3,854C1468.6,861." +
    "7 1461.6,896.4 1454,897.7C1436.6,900.5 1427.2,847.4 1410.9,884C1409.3,887.5 1408.1,891.2 1407.4," +
    "895C1403,921.1 1407.8,943.7 1407.1,969C1406.9,975.1 1397.9,981.5 1399.4,987C1400.8,992.2 1408.2," +
    "993.4 1409.8,999C1413.5,1012.5 1413.5,1076.1 1410.3,1090C1406.2,1107.9 1374.2,1156.7 1363.2,1175" +
    "C1324,1240.5 1281.9,1303.8 1237.7,1366C1226.2,1382.2 1178,1451.3 1166,1459.3C1147.7,1471.5 1071." +
    "5,1486.4 1047,1490.2C1023.6,1494.2 943.1,1502.9 924,1493.2C910.7,1486.4 883.2,1465.1 872.1,1454." +
    "6C867.3,1450 871.8,1443.8 866,1439.9C829.1,1415.5 787.9,1415.4 797.3,1358C802.2,1327.5 854.9,125" +
    "1.1 873.6,1221C934,1123.2 994.1,1025.2 1053.8,927C1078.1,887 1101.6,845.9 1127.7,807C1138.3,791." +
    "1 1157.7,771.5 1178,769.2C1207.8,765.8 1231.3,794.3 1255,807.6C1260.1,810.4 1262.2,804.9 1267,80" +
    "4.9C1272.5,805 1278.1,809.3 1283,811.4C1288.6,813.8 1315.9,830.9 1319,825.8C1321,822.6 1322.2,80" +
    "3 1325.3,796C1331.3,782.6 1341,767.8 1349.8,756C1351.6,753.7 1355.6,751.9 1356.3,748.9C1358.8,73" +
    "8.4 1354.4,709.4 1354,697C1353.7,687.9 1357.5,671.4 1357.3,670C1356.8,663.5 1349.4,660.7 1348.3," +
    "654C1342.2,618 1335.6,582.8 1324.3,548C1291.2,445.9 1234.4,352.5 1156,278.9C1145.4,269 1135,256." +
    "8 1123,248.7C1106.1,237.1 1086.4,228 1068,219.2C979.1,176.7 875.7,151.5 777,150C759.3,150 741.7," +
    "148.4 724,149C622.8,152.4 521.6,169 429,211.7C405.1,222.7 379.8,233.8 358,248.7C333.1,265.6 283." +
    "1,321 263.6,346C195.3,433.5 149.9,537.1 133.7,647C131.6,661.3 125,662.7 123.8,669C123.4,671.1 12" +
    "7,687.1 127,693C127,706.2 122.1,738.2 124.7,748.9C125.4,751.9 129.4,753.7 131.2,756C140,767.8 14" +
    "9.7,782.6 155.7,796C158.8,803 160,822.6 162,825.8C165.1,830.9 192.4,813.8 198,811.4C202.9,809.3 " +
    "208.5,805 214,804.9C218.8,804.9 220.9,810.4 226,807.6C249.6,794.4 271,768.6 300,769C341.4,769.6 " +
    "379.4,849.5 398.9,881C464.7,987 528.8,1093.9 594.4,1200C614.6,1232.6 677.5,1325.7 683.5,1357C694" +
    ".6,1414.3 651.5,1415.8 615,1439.9C609.1,1443.9 612.9,1451.2 607,1456.2C597.5,1464.3 587,1471.5 5" +
    "77,1478.9C547.5,1500.8 543.3,1497.5 508,1497C451.5,1493.5 396.3,1486.1 342,1469C333.5,1466.3 323" +
    ".8,1464.4 316,1459.9C303.3,1452.7 260.8,1389.8 249.1,1374C202.5,1311.1 159.9,1245.1 119.6,1178C1" +
    "08.3,1159.2 74.9,1108.3 70.7,1090C67.5,1076.1 67.5,1012.5 71.2,999C72.8,993.4 80.2,992.2 81.6,98" +
    "7C83.1,981.4 73.8,974.5 73.9,968C74.2,942.6 79.3,916.1 72.7,891C71.3,885.6 68.7,880.4 65.3,876C6" +
    "3.8,874 61.1,870 58,870.5C47.4,872.2 30.2,917.1 19.9,888C-2.9,823.9 -2.2,746 9,680C9.7,675.8 8.7" +
    ",671.3 9.4,667C11.4,655 13.1,642.9 15.6,631C16.4,627.5 18.4,624.5 19.1,621C19.6,618 18.9,615 19." +
    "5,612C24.7,581.9 37.1,548 47.3,519C116.3,322.6 260.6,152.1 451,64.3C512.6,35.9 578.9,17.5 646,8." +
    "3C665.2,5.6 684.9,5.4 704,2.4Z",
)

/** The band's top edge, from joint to joint. */
private val BandEdge = svgPath(
    "M293.7,181.8C322.6,170.9 351.9,160.5 379.9,147.3C398.4,138.6 415.9,127.7 434,118.2C478.2,95.2 52" +
    "4.8,77.3 572.9,64.6C700.2,30.8 837.9,36.8 962,81.1C1009.3,98 1051.2,122.9 1096,144.9C1125.5,159." +
    "3 1156.7,170.2 1187.3,181.8",
)

/** Left then right: the band's joints, the arms above the cups, the cushions' edges and the shells' grooves. */
private val Lines = listOf(
    svgPath("M230.2,222.7L299.4,304.3"),
    svgPath("M1250.8,222.7L1181.6,304.3"),
    svgPath("M285,171.6L354,252.5"),
    svgPath("M1196,171.6L1127,252.5"),
    svgPath("M107,750.8L63.8,867"),
    svgPath("M1374,750.8L1417.2,867"),
    svgPath("M237,809.4L591.4,1438.1"),
    svgPath("M1244,809.4L889.6,1438.1"),
    svgPath(
        "M148,838C149.2,844.7 149.2,852.7 151.6,859.1C158.3,877.5 182.4,914.3 193.5,932.5C218,972.4 242,1" +
        "012.8 266,1053C279.2,1075.1 298.2,1098.3 303.5,1124C311.2,1161.2 283,1211.3 240.7,1202.2C235.6,1" +
        "201.1 229.2,1201 225,1197.4C218.5,1191.9 217.3,1181.1 212.4,1174.1C200.3,1157 187.6,1140.3 175.5" +
        ",1123.2C155.5,1094.7 127.9,1055.8 106.6,1029.1C101.7,1023 96.6,1015.9 90.1,1011.5C88.2,1010.2 82" +
        ".1,1008.6 80.8,1006.7C79.4,1004.7 79.4,1002 78,1000",
    ),
    svgPath(
        "M1333,838C1331.8,844.7 1331.8,852.7 1329.4,859.1C1322.7,877.5 1298.6,914.3 1287.5,932.5C1263,972" +
        ".4 1239,1012.8 1215,1053C1201.8,1075.1 1182.8,1098.3 1177.5,1124C1169.8,1161.2 1198,1211.3 1240." +
        "3,1202.2C1245.4,1201.1 1251.8,1201 1256,1197.4C1262.5,1191.9 1263.7,1181.1 1268.6,1174.1C1280.7," +
        "1157 1293.4,1140.3 1305.5,1123.2C1325.5,1094.7 1353.1,1055.8 1374.4,1029.1C1379.3,1023 1384.4,10" +
        "15.9 1390.9,1011.5C1392.8,1010.2 1398.9,1008.6 1400.2,1006.7C1401.6,1004.7 1401.6,1002 1403,1000",
    ),
)

/** The band's screws and the right cup's microphone: centre and radius. */
private val Dots = listOf(
    Triple(311f, 257f, 10f),
    Triple(1170f, 257f, 10f),
    Triple(1120f, 1297f, 6f),
)

/** A cup, for its glow: an ellipse centred at ([cx], [cy]) with semi-axes [a] and [b], turned by [degrees]. */
private class Cup(val cx: Float, val cy: Float, val a: Float, val b: Float, val degrees: Float)

private val Cups = listOf(
    Cup(390f, 1130f, 225f, 370f, -25.5f),
    Cup(1091f, 1130f, 225f, 370f, 25.5f),
)

private fun DrawScope.drawHeadphones(ink: Color, glow: Color, lineScale: Float) {
    val line = Stroke(width = LINE * lineScale, cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (glow.alpha > 0f) Cups.forEach { halo(it, glow) }
    drawPath(Outline, ink.copy(alpha = ink.alpha * FAINT))
    drawPath(Outline, ink, style = line)
    drawPath(BandEdge, ink, style = line)
    for (path in Lines) drawPath(path, ink, style = line)
    val holes = ink.copy(alpha = ink.alpha * 0.75f)
    for ((x, y, r) in Dots) drawCircle(holes, radius = r * lineScale, center = Offset(x, y))
    // The jack: its socket and the hole in it, seen from below.
    rotate(-8f, pivot = Offset(1025f, 1423f)) {
        val rim = Stroke(LINE * 0.7f * lineScale)
        drawOval(ink, Offset(996f, 1405f), Size(58f, 36f), style = rim)
        drawOval(ink, Offset(1018f, 1412f), Size(24f, 16f), style = rim)
    }
}

/** Light around a cup: [glow] over its ellipse, fading out just beyond it. */
private fun DrawScope.halo(cup: Cup, glow: Color) {
    val center = Offset(cup.cx, cup.cy)
    val r = cup.a * 1.18f
    rotate(cup.degrees, pivot = center) {
        scale(1f, cup.b / cup.a, pivot = center) {
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
