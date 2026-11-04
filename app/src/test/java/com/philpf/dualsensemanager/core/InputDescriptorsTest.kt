package com.philpf.dualsensemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputDescriptorsTest {
    // Reference values: SHA-1 of the raw descriptors, computed separately (Python hashlib).
    @Test
    fun descriptorsMatchAndroidsFormat() {
        assertEquals("4f286ee2d09824ba18a9117358474a57e44fc48c", InputDescriptors.descriptor(0x054C, 0x0CE6, "aa:bb:cc:dd:ee:ff"))
        assertEquals("3bd91c5359534f23a1638311cb17a03907e48a45", InputDescriptors.descriptor(0x054C, 0x0CE6, "aa:bb:cc:dd:ee:ff", nonce = 1))
        assertEquals("c7ced929059b92cc593f34a3ef832fc98cd1ed0c", InputDescriptors.descriptor(0x054C, 0x0DF2, "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun aPairedDeviceIsFoundByItsAddress() {
        val descriptor = "4f286ee2d09824ba18a9117358474a57e44fc48c"
        // Android lists paired devices' addresses in uppercase.
        assertTrue(InputDescriptors.matches(0x054C, 0x0CE6, "AA:BB:CC:DD:EE:FF", descriptor))
        assertFalse(InputDescriptors.matches(0x054C, 0x0CE6, "AA:BB:CC:DD:EE:00", descriptor))
        assertFalse(InputDescriptors.matches(0x054C, 0x0DF2, "AA:BB:CC:DD:EE:FF", descriptor))
        assertTrue(InputDescriptors.matches(0x054C, 0x0CE6, "AA:BB:CC:DD:EE:FF", "3bd91c5359534f23a1638311cb17a03907e48a45"))
    }
}
