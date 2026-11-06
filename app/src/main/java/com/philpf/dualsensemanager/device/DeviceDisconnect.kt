package com.philpf.dualsensemanager.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import com.philpf.dualsensemanager.Permissions
import com.philpf.dualsensemanager.core.InputDescriptors

/** How asking to disconnect a device ended. */
sealed interface DisconnectResult {
    /** Android is disconnecting it (a controller then turns off). */
    data object Done : DisconnectResult

    /** The user did not let the app manage the device. */
    data object Declined : DisconnectResult

    /**
     * The app cannot do it itself; the system settings can, in one tap: the device's own page
     * when its Bluetooth [address] is known, otherwise the list of Bluetooth devices.
     */
    data class InSettings(val address: String?) : DisconnectResult
}

/**
 * Disconnects a controller (which turns it off) or headphones on Bluetooth.
 *
 * From Android 17 (API 37) an app may disconnect a device it is associated with through the
 * Companion Device Manager; the user approves that once per device, in a system prompt.
 * Before, only system apps may, so the app leads to the device's page in the system settings
 * instead, whose "Disconnect" button does it.
 */
class DeviceDisconnect(private val context: Context) {

    private val companions: CompanionDeviceManager? = context.getSystemService(CompanionDeviceManager::class.java)

    /** Whether this Android lets an approved app disconnect a device itself. */
    private val direct: Boolean get() = Build.VERSION.SDK_INT >= API_DISCONNECT && companions != null

    /**
     * Disconnects the controller with input [descriptor] (and [vendorId], [productId]). When the
     * app is not yet approved for it, [askUser] gets the system's prompt to show first.
     * [onResult] runs on the main thread.
     */
    fun disconnect(
        descriptor: String,
        vendorId: Int,
        productId: Int,
        askUser: (IntentSender) -> Unit,
        onResult: (DisconnectResult) -> Unit,
    ) {
        val device = pairedDevice { InputDescriptors.matches(vendorId, productId, it.address, descriptor) }
            ?: return onResult(DisconnectResult.InSettings(null))
        disconnect(device, askUser, onResult)
    }

    /** Disconnects the paired device with Bluetooth [address] (headphones), as [disconnect] above. */
    fun disconnect(address: String, askUser: (IntentSender) -> Unit, onResult: (DisconnectResult) -> Unit) {
        val device = pairedDevice { it.address.equals(address, ignoreCase = true) }
            ?: return onResult(DisconnectResult.InSettings(address))
        disconnect(device, askUser, onResult)
    }

    private fun disconnect(device: BluetoothDevice, askUser: (IntentSender) -> Unit, onResult: (DisconnectResult) -> Unit) {
        val companions = companions
        if (!direct || companions == null) return onResult(DisconnectResult.InSettings(device.address))
        val approved = companions.myAssociations.any { it.deviceMacAddress?.toString().equals(device.address, ignoreCase = true) }
        if (approved) return onResult(disconnectNow(device))

        // One device by address: the system also looks among paired ones, so it finds it at once.
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(device.address).build())
            .setSingleDevice(true)
            .build()
        companions.associate(
            request,
            context.mainExecutor,
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) = askUser(intentSender)

                override fun onAssociationCreated(associationInfo: AssociationInfo) = onResult(disconnectNow(device))

                override fun onFailure(error: CharSequence?) = onResult(DisconnectResult.Declined)
            },
        )
    }

    /** The first paired device that [matches]. */
    @SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is checked first.
    private fun pairedDevice(matches: (BluetoothDevice) -> Boolean): BluetoothDevice? {
        if (!Permissions.hasBluetoothConnect(context)) return null
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        return try {
            adapter.bondedDevices?.firstOrNull(matches)
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * BluetoothDevice.disconnect(): public from API 37, which needs BLUETOOTH_CONNECT and an
     * association with the device. The app is compiled against an older SDK, so it is called
     * by name.
     */
    @SuppressLint("MissingPermission")
    private fun disconnectNow(device: BluetoothDevice): DisconnectResult = try {
        val status = BluetoothDevice::class.java.getMethod("disconnect").invoke(device)
        if (status == BluetoothStatusCodes.SUCCESS) DisconnectResult.Done else DisconnectResult.InSettings(device.address)
    } catch (e: ReflectiveOperationException) {
        DisconnectResult.InSettings(device.address)
    } catch (e: SecurityException) {
        DisconnectResult.InSettings(device.address)
    }

    private companion object {
        const val API_DISCONNECT = 37
    }
}
