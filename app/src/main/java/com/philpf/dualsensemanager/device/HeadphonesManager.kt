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
import android.content.IntentSender
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.philpf.dualsensemanager.Permissions
import com.philpf.dualsensemanager.core.Headphones
import com.philpf.dualsensemanager.core.HeadphonesProblem
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
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.util.UUID

/** Sony headphones: their state, and their controls. */
interface HeadphonesSource {
    val headphones: StateFlow<List<Headphones>>

    /**
     * How many times each pair of headphones (by address) has been used while the app is on
     * screen: their button or touch panel changed their noise control, equalizer or volume.
     */
    val interactions: StateFlow<Map<String, Int>>

    /** Looks for headphones again (after the Nearby devices permission is granted). */
    fun refresh()

    fun setNoise(address: String, control: NoiseControl)

    fun setEqualizer(address: String, preset: SonyEqualizer)

    /** Sets the phone's media volume, theirs while they play the phone's sound. */
    fun setVolume(address: String, volume: Int)

    fun powerOff(address: String)

    /** Disconnects them from the phone, as [DeviceDisconnect] does. Call on the main thread. */
    fun disconnect(address: String, askUser: (IntentSender) -> Unit, onResult: (DisconnectResult) -> Unit)
}

/**
 * Sony headphones paired with the phone. While the app is on screen it talks to those that are
 * connected, over the RFCOMM channel Sony's own app uses (core/SonySession), to read their
 * battery and noise control and to change them; it lets go when the app leaves the screen.
 * Sony's app, if open at the same time, may hold that channel. The form in which each pair
 * answered for its noise control is kept, to be asked first next time.
 *
 * Device work happens on one background thread; each open channel has a thread that reads it.
 */
@SuppressLint("MissingPermission") // Every Bluetooth call is behind Permissions.hasBluetoothConnect.
class HeadphonesManager(private val context: Context) : HeadphonesSource {

    private val thread = HandlerThread("headphones").apply { start() }
    private val handler = Handler(thread.looper)
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val disconnector = DeviceDisconnect(context)

    private val _headphones = MutableStateFlow<List<Headphones>>(emptyList())
    override val headphones: StateFlow<List<Headphones>> = _headphones.asStateFlow()

    private val _interactions = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val interactions: StateFlow<Map<String, Int>> = _interactions.asStateFlow()

    // Only touched on [thread].
    private var visible = false
    private val profiles = HashMap<Int, BluetoothProfile>()
    private val links = HashMap<String, Link>()
    private val lastTry = HashMap<String, Long>()
    private val problems = HashMap<String, HeadphonesProblem>()
    private var receiverOn = false

    /** Headphones that play the phone's media sound, so that the media volume is theirs. */
    private var playing: Set<String> = emptySet()
    private var volume = 0
    private var maxVolume = 0

    /** The volume this app set last, and when: its echo is not the headphones being used. */
    private var ownVolume = -1
    private var ownVolumeAt = 0L

    private val receiver = object : BroadcastReceiver() {
        // Connections settle a moment after these arrive.
        override fun onReceive(context: Context, intent: Intent) {
            handler.postDelayed(::scan, SETTLE_MS)
        }
    }

