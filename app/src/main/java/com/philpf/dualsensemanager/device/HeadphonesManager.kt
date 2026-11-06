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
import com.philpf.dualsensemanager.core.BudPart
import com.philpf.dualsensemanager.core.EqPreset
import com.philpf.dualsensemanager.core.Headphones
import com.philpf.dualsensemanager.core.HeadphonesKind
import com.philpf.dualsensemanager.core.HeadphonesProblem
import com.philpf.dualsensemanager.core.NoiseControl
import com.philpf.dualsensemanager.core.NoiseMode
import com.philpf.dualsensemanager.core.OnePlusBuds
import com.philpf.dualsensemanager.core.OnePlusSession
import com.philpf.dualsensemanager.core.OpoEvent
import com.philpf.dualsensemanager.core.OpoFrame
import com.philpf.dualsensemanager.core.OpoFrameReader
import com.philpf.dualsensemanager.core.OpoNoise
import com.philpf.dualsensemanager.core.PartBattery
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

/** Headphones and earbuds: their state, and their controls. */
interface HeadphonesSource {
    val headphones: StateFlow<List<Headphones>>

    /**
     * How many times each pair (by address) has been used while the app is on screen: their
     * button, touch panel or gesture changed something, or the media volume changed.
     */
    val interactions: StateFlow<Map<String, Int>>

    /** Looks for headphones again (after the Nearby devices permission is granted). */
    fun refresh()

    fun setNoise(address: String, control: NoiseControl)

    fun setEqualizer(address: String, preset: EqPreset)

    /** Sets the phone's media volume, theirs while they play the phone's sound. */
    fun setVolume(address: String, volume: Int)

    /** Turns them off (Sony headphones; earbuds go back in their case). */
    fun powerOff(address: String)

    /** Disconnects them from the phone, as [DeviceDisconnect] does. Call on the main thread. */
    fun disconnect(address: String, askUser: (IntentSender) -> Unit, onResult: (DisconnectResult) -> Unit)
}

