package com.philpf.dualsensemanager.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import com.philpf.dualsensemanager.Permissions
import com.philpf.dualsensemanager.core.Headphones
import com.philpf.dualsensemanager.core.NoiseControl
import com.philpf.dualsensemanager.core.NoiseMode
import com.philpf.dualsensemanager.core.SonyCommands
import com.philpf.dualsensemanager.core.SonyEqualizer
import com.philpf.dualsensemanager.core.SonyEvent
import com.philpf.dualsensemanager.core.SonyFrameReader
import com.philpf.dualsensemanager.core.SonyFrames
import com.philpf.dualsensemanager.core.SonyHeadphonesIds
import com.philpf.dualsensemanager.core.SonyMessage
import com.philpf.dualsensemanager.core.SonySession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.UUID

/** Sony headphones: their state, and their controls. */
interface HeadphonesSource {
    val headphones: StateFlow<List<Headphones>>

    /** Looks for headphones again (after the Nearby devices permission is granted). */
    fun refresh()

    fun setNoise(address: String, control: NoiseControl)

    fun setEqualizer(address: String, preset: SonyEqualizer)

    fun powerOff(address: String)
}

/**
 * Sony headphones paired with the phone. While the app is on screen it talks to those that are
 * connected, over the RFCOMM channel Sony's own app uses (core/SonySession), to read their
 * battery and noise control and to change them; it lets go when the app leaves the screen.
 * Sony's app, if open at the same time, may hold that channel.
 *
 * Device work happens on one background thread; each open channel has a thread that reads it.
 */
@SuppressLint("MissingPermission") // Every Bluetooth call is behind Permissions.hasBluetoothConnect.
class HeadphonesManager(private val context: Context) : HeadphonesSource {

    private val thread = HandlerThread("headphones").apply { start() }
    private val handler = Handler(thread.looper)
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _headphones = MutableStateFlow<List<Headphones>>(emptyList())
    override val headphones: StateFlow<List<Headphones>> = _headphones.asStateFlow()

    // Only touched on [thread].
    private var visible = false
    private val profiles = HashMap<Int, BluetoothProfile>()
    private val links = HashMap<String, Link>()
    private val lastTry = HashMap<String, Long>()
    private var receiverOn = false

    private val receiver = object : BroadcastReceiver() {
        // Connections settle a moment after these arrive.
        override fun onReceive(context: Context, intent: Intent) {
            handler.postDelayed(::scan, SETTLE_MS)
        }
    }

    /** The app's screen is visible: talk to connected headphones; otherwise let go of them. */
    fun setUiVisible(on: Boolean) {
        handler.post {
            visible = on
            if (on) start() else stop()
        }
    }

    override fun refresh() {
        handler.post { if (visible) start() }
    }

    override fun setNoise(address: String, control: NoiseControl) {
        handler.post {
            val link = links[address] ?: return@post
            val wind = link.state.noise?.windVariant ?: control.windVariant
            val next = control.copy(windVariant = wind)
            link.write(link.session.send(SonyCommands.setNoise(next)))
            link.update { it.copy(noise = next) }
        }
    }

    override fun setEqualizer(address: String, preset: SonyEqualizer) {
        handler.post {
            val link = links[address] ?: return@post
            link.write(link.session.send(SonyCommands.setEqualizer(preset)))
            link.update { it.copy(equalizer = preset) }
        }
    }

    override fun powerOff(address: String) {
        handler.post { links[address]?.let { it.write(it.session.send(SonyCommands.POWER_OFF)) } }
    }

    // ---- Everything below runs on [thread]. ----

