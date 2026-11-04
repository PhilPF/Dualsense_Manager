package com.philpf.dualsensemanager.device

import android.content.Context
import android.content.IntentSender
import android.hardware.BatteryState
import android.hardware.input.InputManager
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.hardware.usb.UsbManager
import android.os.CombinedVibration
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.VibrationEffect
import android.util.Log
import android.view.InputDevice
import com.philpf.dualsensemanager.core.AlertState
import com.philpf.dualsensemanager.core.BatteryAlert
import com.philpf.dualsensemanager.core.BatteryAlerts
import com.philpf.dualsensemanager.core.BatteryReading
import com.philpf.dualsensemanager.core.BatteryRules
import com.philpf.dualsensemanager.core.ChargeStatus
import com.philpf.dualsensemanager.core.ControllerKind
import com.philpf.dualsensemanager.core.ControllerProfile
import com.philpf.dualsensemanager.core.LedPalette
import com.philpf.dualsensemanager.core.LiveController
import com.philpf.dualsensemanager.core.Transport
import com.philpf.dualsensemanager.core.Player
import com.philpf.dualsensemanager.core.PlayerLeds
import com.philpf.dualsensemanager.core.Players
import com.philpf.dualsensemanager.core.TransportRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Watches for DualSense controllers, sets their lights and reads their batteries.
 *
 * Lights are set through Android's per-device [LightsManager] (Android 12+): the light bar is
 * an RGB light, and the five player LEDs show up as five separate lights named "...player-1"
 * to "...player-5". A light session stays open for as long as the controller is connected,
 * because Android turns the lights off when the session closes, including when this app's
 * process dies. That is why the app keeps a small foreground service running while
 * controllers are connected.
 *
 * All device work happens on one background thread; results are published as [StateFlow]s.
 */
