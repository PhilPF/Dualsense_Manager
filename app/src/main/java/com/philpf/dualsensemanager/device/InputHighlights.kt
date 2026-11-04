package com.philpf.dualsensemanager.device

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.philpf.dualsensemanager.core.ControllerKind
import com.philpf.dualsensemanager.core.ControllerPart
import com.philpf.dualsensemanager.core.InputMath
import com.philpf.dualsensemanager.core.Sticks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot

/** What the controllers' buttons and sticks are doing, by input-device id. */
interface ControllerInput {
    /** Buttons, triggers, d-pad, touchpad and sticks pushed past half way, held right now. */
    val pressed: StateFlow<Map<Int, Set<ControllerPart>>>

    /** How many times something was newly pressed on each controller, so each press can show. */
    val presses: StateFlow<Map<Int, Int>>

    /** Where each controller's sticks are. */
    val sticks: StateFlow<Map<Int, Sticks>>
}

/**
 * The DualSenses' input while the app is open, fed by the activity with every key and motion
 * event. Use on the main thread.
 */
class InputHighlights : ControllerInput {

    private val keys = HashMap<Int, MutableSet<ControllerPart>>()
    private val axes = HashMap<Int, Set<ControllerPart>>()
    private val touched = HashSet<Int>()
    private val handler = Handler(Looper.getMainLooper())

    // Handler tokens are matched by identity, so keep one object per device.
    private val tokens = HashMap<Int, Any>()

    private val _pressed = MutableStateFlow<Map<Int, Set<ControllerPart>>>(emptyMap())
    override val pressed: StateFlow<Map<Int, Set<ControllerPart>>> = _pressed.asStateFlow()

    private val _presses = MutableStateFlow<Map<Int, Int>>(emptyMap())
    override val presses: StateFlow<Map<Int, Int>> = _presses.asStateFlow()

    private val _sticks = MutableStateFlow<Map<Int, Sticks>>(emptyMap())
    override val sticks: StateFlow<Map<Int, Sticks>> = _sticks.asStateFlow()

    /** Returns true when the event came from a DualSense and was used up. */
    fun onKey(event: KeyEvent): Boolean {
        if (!isDualSense(event.device)) return false
        val part = partFor(event.keyCode)
        if (part != null) {
            val held = keys.getOrPut(event.deviceId) { HashSet() }
            when (event.action) {
                KeyEvent.ACTION_DOWN -> held += part
                KeyEvent.ACTION_UP -> held -= part
            }
            publish()
        }
        // Keep controller buttons from acting on the app (Circle would otherwise mean "back").
        return true
    }

