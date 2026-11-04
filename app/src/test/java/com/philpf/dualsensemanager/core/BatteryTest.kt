package com.philpf.dualsensemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryTest {

    @Test
    fun everyKernelLevelMapsToItsTenPercentStep() {
        // hid-playstation: p = min(10d + 5, 100) for d in 0..10.
        for (d in 0..9) {
            val p = 10 * d + 5
            assertEquals(PercentRange(10 * d, 10 * d + 10), BatteryText.range(p))
            assertEquals("${10 * d}–${10 * d + 10}%", BatteryText.level(p))
        }
        assertNull(BatteryText.range(100))
        assertEquals("100%", BatteryText.level(100))
    }

    @Test
    fun valuesNotOnTheStepGridAreShownAsIs() {
        assertNull(BatteryText.range(50))
        assertNull(BatteryText.range(0))
        assertNull(BatteryText.range(105))
        assertEquals("42%", BatteryText.level(42))
    }

    @Test
    fun placeholderBeforeFirstReportHasNoLevel() {
        assertEquals(BatteryReading(null, ChargeStatus.UNKNOWN), BatteryRules.reading(1f, ChargeStatus.UNKNOWN))
    }

    @Test
    fun errorStatesHaveNoLevel() {
        assertNull(BatteryRules.reading(0f, ChargeStatus.NOT_CHARGING).percent)
        assertNull(BatteryRules.reading(0f, ChargeStatus.UNKNOWN).percent)
        assertNull(BatteryRules.reading(Float.NaN, ChargeStatus.DISCHARGING).percent)
    }

    @Test
    fun normalReadingsAreKept() {
        assertEquals(BatteryReading(55, ChargeStatus.CHARGING), BatteryRules.reading(0.55f, ChargeStatus.CHARGING))
        assertEquals(BatteryReading(100, ChargeStatus.FULL), BatteryRules.reading(1f, ChargeStatus.FULL))
        assertEquals(BatteryReading(5, ChargeStatus.DISCHARGING), BatteryRules.reading(0.05f, ChargeStatus.DISCHARGING))
        assertEquals(100, BatteryRules.reading(1.2f, ChargeStatus.DISCHARGING).percent)
    }

    @Test
    fun lowMeansAtMostTheTenToTwentyStep() {
        assertTrue(BatteryText.isLow(5))
        assertTrue(BatteryText.isLow(15))
        assertFalse(BatteryText.isLow(25))
    }

    @Test
    fun missingReadingShowsDash() {
        assertEquals("—", BatteryText.level(null as BatteryReading?))
        assertEquals("—", BatteryText.level(BatteryReading(null, ChargeStatus.UNKNOWN)))
    }
}
