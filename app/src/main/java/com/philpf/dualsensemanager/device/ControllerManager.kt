package com.philpf.dualsensemanager.device

import android.content.Context
import android.hardware.BatteryState
import android.hardware.input.InputManager
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import com.philpf.dualsensemanager.core.BatteryReading
import com.philpf.dualsensemanager.core.BatteryRules
import com.philpf.dualsensemanager.core.ChargeStatus
import com.philpf.dualsensemanager.core.ControllerKind
import com.philpf.dualsensemanager.core.LedPalette
import com.philpf.dualsensemanager.core.LiveController
import com.philpf.dualsensemanager.core.TransportRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Watches for DualSense controllers, sets their light bars and reads their batteries.
 *
 * Light bars are set through Android's per-device [LightsManager] (Android 12+). A light
 * session stays open for as long as the controller is connected, because Android turns the
 * light off when the session closes, including when this app's process dies. That is why
 * the app keeps a small foreground service running while controllers are connected.
 *
 * All device work happens on one background thread; results are published as [StateFlow]s.
 */
class ControllerManager(
    context: Context,
    private val store: ProfileStore,
    /** Called on the main thread when the first controller connects. */
    private val onFirstControllerConnected: () -> Unit,
) {
    private val inputManager: InputManager = context.getSystemService(InputManager::class.java)
    private val usbManager: UsbManager? = context.getSystemService(UsbManager::class.java)

    private val thread = HandlerThread("controller-manager").apply { start() }
    private val handler = Handler(thread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _live = MutableStateFlow<List<LiveController>>(emptyList())

    /** Controllers connected right now. */
    val live: StateFlow<List<LiveController>> = _live.asStateFlow()

    private val _previews = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Colours being tried out in the colour picker, by controller id. Not saved yet. */
    val previews: StateFlow<Map<String, Int>> = _previews.asStateFlow()

    /** A connected controller and the light session that holds its colour. */
    private class Attached(val deviceId: Int, val id: String) {
        var session: LightsManager.LightsSession? = null
        var shownColor: Int? = null
    }

    // Only touched on [thread].
    private val attached = HashMap<Int, Attached>()
    private val pendingApply = HashSet<String>()
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

    /** Shows [rgb] on the controller without saving it, e.g. while a colour slider is dragged. */
    fun preview(id: String, rgb: Int) {
        _previews.update { it + (id to (rgb and 0xFFFFFF)) }
        scheduleApply(id)
    }

    /** Ends a preview without saving, going back to the saved colour. */
    fun endPreview(id: String) {
        _previews.update { it - id }
        scheduleApply(id)
    }

    fun setColor(id: String, rgb: Int) {
        store.update(id) { it.copy(color = rgb and 0xFFFFFF, customColor = true) }
        _previews.update { it - id }
        scheduleApply(id)
    }

    /** Goes back to the automatic colour: the controller's palette slot, or a free colour. */
    fun useAutomaticColor(id: String) {
        val others = store.profiles.value.values.filter { it.id != id }.map { it.color }
        store.update(id) { it.copy(color = LedPalette.automaticColor(it.number, others), customColor = false) }
        _previews.update { it - id }
        scheduleApply(id)
    }

    fun setNickname(id: String, nickname: String) {
        store.update(id) { it.copy(nickname = nickname) }
    }

    /** Forgets a controller that is not connected; its number and colour become free again. */
    fun forget(id: String) {
        if (_live.value.none { it.id == id }) store.forget(id)
    }

    // ---- Everything below runs on [thread]. ----

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
            } else if (existing.shownColor == null) {
                // The kernel registers the light bar just after the input device; try again.
                applyLight(existing)
            }
        }
        publish()
        if (wasEmpty && added) mainHandler.post(onFirstControllerConnected)
    }

    /** Re-checks shortly after a connection: the light bar and the first battery report arrive late. */
    private fun followUp(deviceId: Int) {
        for (delayMs in FOLLOW_UP_DELAYS_MS) {
            handler.postDelayed({
                val a = attached[deviceId] ?: return@postDelayed
                if (a.shownColor == null) applyLight(a)
                publish()
            }, delayMs)
        }
    }

    private fun applyLight(a: Attached) {
        val color = _previews.value[a.id] ?: store.get(a.id)?.color ?: return
        val device = inputManager.getInputDevice(a.deviceId) ?: return
        val lightsManager = device.lightsManager
        val bars = rgbLights(lightsManager)
        if (bars.isEmpty()) return
        val state = LightState.Builder().setColor(OPAQUE or (color and 0xFFFFFF)).build()
        val request = LightsRequest.Builder().apply { bars.forEach { addLight(it, state) } }.build()
        try {
            val session = a.session ?: lightsManager.openSession().also { a.session = it }
            session.requestLights(request)
            a.shownColor = color
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not set the light bar of ${device.name}", e)
        }
    }

    private fun rgbLights(lightsManager: LightsManager) = try {
        lightsManager.lights.filter { it.hasRgbControl() }
    } catch (e: RuntimeException) {
        emptyList()
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
            LiveController(
                id = a.id,
                deviceId = a.deviceId,
                kind = kind,
                transport = TransportRules.transport(device.name, usbCount),
                battery = battery,
                lightBar = a.shownColor != null || rgbLights(device.lightsManager).isNotEmpty(),
            )
        }
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
        const val POLL_VISIBLE_MS = 5_000L
        const val POLL_BACKGROUND_MS = 60_000L
        const val APPLY_COALESCE_MS = 16L
        const val SIGHTING_INTERVAL_MS = 5 * 60_000L
        val FOLLOW_UP_DELAYS_MS = longArrayOf(700L, 2_000L, 5_000L)
    }
}
