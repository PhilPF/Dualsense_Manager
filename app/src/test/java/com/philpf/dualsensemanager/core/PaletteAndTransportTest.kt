package com.philpf.dualsensemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaletteAndTransportTest {

    private val blue = LedPalette.colors[0].rgb
    private val red = LedPalette.colors[1].rgb
    private val green = LedPalette.colors[2].rgb

    @Test
    fun numbersFillTheSmallestGap() {
        assertEquals(1, LedPalette.nextNumber(emptyList()))
        assertEquals(4, LedPalette.nextNumber(listOf(1, 2, 3)))
        assertEquals(2, LedPalette.nextNumber(listOf(3, 1, 5)))
    }

    @Test
    fun firstFourControllersGetThePs5PlayerColours() {
        val names = (1..4).map { LedPalette.nameOf(LedPalette.automaticColor(it, emptyList())) }
        assertEquals(listOf("Blue", "Red", "Green", "Pink"), names)
    }

    @Test
    fun automaticColourAvoidsColoursInUse() {
        // Controller 3 would be green, but another controller already uses green.
        assertEquals(blue, LedPalette.automaticColor(3, listOf(green)))
        assertEquals(green, LedPalette.automaticColor(1, listOf(blue, red)))
        // Alpha bits of stored colours are ignored.
        assertEquals(red, LedPalette.automaticColor(1, listOf(blue or (0xFF shl 24))))
    }

    @Test
    fun automaticColourFallsBackToTheSlotWhenAllAreTaken() {
        val all = LedPalette.colors.map { it.rgb }
        assertEquals(red, LedPalette.automaticColor(2, all))
        assertEquals(blue, LedPalette.automaticColor(LedPalette.colors.size + 1, all))
    }

    @Test
    fun unknownColourHasNoName() {
        assertNull(LedPalette.nameOf(0x123456))
    }

    @Test
    fun transportFromNameAndUsbCount() {
        val usbName = "Sony Interactive Entertainment DualSense Wireless Controller"
        val btName = "DualSense Wireless Controller"
        assertEquals(Transport.USB, TransportRules.transport(usbName, 1))
        assertEquals(Transport.BLUETOOTH, TransportRules.transport(btName, 1))
        // Nothing attached over USB: Bluetooth whatever the name says.
        assertEquals(Transport.BLUETOOTH, TransportRules.transport(usbName, 0))
    }

    @Test
    fun controllerKindsFromIds() {
        assertEquals(ControllerKind.DUALSENSE, ControllerKind.fromIds(0x054C, 0x0CE6))
        assertEquals(ControllerKind.DUALSENSE_EDGE, ControllerKind.fromIds(0x054C, 0x0DF2))
        assertNull(ControllerKind.fromIds(0x054C, 0x09CC)) // DualShock 4
        assertNull(ControllerKind.fromIds(0x045E, 0x0CE6))
    }
}
