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

/** How asking to disconnect a controller ended. */
enum class DisconnectResult {
    /** Android is disconnecting it (the controller then turns off). */
    DONE,

    /** The user did not let the app manage the controller. */
    DECLINED,

    /** Not possible here; the system's Bluetooth settings can still do it. */
    FAILED,
}

/**
 * Disconnects a controller on Bluetooth, which turns it off. Since Android 17 (API 37) an app may
 * disconnect a device it is associated with through the Companion Device Manager; the user
 * approves that once per controller, in a system prompt. (Before, only system apps could.)
 */
class ControllerDisconnect(private val context: Context) {

    private val companions: CompanionDeviceManager? = context.getSystemService(CompanionDeviceManager::class.java)

    /** Whether this Android lets an approved app disconnect a device. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= API_DISCONNECT && companions != null

    /**
     * Disconnects the controller with input [descriptor] (and [vendorId], [productId]). When the
     * app is not yet approved for it, [askUser] gets the system's prompt to show first.
     * [onResult] runs on the main thread.
     */
    @SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is checked first.
    fun disconnect(
        descriptor: String,
        vendorId: Int,
        productId: Int,
        askUser: (IntentSender) -> Unit,
        onResult: (DisconnectResult) -> Unit,
    ) {
        val companions = companions
        if (!supported || companions == null || !Permissions.hasBluetoothConnect(context)) return onResult(DisconnectResult.FAILED)
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return onResult(DisconnectResult.FAILED)
        // The paired device whose address hashes to the controller's descriptor.
        val device = adapter.bondedDevices.firstOrNull { InputDescriptors.matches(vendorId, productId, it.address, descriptor) }
            ?: return onResult(DisconnectResult.FAILED)
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

                override fun onFailure(error: CharSequence?) = onResult(DisconnectResult.DECLINED)
            },
        )
    }

    /**
     * BluetoothDevice.disconnect(): public from API 37, which needs BLUETOOTH_CONNECT and an
     * association with the device. The app is compiled against an older SDK, so it is called
     * by name.
     */
    @SuppressLint("MissingPermission")
    private fun disconnectNow(device: BluetoothDevice): DisconnectResult = try {
        val status = BluetoothDevice::class.java.getMethod("disconnect").invoke(device)
        if (status == BluetoothStatusCodes.SUCCESS) DisconnectResult.DONE else DisconnectResult.FAILED
    } catch (e: ReflectiveOperationException) {
        DisconnectResult.FAILED
    } catch (e: SecurityException) {
        DisconnectResult.FAILED
    }

    private companion object {
        const val API_DISCONNECT = 37
    }
}
