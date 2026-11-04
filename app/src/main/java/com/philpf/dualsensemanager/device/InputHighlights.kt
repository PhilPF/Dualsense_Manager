package com.philpf.dualsensemanager.device

import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.philpf.dualsensemanager.core.ControllerKind
import com.philpf.dualsensemanager.core.ControllerPart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot

/**
 * What is being pressed or moved on each DualSense while the app is open, by input-device id,
 * so the screen can light it up and show which controller is which. Fed by the activity with
 * every key and motion event; use on the main thread.
 */
class InputHighlights {

    private val keys = HashMap<Int, MutableSet<ControllerPart>>()
    private val axes = HashMap<Int, Set<ControllerPart>>()
    private val touched = HashSet<Int>()
    private val handler = Handler(Looper.getMainLooper())

    // Handler tokens are matched by identity, so keep one object per device.
    private val tokens = HashMap<Int, Any>()

    private val _pressed = MutableStateFlow<Map<Int, Set<ControllerPart>>>(emptyMap())
    val pressed: StateFlow<Map<Int, Set<ControllerPart>>> = _pressed.asStateFlow()

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
        }, token, android.os.SystemClock.uptimeMillis() + TOUCH_LINGER_MS)
    }

    private fun publish() {
        val ids = keys.keys + axes.keys + touched
        _pressed.value = ids.associateWith { id ->
            buildSet {
                keys[id]?.let(::addAll)
                axes[id]?.let(::addAll)
                if (id in touched) add(ControllerPart.TOUCHPAD)
            }
        }.filterValues { it.isNotEmpty() }
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