class ControllerManager(
    context: Context,
    private val store: ProfileStore,
    /** Called on the main thread when the first controller connects. */
    private val onFirstControllerConnected: () -> Unit,
    /** Called on the main thread when a battery alert should be shown. */
    private val onBatteryAlert: (controllerId: String, alert: BatteryAlert, reading: BatteryReading) -> Unit,
) : ControllerSource {

    private val inputManager: InputManager = context.getSystemService(InputManager::class.java)
    private val disconnector = ControllerDisconnect(context)
    private val usbManager: UsbManager? = context.getSystemService(UsbManager::class.java)

    private val thread = HandlerThread("controller-manager").apply { start() }
    private val handler = Handler(thread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    override val profiles: StateFlow<Map<String, ControllerProfile>> get() = store.profiles
    override val players: StateFlow<Map<String, Player>> get() = store.players

    private val _live = MutableStateFlow<List<LiveController>>(emptyList())
    override val live: StateFlow<List<LiveController>> = _live.asStateFlow()

    private val _previews = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val previews: StateFlow<Map<String, Int>> = _previews.asStateFlow()

    /** A connected controller and the light session that holds its lights. */
    private class Attached(val deviceId: Int, val id: String) {
        var session: LightsManager.LightsSession? = null
        var lightsShown = false
    }

    // Only touched on [thread].
    private val attached = HashMap<Int, Attached>()
    private val pendingApply = HashSet<String>()
    private val blink = HashMap<String, Boolean>()
    private val alertStates = HashMap<String, AlertState>()
    private var started = false

    @Volatile
    private var uiVisible = false

    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = rescan()
        override fun onInputDeviceRemoved(deviceId: Int) = rescan()
        override fun onInputDeviceChanged(deviceId: Int) = rescan()
    }

    private val poll = object : Runnable {
        override fun run() {
            if (attached.isNotEmpty()) publish()
            handler.postDelayed(this, if (uiVisible) POLL_VISIBLE_MS else POLL_BACKGROUND_MS)
        }
    }

    fun start() {
        handler.post {
            if (started) return@post
            started = true
            inputManager.registerInputDeviceListener(listener, handler)
            rescan()
            handler.postDelayed(poll, POLL_VISIBLE_MS)
        }
    }

    /** The app's screen is visible: refresh now and poll the batteries more often. */
    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        if (visible) handler.post { rescan() }
    }

    override fun preview(controllerId: String, rgb: Int) {
        _previews.update { it + (controllerId to (rgb and 0xFFFFFF)) }
        scheduleApply(controllerId)
    }

    override fun endPreview(controllerId: String) {
        _previews.update { it - controllerId }
        scheduleApply(controllerId)
    }

    override fun setColor(controllerId: String, rgb: Int) {
        val profile = store.get(controllerId) ?: return
        val playerId = profile.playerId
        if (playerId != null && store.player(playerId) != null) {
            store.updatePlayer(playerId) { it.copy(color = rgb and 0xFFFFFF) }
        } else {
            store.update(controllerId) { it.copy(color = rgb and 0xFFFFFF, customColor = true) }
        }
        _previews.update { it - controllerId }
        scheduleApply(controllerId)
    }

    override fun useAutomaticColor(controllerId: String) {
        val taken = store.colorsInUse(exceptController = controllerId)
        store.update(controllerId) { it.copy(color = LedPalette.automaticColor(it.number, taken), customColor = false) }
        _previews.update { it - controllerId }
        scheduleApply(controllerId)
    }

    override fun assignPlayer(controllerId: String, playerId: String?) {
        store.assignPlayer(controllerId, playerId)
        applyAll()
    }

    override fun newPlayer(controllerId: String, name: String, color: Int): String? {
        if (store.get(controllerId) == null) return null
        val player = store.createPlayer(name.ifBlank { Players.defaultName(store.players.value.values.map { it.name }) }, color)
        store.assignPlayer(controllerId, player.id)
        _previews.update { it - controllerId }
        applyAll()
        return player.id
    }

    override fun updatePlayer(playerId: String, name: String, color: Int) {
        store.updatePlayer(playerId) { it.copy(name = name, color = color and 0xFFFFFF) }
        val controllers = store.profiles.value.values.filter { it.playerId == playerId }.map { it.id }.toSet()
        _previews.update { it - controllers }
        applyAll()
    }

    override fun reorder(order: List<String>) {
        store.reorder(order)
        applyAll()
    }

    override fun deletePlayer(playerId: String) {
        store.deletePlayer(playerId)
        applyAll()
    }

    override fun canDisconnect(controllerId: String): Boolean =
        _live.value.any { it.id == controllerId && it.transport == Transport.BLUETOOTH }

    override fun disconnect(controllerId: String, askUser: (IntentSender) -> Unit, onResult: (DisconnectResult) -> Unit) {
        val controller = _live.value.firstOrNull { it.id == controllerId } ?: return onResult(DisconnectResult.InSettings(null))
        val device = inputManager.getInputDevice(controller.deviceId) ?: return onResult(DisconnectResult.InSettings(null))
        disconnector.disconnect(controllerId, device.vendorId, device.productId, askUser, onResult)
    }

    override fun identify(controllerId: String) {
        handler.post {
            val targets = attached.values.filter { it.id == controllerId }
            if (targets.isEmpty()) return@post
            targets.forEach { vibrate(it.deviceId) }
            BLINK_STEPS.forEachIndexed { i, on ->
                handler.postDelayed({
                    if (on == null) blink.remove(controllerId) else blink[controllerId] = on
                    attached.values.filter { it.id == controllerId }.forEach(::applyLight)
                }, i * BLINK_STEP_MS)
            }
        }
    }

    override fun forget(controllerId: String) {
        if (_live.value.none { it.id == controllerId }) store.forget(controllerId)
    }

    // ---- Everything below runs on [thread]. ----

    private fun applyAll() {
        handler.post { attached.values.forEach(::applyLight) }
    }

    private fun scheduleApply(id: String) {
        handler.post {
            // Coalesce bursts (slider drags) into at most one light update per frame.
            if (!pendingApply.add(id)) return@post
            handler.postDelayed({
                pendingApply.remove(id)
                attached.values.filter { it.id == id }.forEach(::applyLight)
            }, APPLY_COALESCE_MS)
        }
    }

    private fun rescan() {
        val found = LinkedHashMap<String, Pair<InputDevice, ControllerKind>>()
        for (deviceId in inputManager.inputDeviceIds) {
            val device = inputManager.getInputDevice(deviceId) ?: continue
            if (device.isVirtual) continue
            val kind = ControllerKind.fromIds(device.vendorId, device.productId) ?: continue
            val isPad = device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
                device.supportsSource(InputDevice.SOURCE_JOYSTICK)
            if (!isPad) continue
            // Touchpad and motion sensors share the descriptor; keep one device per controller.
            found.putIfAbsent(device.descriptor, device to kind)
        }

        val now = System.currentTimeMillis()
        val present = found.values.mapTo(HashSet()) { it.first.id }
        for (deviceId in attached.keys.filter { it !in present }) {
            val gone = attached.remove(deviceId) ?: continue
            closeSession(gone)
            alertStates.remove(gone.id)
            blink.remove(gone.id)
            store.update(gone.id) { it.copy(lastSeen = now) }
        }

        val wasEmpty = attached.isEmpty()
        var added = false
        for ((descriptor, entry) in found) {
            val (device, kind) = entry
            val existing = attached[device.id]
            if (existing == null) {
                store.ensure(descriptor, kind, now)
                val a = Attached(device.id, descriptor)
                attached[device.id] = a
                applyLight(a)
                followUp(device.id)
                added = true
            } else if (!existing.lightsShown) {
                // The kernel registers the lights just after the input device; try again.
                applyLight(existing)
            }
        }
        publish()
        if (wasEmpty && added) mainHandler.post(onFirstControllerConnected)
    }

    /** Re-checks shortly after a connection: the lights and the first battery report arrive late. */
    private fun followUp(deviceId: Int) {
        for (delayMs in FOLLOW_UP_DELAYS_MS) {
            handler.postDelayed({
                val a = attached[deviceId] ?: return@postDelayed
                if (!a.lightsShown) applyLight(a)
                publish()
            }, delayMs)
        }
    }

    /** Sets the light bar to the controller's colour and the player LEDs to its number. */
    private fun applyLight(a: Attached) {
        val profile = store.get(a.id) ?: return
        val device = inputManager.getInputDevice(a.deviceId) ?: return
        val lightsManager = device.lightsManager
        val lights = try {
            lightsManager.lights
        } catch (e: RuntimeException) {
            emptyList()
        }
        val blinking = blink[a.id]
        val color = when (blinking) {
            false -> 0
            else -> _previews.value[a.id] ?: store.colorOf(profile)
        }
        val playerLeds = when (blinking) {
            true -> setOf(1, 2, 3, 4, 5)
            false -> emptySet()
            null -> PlayerLeds.pattern(profile.number)
        }

        val request = LightsRequest.Builder()
        var count = 0
        for (light in lights) {
            val state = when {
                light.hasRgbControl() -> LightState.Builder().setColor(OPAQUE or (color and 0xFFFFFF)).build()
                light.type == Light.LIGHT_TYPE_PLAYER_ID -> LightState.Builder().setPlayerId(profile.number).build()
                light.type == Light.LIGHT_TYPE_INPUT -> {
                    val position = PlayerLeds.position(light.name) ?: continue
                    LightState.Builder().setColor(if (position in playerLeds) LED_ON else LED_OFF).build()
                }
                else -> continue
            }
            request.addLight(light, state)
            count++
        }
        if (count == 0) return
        try {
            val session = a.session ?: lightsManager.openSession().also { a.session = it }
            session.requestLights(request.build())
            a.lightsShown = true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not set the lights of ${device.name}", e)
        }
    }

    private fun vibrate(deviceId: Int) {
        val device = inputManager.getInputDevice(deviceId) ?: return
        try {
            val vibrators = device.vibratorManager
            if (vibrators.vibratorIds.isEmpty()) return
            val effect = VibrationEffect.createWaveform(RUMBLE_TIMINGS, RUMBLE_AMPLITUDES, -1)
            vibrators.vibrate(CombinedVibration.createParallel(effect))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not rumble ${device.name}", e)
        }
    }

    private fun rgbLights(device: InputDevice): Boolean = try {
        device.lightsManager.lights.any { it.hasRgbControl() }
    } catch (e: RuntimeException) {
        false
    }

    private fun closeSession(a: Attached) {
        try {
            a.session?.close()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not close the light session", e)
        }
        a.session = null
    }

    private fun publish() {
        val usbCount = sonyUsbDeviceCount()
        val now = System.currentTimeMillis()
        _live.value = attached.values.mapNotNull { a ->
            val device = inputManager.getInputDevice(a.deviceId) ?: return@mapNotNull null
            val kind = ControllerKind.fromIds(device.vendorId, device.productId) ?: return@mapNotNull null
            val battery = readBattery(device)
            recordSighting(a.id, battery, now)
            checkAlerts(a.id, battery)
            LiveController(
                id = a.id,
                deviceId = a.deviceId,
                kind = kind,
                transport = TransportRules.transport(device.name, usbCount),
                battery = battery,
                lightBar = rgbLights(device),
            )
        }
    }

    private fun checkAlerts(id: String, battery: BatteryReading?) {
        val (next, alert) = BatteryAlerts.step(alertStates[id] ?: AlertState(), battery)
        alertStates[id] = next
        if (alert != null && battery != null) mainHandler.post { onBatteryAlert(id, alert, battery) }
    }

    private fun readBattery(device: InputDevice): BatteryReading? {
        val state = try {
            device.batteryState
        } catch (e: RuntimeException) {
            return null
        }
        if (!state.isPresent) return null
        val status = when (state.status) {
            BatteryState.STATUS_CHARGING -> ChargeStatus.CHARGING
            BatteryState.STATUS_DISCHARGING -> ChargeStatus.DISCHARGING
            BatteryState.STATUS_FULL -> ChargeStatus.FULL
            BatteryState.STATUS_NOT_CHARGING -> ChargeStatus.NOT_CHARGING
            else -> ChargeStatus.UNKNOWN
        }
        return BatteryRules.reading(state.capacity, status)
    }

    /** Saves "last seen" and the battery level, without rewriting storage on every poll. */
    private fun recordSighting(id: String, battery: BatteryReading?, now: Long) {
        val profile = store.get(id) ?: return
        val percent = battery?.percent ?: profile.lastBattery
        if (percent != profile.lastBattery || now - profile.lastSeen > SIGHTING_INTERVAL_MS) {
            store.update(id) { it.copy(lastSeen = now, lastBattery = percent) }
        }
    }

    private fun sonyUsbDeviceCount(): Int = try {
        usbManager?.deviceList?.values?.count { ControllerKind.fromIds(it.vendorId, it.productId) != null } ?: 0
    } catch (e: RuntimeException) {
        0
    }

    private companion object {
        const val TAG = "ControllerManager"
        const val OPAQUE = 0xFF shl 24
        const val LED_ON = -1 // 0xFFFFFFFF: full brightness
        const val LED_OFF = 0
        const val POLL_VISIBLE_MS = 5_000L
        const val POLL_BACKGROUND_MS = 60_000L
        const val APPLY_COALESCE_MS = 16L
        const val SIGHTING_INTERVAL_MS = 5 * 60_000L
        const val BLINK_STEP_MS = 230L
        val FOLLOW_UP_DELAYS_MS = longArrayOf(700L, 2_000L, 5_000L)

        /** Lights on, off, ..., then back to normal (null): as long as the rumble. */
        val BLINK_STEPS = List(14) { it % 2 == 0 } + null
        // Both motors at full strength (255 is the most Android allows) for about 3.4 s, with
        // two short breaks so the buzz pulses and is easier to locate by ear.
        val RUMBLE_TIMINGS = longArrayOf(0L, 1_000L, 80L, 1_000L, 80L, 1_200L)
        val RUMBLE_AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255)
    }
}