/**
 * Sony headphones and OnePlus earbuds paired with the phone. While the app is on screen it talks
 * to those that are connected, over the RFCOMM channel their maker's app uses (core/SonySession,
 * core/OnePlusSession), to read their battery, noise control and equalizer and to change them;
 * it lets go when the app leaves the screen. Their maker's app, if open at the same time, may
 * hold that channel. The form in which each Sony pair answered for its noise control is kept, to
 * be asked first next time.
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
        handler.post { links[address]?.setNoise(control) }
    }

    override fun setEqualizer(address: String, preset: EqPreset) {
        handler.post { links[address]?.setEqualizer(preset) }
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
        handler.post { links[address]?.powerOff() }
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
        _headphones.value = _headphones.value.map { Headphones(it.address, it.name, connected = false, kind = it.kind) }
    }

    /** Sony headphones or OnePlus earbuds, by name and services; null for any other device. */
    private fun kindOf(device: BluetoothDevice): HeadphonesKind? {
        if (device.type == BluetoothDevice.DEVICE_TYPE_LE) return null
        val name = device.name
        return when {
            OnePlusBuds.isOnePlus(name) -> HeadphonesKind.ONEPLUS
            SonyHeadphonesIds.isSony(name, device.uuids.orEmpty().map { it.uuid.toString() }) -> HeadphonesKind.SONY
            else -> null
        }
    }

    /** Lists paired headphones and opens a channel to each that is connected. */
    private fun scan() {
        if (!visible) return
        val adapter = adapter ?: return
        if (!Permissions.hasBluetoothConnect(context)) return
        val paired = try {
            adapter.bondedDevices.orEmpty().mapNotNull { device -> kindOf(device)?.let { device to it } }
        } catch (e: SecurityException) {
            emptyList()
        }
        val connected = profiles.values.flatMap { proxy -> connectedTo(proxy) }.toSet()
        playing = profiles[BluetoothProfile.A2DP]?.let { connectedTo(it).toSet() }.orEmpty()

        for (address in links.keys.toList()) {
            if (address !in connected || paired.none { it.first.address == address }) links.remove(address)?.close()
        }
        problems.keys.retainAll(connected)
        val now = System.currentTimeMillis()
        for ((device, kind) in paired) {
            if (device.address !in connected || device.address in links) continue
            if (now - (lastTry[device.address] ?: 0L) < RETRY_MS) continue
            lastTry[device.address] = now
            val link = if (kind == HeadphonesKind.ONEPLUS) OnePlusLink(device, nameOf(device)) else SonyLink(device, nameOf(device))
            links[device.address] = link.also { it.open() }
        }
        _headphones.value = paired.map { (device, kind) ->
            present(
                links[device.address]?.state ?: Headphones(
                    device.address,
                    nameOf(device),
                    connected = device.address in connected,
                    kind = kind,
                    problem = problems[device.address],
                ),
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

    /** An RFCOMM service to connect to, by [uuid], with or without encryption. */
    private class Service(val uuid: UUID, val secure: Boolean = true)

    /**
     * One open channel to a pair of headphones: connecting to the first of [services] that
     * opens, reading on its own thread, retrying, and giving up with a problem. What is said on
     * the channel is the protocol's: [SonyLink], [OnePlusLink].
     */
    private abstract inner class Link(protected val device: BluetoothDevice, name: String, kind: HeadphonesKind) {
        val address: String = device.address
        var state = Headphones(address, name, connected = true, kind = kind, problem = problems[address])
            private set
        private var socket: BluetoothSocket? = null
        protected var closed = false
            private set
        private var lastWrite = 0L

        /** How many times the first request has been sent. */
        protected var asked = 0

        protected abstract val services: List<Service>

        /** The channel is open: start talking. On [thread]. */
        protected abstract fun begin()

        /** Bytes read, on the channel's own thread. */
        protected abstract fun read(buffer: ByteArray, count: Int)

        /** A tick: requests sent again, given up, or guessed. Returns false when the link gave up. */
        protected abstract fun onTick(): Boolean

        abstract fun setNoise(control: NoiseControl)

        abstract fun setEqualizer(preset: EqPreset)

        abstract fun powerOff()

        private val tick = object : Runnable {
            override fun run() {
                if (closed) return
                if (onTick()) handler.postDelayed(this, TICK_MS)
            }
        }

        fun update(transform: (Headphones) -> Headphones) {
            state = transform(state)
            publish(this)
        }

        /** They answered: ready, and no problem any more. */
        protected fun answered() {
            if (state.ready) return
            problems.remove(address)
            update { it.copy(ready = true, problem = null) }
        }

        /** This app changed something: what they report next is not them being used. */
        protected fun wrote() {
            lastWrite = SystemClock.elapsedRealtime()
        }

        /**
         * They reported a change: a use of theirs (their button, touch panel or gesture) when
         * [notified] by them and not shortly after this app asked for one.
         */
        protected fun reportUse(notified: Boolean) {
            if (notified && SystemClock.elapsedRealtime() - lastWrite > OWN_CHANGE_MS) interacted(address)
        }

        fun open() {
            val services = services
            Thread({ run(services) }, "headphones-$address").start()
        }

        /** Connects to the first of [services] that opens, then reads until the channel closes. Runs on its own thread. */
        private fun run(services: List<Service>) {
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
                begin()
                handler.postDelayed(tick, TICK_MS)
            }
            val buffer = ByteArray(512)
            try {
                val input = s.inputStream
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    read(buffer, n)
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

        private fun connect(service: Service): BluetoothSocket? {
            var opening: BluetoothSocket? = null
            return try {
                val socket = if (service.secure) {
                    device.createRfcommSocketToServiceRecord(service.uuid)
                } else {
                    device.createInsecureRfcommSocketToServiceRecord(service.uuid)
                }
                socket.also {
                    opening = it
                    it.connect()
                }
            } catch (e: IOException) {
                Log.i(TAG, "Could not open ${service.uuid} on $address", e)
                opening?.let(::closeQuietly)
                null
            } catch (e: SecurityException) {
                opening?.let(::closeQuietly)
                null
            }
        }

        /** The channel could not be opened, or they did not answer: shown with [problem], and tried again later. */
        protected fun failed(problem: HeadphonesProblem) {
            if (links[address] !== this) return
            Log.i(TAG, "$address: $problem")
            problems[address] = problem
            links.remove(address)
            close()
            scan()
            handler.postDelayed(::scan, RETRY_MS + SETTLE_MS)
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

    private fun noiseKey(address: String) = "noise:$address"

    /** Sony's protocol (core/SonySession). */
    private inner class SonyLink(device: BluetoothDevice, name: String) : Link(device, name, HeadphonesKind.SONY) {
        val session = SonySession(knownNoise = prefs.getInt(noiseKey(address), -1).takeIf { it >= 0 })
        private val reader = SonyFrameReader()
        private var readyTicks = 0

        /** Version 2's service first (as Gadgetbridge and Cans do) unless only version 1's is offered; the first answer tells the version. */
        override val services: List<Service>
            get() {
                val offered = try {
                    device.uuids.orEmpty().map { it.uuid.toString().uppercase() }
                } catch (e: SecurityException) {
                    emptyList()
                }
                val v1 = Service(UUID.fromString(SonyHeadphonesIds.SERVICE_V1))
                val v2 = Service(UUID.fromString(SonyHeadphonesIds.SERVICE_V2))
                return if (SonyHeadphonesIds.SERVICE_V1 in offered && SonyHeadphonesIds.SERVICE_V2 !in offered) listOf(v1, v2) else listOf(v2, v1)
            }

        override fun begin() {
            asked = 1
            write(session.start())
        }

        override fun read(buffer: ByteArray, count: Int) {
            for (i in 0 until count) {
                val frame = reader.feed(buffer[i]) ?: continue
                val message = SonyFrames.decode(frame) ?: continue
                handler.post { receive(message) }
            }
        }

        override fun onTick(): Boolean {
            if (session.version == null) {
                // The first request, asked again until answered.
                if (asked >= INIT_TRIES) {
                    failed(HeadphonesProblem.NO_ANSWER)
                    return false
                }
                asked++
                write(session.restart())
            } else {
                if (session.waiting) write(session.timeout())
                // Version 2 headphones whose noise control has not answered: the other forms.
                if (session.version == 2 && ++readyTicks == GUESS_TICKS) write(session.guessNoise())
            }
            return true
        }

        override fun setNoise(control: NoiseControl) {
            // Written in the form the headphones use.
            val next = control.copy(form = state.noise?.form ?: return)
            wrote()
            write(session.setNoise(next))
            update { it.copy(noise = next) }
        }

        override fun setEqualizer(preset: EqPreset) {
            val version = session.version ?: return
            wrote()
            write(session.send(SonyCommands.setEqualizer(version, SonyEqualizer.fromCode(preset.id))))
            update { it.copy(equalizer = preset) }
        }

        override fun powerOff() {
            val version = session.version ?: return
            write(session.send(SonyCommands.powerOff(version)))
        }

        private fun receive(message: SonyMessage) {
            if (closed) return
            val (replies, event) = session.receive(message)
            write(replies)
            when (event) {
                is SonyEvent.Ready -> {
                    answered()
                    update { it.copy(presets = SonyEqualizer.presets) }
                }
                is SonyEvent.Functions -> Unit
                is SonyEvent.Battery -> update { it.copy(battery = event.level, charging = event.charging) }
                is SonyEvent.Noise -> {
                    reportUse(event.notified)
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
                    reportUse(event.notified)
                    update { it.copy(equalizer = event.preset.preset) }
                }
                null -> Unit
            }
        }
    }

    /** OnePlus earbuds' protocol (core/OnePlusSession). */
    private inner class OnePlusLink(device: BluetoothDevice, name: String) : Link(device, name, HeadphonesKind.ONEPLUS) {
        val session = OnePlusSession()
        private val reader = OpoFrameReader()

        // HeyMelody's order: its service secure, then without encryption, then the second one.
        override val services = listOf(
            Service(UUID.fromString(OnePlusBuds.SERVICE)),
            Service(UUID.fromString(OnePlusBuds.SERVICE), secure = false),
            Service(UUID.fromString(OnePlusBuds.SERVICE_2)),
        )

        override fun begin() {
            // HeyMelody waits 300 ms before its first message.
            handler.postDelayed({
                if (!closed) {
                    asked = 1
                    write(session.start())
                }
            }, FIRST_MESSAGE_MS)
        }

        override fun read(buffer: ByteArray, count: Int) {
            val frames = reader.feed(buffer, count)
            if (frames.isNotEmpty()) handler.post { frames.forEach(::receive) }
        }

        override fun onTick(): Boolean {
            if (!session.ready) {
                if (asked >= INIT_TRIES) {
                    failed(HeadphonesProblem.NO_ANSWER)
                    return false
                }
                if (asked > 0) {
                    asked++
                    write(if (session.waiting) session.timeout() else session.restart())
                }
            } else if (session.waiting) {
                write(session.timeout())
            }
            return true
        }

        override fun setNoise(control: NoiseControl) {
            val next = control.copy(strength = if (control.mode == NoiseMode.NOISE_CANCELLING) control.strength else null)
            wrote()
            write(session.setNoise(OpoNoise.mask(next.mode, next.strength)))
            update { it.copy(noise = next) }
        }

        override fun setEqualizer(preset: EqPreset) {
            wrote()
            write(session.setEqualizer(preset.id))
            update { it.copy(equalizer = preset) }
        }

        // Earbuds turn off in their case; they take no such command.
        override fun powerOff() = Unit

        private fun receive(frame: OpoFrame) {
            if (closed) return
            val (replies, event) = session.receive(frame, System.currentTimeMillis() / 1000)
            write(replies)
            if (session.ready) answered()
            when (event) {
                is OpoEvent.Product -> update { current ->
                    val presets = OnePlusBuds.presets(event.id)
                    current.copy(presets = presets, equalizer = current.equalizer?.let { OnePlusBuds.preset(it.id, presets) })
                }
                // A part left out keeps its last level (the case is reported only while open).
                is OpoEvent.Battery -> update { current ->
                    val parts = (current.parts.filter { old -> event.parts.none { it.part == old.part } } + event.parts).sortedBy { it.part }
                    current.copy(parts = parts)
                }
                is OpoEvent.Noise -> {
                    reportUse(event.notified)
                    update { it.copy(noise = NoiseControl(event.mode, 0, false, strength = event.strength)) }
                }
                is OpoEvent.Equalizer -> {
                    reportUse(event.notified)
                    update { current ->
                        val presets = current.presets.ifEmpty { OnePlusBuds.presets(session.product) }
                        current.copy(presets = presets, equalizer = OnePlusBuds.preset(event.id, presets))
                    }
                }
                OpoEvent.Gesture -> reportUse(notified = true)
                // Refused (no bud in an ear): the change is read back, which shows the mode they kept.
                is OpoEvent.Refused, is OpoEvent.Offered, null -> Unit
            }
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
        const val PREFS = "headphones"
        const val TICK_MS = 1_250L
        const val FIRST_MESSAGE_MS = 300L

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