    private fun start() {
        if (adapter == null || !Permissions.hasBluetoothConnect(context)) return
        if (!receiverOn) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverOn = true
        }
        for (profile in intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            if (profile in profiles) continue
            adapter.getProfileProxy(
                context,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(type: Int, proxy: BluetoothProfile) {
                        handler.post {
                            profiles[type] = proxy
                            scan()
                        }
                    }

                    override fun onServiceDisconnected(type: Int) {
                        handler.post { profiles.remove(type) }
                    }
                },
                profile,
            )
        }
        scan()
    }

    private fun stop() {
        links.values.toList().forEach { it.close() }
        links.clear()
        if (receiverOn) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver was not registered", e)
            }
            receiverOn = false
        }
        for ((type, proxy) in profiles) adapter?.closeProfileProxy(type, proxy)
        profiles.clear()
        // Keep showing them, as not connected.
        _headphones.value = _headphones.value.map { Headphones(it.address, it.name, connected = false) }
    }

    /** Lists paired Sony headphones and opens a channel to each that is connected. */
    private fun scan() {
        if (!visible) return
        val adapter = adapter ?: return
        if (!Permissions.hasBluetoothConnect(context)) return
        val paired = try {
            adapter.bondedDevices.orEmpty().filter { device ->
                device.type != BluetoothDevice.DEVICE_TYPE_LE &&
                    SonyHeadphonesIds.isSony(device.name, device.uuids.orEmpty().map { it.uuid.toString() })
            }
        } catch (e: SecurityException) {
            emptyList()
        }
        val connected = profiles.values.flatMap { proxy ->
            try {
                proxy.connectedDevices.map { it.address }
            } catch (e: SecurityException) {
                emptyList()
            }
        }.toSet()

        for (address in links.keys.toList()) {
            if (address !in connected || paired.none { it.address == address }) links.remove(address)?.close()
        }
        val now = System.currentTimeMillis()
        for (device in paired) {
            if (device.address !in connected || device.address in links) continue
            if (now - (lastTry[device.address] ?: 0L) < RETRY_MS) continue
            lastTry[device.address] = now
            links[device.address] = Link(device, nameOf(device)).also { it.open() }
        }
        _headphones.value = paired.map { device ->
            links[device.address]?.state ?: Headphones(device.address, nameOf(device), connected = device.address in connected)
        }
    }

    private fun nameOf(device: BluetoothDevice): String = try {
        device.alias ?: device.name ?: device.address
    } catch (e: SecurityException) {
        device.address
    }

    private fun publish(link: Link) {
        if (links[link.address] !== link) return
        _headphones.value = _headphones.value.map { if (it.address == link.address) link.state else it }
    }

    /** One open channel to a pair of headphones. */
    private inner class Link(private val device: BluetoothDevice, name: String) {
        val address: String = device.address
        val session = SonySession()
        var state = Headphones(address, name, connected = true)
            private set
        private var socket: BluetoothSocket? = null
        private var closed = false
        private var initRetries = 0

        private val tick = object : Runnable {
            override fun run() {
                if (closed) return
                when {
                    session.waiting -> write(session.timeout())
                    session.version == null && initRetries++ < INIT_RETRIES -> write(session.retryInit())
                }
                handler.postDelayed(this, TICK_MS)
            }
        }

        fun update(transform: (Headphones) -> Headphones) {
            state = transform(state)
            publish(this)
        }

        fun open() {
            val service = serviceOf(device) ?: run {
                update { it.copy(supported = false) }
                return
            }
            Thread({ run(service) }, "headphones-$address").start()
        }

        /** Connects, then reads until the channel closes. Runs on its own thread. */
        private fun run(service: UUID) {
            var opening: BluetoothSocket? = null
            val s = try {
                device.createRfcommSocketToServiceRecord(service).also {
                    opening = it
                    it.connect()
                }
            } catch (e: IOException) {
                Log.i(TAG, "Could not open the channel to $address", e)
                opening?.let(::closeQuietly)
                handler.post(::failed)
                return
            } catch (e: SecurityException) {
                opening?.let(::closeQuietly)
                handler.post(::failed)
                return
            }
            handler.post {
                if (closed) {
                    closeQuietly(s)
                    return@post
                }
                socket = s
                write(session.start())
                handler.postDelayed(tick, TICK_MS)
            }
            val reader = SonyFrameReader()
            val buffer = ByteArray(256)
            try {
                val input = s.inputStream
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    for (i in 0 until n) {
                        val frame = reader.feed(buffer[i]) ?: continue
                        val message = SonyFrames.decode(frame) ?: continue
                        handler.post { receive(message) }
                    }
                }
            } catch (e: IOException) {
                // Closed: by us, or the headphones went away.
            }
            handler.post {
                if (links[address] === this) {
                    links.remove(address)
                    close()
                    scan()
                }
            }
        }

        /** The channel could not be opened (Sony's app may hold it): shown as connected, and tried again later. */
        private fun failed() {
            if (links[address] !== this) return
            links.remove(address)
            scan()
            handler.postDelayed(::scan, RETRY_MS + SETTLE_MS)
        }

        private fun receive(message: SonyMessage) {
            if (closed) return
            val (replies, event) = session.receive(message)
            write(replies)
            when (event) {
                is SonyEvent.Ready -> update { it.copy(ready = event.version == 1, supported = event.version == 1) }
                is SonyEvent.Battery -> update { it.copy(battery = event.level, charging = event.charging) }
                // While noise cancelling they report level 0: keep the last one, for ambient sound.
                is SonyEvent.Noise -> update { current ->
                    val control = event.control
                    val kept = if (control.mode == NoiseMode.NOISE_CANCELLING && control.ambientLevel == 0) {
                        control.copy(ambientLevel = current.noise?.ambientLevel?.takeIf { it > 0 } ?: DEFAULT_AMBIENT_LEVEL)
                    } else {
                        control
                    }
                    current.copy(noise = kept)
                }
                is SonyEvent.Equalizer -> update { it.copy(equalizer = event.preset) }
                null -> Unit
            }
        }

        fun write(frames: List<ByteArray>) {
            val out = socket?.outputStream ?: return
            try {
                for (frame in frames) out.write(frame)
                out.flush()
            } catch (e: IOException) {
                Log.i(TAG, "Could not write to $address", e)
            }
        }

        fun close() {
            closed = true
            handler.removeCallbacks(tick)
            socket?.let(::closeQuietly)
            socket = null
        }
    }

    /** The service to connect to: version 1's, which this app speaks; none when only version 2's is offered. */
    private fun serviceOf(device: BluetoothDevice): UUID? {
        val offered = try {
            device.uuids.orEmpty().map { it.uuid.toString().uppercase() }
        } catch (e: SecurityException) {
            emptyList()
        }
        return when {
            SonyHeadphonesIds.SERVICE_V1 in offered || offered.isEmpty() -> UUID.fromString(SonyHeadphonesIds.SERVICE_V1)
            SonyHeadphonesIds.SERVICE_V2 in offered -> null
            else -> UUID.fromString(SonyHeadphonesIds.SERVICE_V1)
        }
    }

    private fun closeQuietly(socket: BluetoothSocket) {
        try {
            socket.close()
        } catch (e: IOException) {
            Log.i(TAG, "Could not close a channel", e)
        }
    }

    private companion object {
        const val TAG = "HeadphonesManager"
        const val TICK_MS = 1_250L
        const val INIT_RETRIES = 2
        const val SETTLE_MS = 1_500L
        const val RETRY_MS = 10_000L
        const val DEFAULT_AMBIENT_LEVEL = 10
    }
}
