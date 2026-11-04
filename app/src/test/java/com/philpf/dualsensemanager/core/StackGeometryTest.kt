package com.philpf.dualsensemanager.core

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class StackGeometryTest {
    /** The front card's rectangle as StackView draws it, for a stack placed as [place]. */
    private fun frontCard(place: StackPlacement, p: Int): FloatArray {
        val cardWidth = (place.width * (1f - StackGeometry.SHIFT)).roundToInt()
        val cardHeight = (place.height * (1f - StackGeometry.SHIFT)).roundToInt()
        val shiftY = StackGeometry.SHIFT * place.height
        return floatArrayOf(
            (place.left + p).toFloat(),
            place.top + shiftY + p,
            (place.left + cardWidth - p).toFloat(),
            place.top + shiftY + cardHeight - p,
        )
    }

    @Test
    fun theFrontCardCoversTheWidgetAndNotMuchMore() {
        for (density in listOf(1f, 1.5f, 2f, 2.625f, 2.75f, 3f, 3.5f, 4f)) {
            val p = StackGeometry.framePadding(density)
            for (wDp in 40..140 step 3) {
                for (hDp in 40..140 step 7) {
                    val w = (wDp * density).roundToInt()
                    val h = (hDp * density).roundToInt()
                    val place = StackGeometry.placement(w, h, p)
                    val (left, top, right, bottom) = frontCard(place, p).toList()
                    val where = "density=$density w=$w h=$h $place -> [$left, $top, $right, $bottom]"
                    // Covers the widget, so nothing behind it shows...
                    assertTrue(where, left <= 0f && top <= 0.5f && right >= w && bottom >= h - 0.5f)
                    // ...and overshoots by a few pixels at most, so the card's content stays in view.
                    assertTrue(where, left >= -3f && top >= -4f && right <= w + 4 && bottom <= h + 4)
                }
            }
        }
    }

    @Test
    fun framePaddingIsFourDpRoundedUp() {
        assertTrue(StackGeometry.framePadding(2.625f) == 11)
        assertTrue(StackGeometry.framePadding(3f) == 12)
    }
}