    /** Sticks, triggers and the d-pad hat. Returns true when the event was used up. */
    fun onMotion(event: MotionEvent): Boolean {
        if (!isDualSense(event.device)) return false
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            axes[event.deviceId] = partsFor(event)
            val (lx, ly) = InputMath.deadZone(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y))
            val (rx, ry) = InputMath.deadZone(event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RZ))
            val now = Sticks(lx, ly, rx, ry)
            if (_sticks.value[event.deviceId] != now) _sticks.value = _sticks.value + (event.deviceId to now)
            publish()
            return true
        }
        noteTouchpad(event)
        return false
    }

    /** Touchpad activity arrives as pointer events; it lights the touchpad briefly. */
    fun noteTouchpad(event: MotionEvent) {
        val device = event.device ?: return
        if (!isDualSense(device)) return
        if (!event.isFromSource(InputDevice.SOURCE_TOUCHPAD) && !event.isFromSource(InputDevice.SOURCE_MOUSE)) return
        val id = event.deviceId
        if (touched.add(id)) publish()
        val token = tokens.getOrPut(id) { Any() }
        handler.removeCallbacksAndMessages(token)
        handler.postAtTime({
            touched.remove(id)
            publish()
        }, token, SystemClock.uptimeMillis() + TOUCH_LINGER_MS)
    }

    private fun publish() {
        val ids = keys.keys + axes.keys + touched
        val now = ids.associateWith { id ->
            buildSet {
                keys[id]?.let(::addAll)
                axes[id]?.let(::addAll)
                if (id in touched) add(ControllerPart.TOUCHPAD)
            }
        }.filterValues { it.isNotEmpty() }
        val fresh = InputMath.newlyPressed(_pressed.value, now)
        if (fresh.isNotEmpty()) {
            _presses.value = _presses.value.toMutableMap().apply { for (id in fresh) this[id] = (this[id] ?: 0) + 1 }
        }
        _pressed.value = now
    }

    private fun isDualSense(device: InputDevice?): Boolean =
        device != null && ControllerKind.fromIds(device.vendorId, device.productId) != null

    private fun partFor(keyCode: Int): ControllerPart? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> ControllerPart.CROSS
        KeyEvent.KEYCODE_BUTTON_B -> ControllerPart.CIRCLE
        KeyEvent.KEYCODE_BUTTON_X -> ControllerPart.SQUARE
        KeyEvent.KEYCODE_BUTTON_Y -> ControllerPart.TRIANGLE
        KeyEvent.KEYCODE_BUTTON_L1 -> ControllerPart.L1
        KeyEvent.KEYCODE_BUTTON_R1 -> ControllerPart.R1
        KeyEvent.KEYCODE_BUTTON_L2 -> ControllerPart.L2
        KeyEvent.KEYCODE_BUTTON_R2 -> ControllerPart.R2
        KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerPart.L3
        KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerPart.R3
        KeyEvent.KEYCODE_BUTTON_SELECT -> ControllerPart.CREATE
        KeyEvent.KEYCODE_BUTTON_START -> ControllerPart.OPTIONS
        KeyEvent.KEYCODE_BUTTON_MODE -> ControllerPart.PS
        KeyEvent.KEYCODE_DPAD_UP -> ControllerPart.DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> ControllerPart.DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> ControllerPart.DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerPart.DPAD_RIGHT
        else -> null
    }

    private fun partsFor(event: MotionEvent): Set<ControllerPart> = buildSet {
        fun axis(a: Int) = event.getAxisValue(a)
        if (hypot(axis(MotionEvent.AXIS_X), axis(MotionEvent.AXIS_Y)) > STICK_THRESHOLD) add(ControllerPart.LEFT_STICK)
        if (hypot(axis(MotionEvent.AXIS_Z), axis(MotionEvent.AXIS_RZ)) > STICK_THRESHOLD) add(ControllerPart.RIGHT_STICK)
        val hatX = axis(MotionEvent.AXIS_HAT_X)
        val hatY = axis(MotionEvent.AXIS_HAT_Y)
        if (hatX < -0.5f) add(ControllerPart.DPAD_LEFT)
        if (hatX > 0.5f) add(ControllerPart.DPAD_RIGHT)
        if (hatY < -0.5f) add(ControllerPart.DPAD_UP)
        if (hatY > 0.5f) add(ControllerPart.DPAD_DOWN)
        if (maxOf(axis(MotionEvent.AXIS_LTRIGGER), axis(MotionEvent.AXIS_BRAKE)) > TRIGGER_THRESHOLD) add(ControllerPart.L2)
        if (maxOf(axis(MotionEvent.AXIS_RTRIGGER), axis(MotionEvent.AXIS_GAS)) > TRIGGER_THRESHOLD) add(ControllerPart.R2)
    }

    private companion object {
        const val STICK_THRESHOLD = 0.5f
        const val TRIGGER_THRESHOLD = 0.3f
        const val TOUCH_LINGER_MS = 350L
    }
}

/**
 * Debug screenshots: [parts] held and [sticks] tilted on one sample controller, pressed again
 * every [every] ms so the press waves keep running.
 */
class DemoInput(deviceId: Int, parts: Set<ControllerPart>, sticks: Sticks?, every: Long = 1_400L) : ControllerInput {
    override val pressed: StateFlow<Map<Int, Set<ControllerPart>>> = MutableStateFlow(mapOf(deviceId to parts))
    private val _presses = MutableStateFlow<Map<Int, Int>>(emptyMap())
    override val presses: StateFlow<Map<Int, Int>> = _presses.asStateFlow()
    override val sticks: StateFlow<Map<Int, Sticks>> =
        MutableStateFlow(if (sticks != null) mapOf(deviceId to sticks) else emptyMap())

    init {
        if (parts.isNotEmpty()) {
            val handler = Handler(Looper.getMainLooper())
            var count = 0
            val tick = object : Runnable {
                override fun run() {
                    count++
                    _presses.value = mapOf(deviceId to count)
                    handler.postDelayed(this, every)
                }
            }
            handler.postDelayed(tick, 300L)
        }
    }
}