    // Android's volume change broadcast: not in the SDK, but sent by every version.
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) == AudioManager.STREAM_MUSIC) handler.post(::volumeChanged)
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
            // Written in the form the headphones use.
            val next = control.copy(form = link.state.noise?.form ?: return@post)
            link.wrote()
            link.write(link.session.setNoise(next))
            link.update { it.copy(noise = next) }
        }
    }

    override fun setEqualizer(address: String, preset: SonyEqualizer) {
        handler.post {
            val link = links[address] ?: return@post
            val version = link.session.version ?: return@post
            link.wrote()
            link.write(link.session.send(SonyCommands.setEqualizer(version, preset)))
            link.update { it.copy(equalizer = preset) }
        }
    }

    override fun setVolume(address: String, volume: Int) {
        handler.post {
            val audio = audio ?: return@post
            if (address !in playing) return@post
            ownVolume = volume
            ownVolumeAt = SystemClock.elapsedRealtime()
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            } catch (e: SecurityException) {
                Log.i(TAG, "Could not set the volume", e)
            }
            volumeChanged()
        }
    }

    override fun powerOff(address: String) {
        handler.post {
            val link = links[address] ?: return@post
            val version = link.session.version ?: return@post
            link.write(link.session.send(SonyCommands.powerOff(version)))
        }
    }

    override fun disconnect(address: String, askUser: (IntentSender) -> Unit, onResult: (DisconnectResult) -> Unit) =
        disconnector.disconnect(address, askUser, onResult)

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
            // Sent by the system, which reaches receivers that are not exported.
            ContextCompat.registerReceiver(context, volumeReceiver, IntentFilter(ACTION_VOLUME_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverOn = true
        }
        audio?.let {
            volume = it.getStreamVolume(AudioManager.STREAM_MUSIC)
            maxVolume = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
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
            for (r in listOf(receiver, volumeReceiver)) {
                try {
                    context.unregisterReceiver(r)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Receiver was not registered", e)
                }
            }
            receiverOn = false
        }
        for ((type, proxy) in profiles) adapter?.closeProfileProxy(type, proxy)
        profiles.clear()
        lastTry.clear()
        playing = emptySet()
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
        val connected = profiles.values.flatMap { proxy -> connectedTo(proxy) }.toSet()
        playing = profiles[BluetoothProfile.A2DP]?.let { connectedTo(it).toSet() }.orEmpty()

        for (address in links.keys.toList()) {
            if (address !in connected || paired.none { it.address == address }) links.remove(address)?.close()
        }
        problems.keys.retainAll(connected)
        val now = System.currentTimeMillis()
        for (device in paired) {
            if (device.address !in connected || device.address in links) continue
            if (now - (lastTry[device.address] ?: 0L) < RETRY_MS) continue
            lastTry[device.address] = now
            links[device.address] = Link(device, nameOf(device)).also { it.open() }
        }
        _headphones.value = paired.map { device ->
            present(
                links[device.address]?.state
                    ?: Headphones(device.address, nameOf(device), connected = device.address in connected, problem = problems[device.address]),
            )
        }
    }

    private fun connectedTo(proxy: BluetoothProfile): List<String> = try {
        proxy.connectedDevices.map { it.address }
    } catch (e: SecurityException) {
        emptyList()
    }

    private fun nameOf(device: BluetoothDevice): String = try {
        device.alias ?: device.name ?: device.address
    } catch (e: SecurityException) {
        device.address
    }

    /** [state] with the media volume, when they play the phone's sound. */
    private fun present(state: Headphones): Headphones {
        val audible = state.connected && state.address in playing && audio != null
        return state.copy(volume = if (audible) volume else null, maxVolume = maxVolume)
    }

    private fun publish(link: Link) {
        if (links[link.address] !== link) return
        _headphones.value = _headphones.value.map { if (it.address == link.address) present(link.state) else it }
    }

    /** The media volume changed: shown, and, unless this app changed it, a use of the headphones playing. */
    private fun volumeChanged() {
        val audio = audio ?: return
        val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (now == volume) return
        volume = now
        val own = now == ownVolume && SystemClock.elapsedRealtime() - ownVolumeAt < OWN_CHANGE_MS
        if (!own) playing.forEach(::interacted)
        _headphones.value = _headphones.value.map(::present)
    }

    private fun interacted(address: String) {
        _interactions.update { it + (address to (it[address] ?: 0) + 1) }
    }

    private fun noiseKey(address: String) = "noise:$address"

    /** One open channel to a pair of headphones. */
    private inner class Link(private val device: BluetoothDevice, name: String) {
        val address: String = device.address
        val session = SonySession(knownNoise = prefs.getInt(noiseKey(address), -1).takeIf { it >= 0 })
        var state = Headphones(address, name, connected = true, problem = problems[address])
            private set
        private var socket: BluetoothSocket? = null
        private var closed = false
        private var asked = 0
        private var readyTicks = 0
        private var lastWrite = 0L

        private val tick = object : Runnable {
            override fun run() {
                if (closed) return
                if (session.version == null) {
                    // The first request, asked again until answered.
                    if (asked >= INIT_TRIES) {
                        failed(HeadphonesProblem.NO_ANSWER)
                        return
                    }
                    asked++
                    write(session.restart())
                } else {
                    if (session.waiting) write(session.timeout())
                    // Version 2 headphones whose noise control has not answered: the other forms.
                    if (session.version == 2 && ++readyTicks == GUESS_TICKS) write(session.guessNoise())
                }
                handler.postDelayed(this, TICK_MS)
            }
        }

        fun update(transform: (Headphones) -> Headphones) {
            state = transform(state)
            publish(this)
        }

        /** This app changed something: what they report next is not them being used. */
        fun wrote() {
            lastWrite = SystemClock.elapsedRealtime()
        }

        fun open() {
            val services = servicesOf(device)
            Thread({ run(services) }, "headphones-$address").start()
        }

        /** Connects to the first of [services] that opens, then reads until the channel closes. Runs on its own thread. */
        private fun run(services: List<UUID>) {
            val s = services.firstNotNullOfOrNull(::connect) ?: run {
                handler.post { failed(HeadphonesProblem.CHANNEL) }
                return
            }
            handler.post {
                if (closed) {
                    closeQuietly(s)
                    return@post
                }
                socket = s
                asked = 1
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
                    handler.postDelayed(::scan, RETRY_MS + SETTLE_MS)
                }
            }
        }

        private fun connect(service: UUID): BluetoothSocket? {
            var opening: BluetoothSocket? = null
            return try {
                device.createRfcommSocketToServiceRecord(service).also {
                    opening = it
                    it.connect()
                }
            } catch (e: IOException) {
                Log.i(TAG, "Could not open $service on $address", e)
                opening?.let(::closeQuietly)
                null
            } catch (e: SecurityException) {
                opening?.let(::closeQuietly)
                null
            }
        }

        /** The channel could not be opened, or they did not answer: shown with [problem], and tried again later. */
        private fun failed(problem: HeadphonesProblem) {
            if (links[address] !== this) return
            Log.i(TAG, "$address: $problem")
            problems[address] = problem
            links.remove(address)
            close()
            scan()
            handler.postDelayed(::scan, RETRY_MS + SETTLE_MS)
        }

        private fun receive(message: SonyMessage) {
            if (closed) return
            val (replies, event) = session.receive(message)
            write(replies)
            // A change they report on their own, not shortly after this app asked for one: their button or touch panel.
            val used = SystemClock.elapsedRealtime() - lastWrite > OWN_CHANGE_MS
            when (event) {
                is SonyEvent.Ready -> {
                    problems.remove(address)
                    update { it.copy(ready = true, problem = null) }
                }
                is SonyEvent.Functions -> Unit
                is SonyEvent.Battery -> update { it.copy(battery = event.level, charging = event.charging) }
                is SonyEvent.Noise -> {
                    if (event.notified && used) interacted(address)
                    val inquiry = event.control.form.inquiry
                    if (session.version == 2 && prefs.getInt(noiseKey(address), -1) != inquiry) {
                        prefs.edit().putInt(noiseKey(address), inquiry).apply()
                    }
                    // While noise cancelling they report level 0: keep the last one, for ambient sound.
                    update { current ->
                        val control = event.control
                        val kept = if (control.mode == NoiseMode.NOISE_CANCELLING && control.ambientLevel == 0) {
                            control.copy(ambientLevel = current.noise?.ambientLevel?.takeIf { it > 0 } ?: DEFAULT_AMBIENT_LEVEL)
                        } else {
                            control
                        }
                        current.copy(noise = kept)
                    }
                }
                is SonyEvent.Equalizer -> {
                    if (event.notified && used) interacted(address)
                    update { it.copy(equalizer = event.preset) }
                }
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

    /**
     * The services to try, in turn: version 2's first (as Gadgetbridge and Cans do) unless only
     * version 1's is offered. The version spoken is then told by the first answer.
     */
    private fun servicesOf(device: BluetoothDevice): List<UUID> {
        val offered = try {
            device.uuids.orEmpty().map { it.uuid.toString().uppercase() }
        } catch (e: SecurityException) {
            emptyList()
        }
        val v1 = UUID.fromString(SonyHeadphonesIds.SERVICE_V1)
        val v2 = UUID.fromString(SonyHeadphonesIds.SERVICE_V2)
        return if (SonyHeadphonesIds.SERVICE_V1 in offered && SonyHeadphonesIds.SERVICE_V2 !in offered) listOf(v1, v2) else listOf(v2, v1)
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
        const val PREFS = "headphones"
        const val TICK_MS = 1_250L

        /** The first request is sent up to this many times, a tick apart (Cans: 8, 1.2 s apart). */
        const val INIT_TRIES = 8

        /** Ticks after the version is known before the noise control is asked for in other forms. */
        const val GUESS_TICKS = 3
        const val SETTLE_MS = 1_500L
        const val RETRY_MS = 10_000L

        /** How long after this app changes something a report of it is taken as its echo. */
        const val OWN_CHANGE_MS = 1_500L
        const val DEFAULT_AMBIENT_LEVEL = 10
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
