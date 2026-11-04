package com.philpf.dualsensemanager.core

import java.security.MessageDigest

/**
 * Android's input-device descriptors, to tell which paired Bluetooth device a controller is.
 *
 * Android's input reader (EventHub) names a device by the SHA-1, in lowercase hex, of
 * ":vvvv:pppp:uniqueId:<uniq>" (vendor and product in 4-digit hex), followed by "nonce:nnnn"
 * when another device already has that descriptor. A DualSense's unique id is its Bluetooth
 * address, so hashing a paired device's address gives the descriptor of the controller it is.
 */
object InputDescriptors {
    /** The descriptor of a device with these ids and unique id (nonce 0: none). */
    fun descriptor(vendorId: Int, productId: Int, uniqueId: String, nonce: Int = 0): String {
        val raw = buildString {
            append(":%04x:%04x:".format(vendorId, productId))
            append("uniqueId:").append(uniqueId)
            if (nonce != 0) append("nonce:%04x".format(nonce))
        }
        return MessageDigest.getInstance("SHA-1").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /**
     * Whether the paired device at Bluetooth [address] is the input device with [descriptor].
     * The driver writes the address in lowercase; uppercase and small nonces are tried too.
     */
    fun matches(vendorId: Int, productId: Int, address: String, descriptor: String): Boolean =
        listOf(address.lowercase(), address.uppercase()).distinct().any { unique ->
            (0..3).any { nonce -> descriptor(vendorId, productId, unique, nonce) == descriptor }
        }
}
