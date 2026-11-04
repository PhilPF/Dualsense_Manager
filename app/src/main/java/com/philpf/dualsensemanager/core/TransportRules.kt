package com.philpf.dualsensemanager.core

object TransportRules {
    /**
     * Android has no public API for a controller's bus, so this uses two facts:
     *
     * - Over USB the kernel names the device from its USB strings, "Sony Interactive
     *   Entertainment DualSense Wireless Controller"; over Bluetooth it gets the Bluetooth name,
     *   "DualSense Wireless Controller".
     * - If no Sony controller is attached over USB at all ([sonyUsbDevices] = 0), every connected
     *   controller must be on Bluetooth, whatever its name.
     */
    fun transport(inputName: String, sonyUsbDevices: Int): Transport =
        if (sonyUsbDevices > 0 && inputName.trimStart().startsWith("Sony", ignoreCase = true)) {
            Transport.USB
        } else {
            Transport.BLUETOOTH
        }
}
