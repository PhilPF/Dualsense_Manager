package com.philpf.dualsensemanager.device

import android.content.Context
import android.content.SharedPreferences
import com.philpf.dualsensemanager.core.ControllerKind
import com.philpf.dualsensemanager.core.ControllerProfile
import com.philpf.dualsensemanager.core.LedPalette
import com.philpf.dualsensemanager.core.ProfileCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remembers every controller the app has seen: its number, nickname and colour. Thread-safe. */
class ProfileStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("controllers", Context.MODE_PRIVATE)
    private val lock = Any()

    private val _profiles = MutableStateFlow(
        ProfileCodec.decode(prefs.getString(KEY_PROFILES, null)).associateBy { it.id },
    )
    val profiles: StateFlow<Map<String, ControllerProfile>> = _profiles.asStateFlow()

    fun get(id: String): ControllerProfile? = _profiles.value[id]

    /**
     * The profile for [id], created on first sight with the smallest free number and an
     * automatic colour that no other remembered controller uses.
     */
    fun ensure(id: String, kind: ControllerKind, now: Long): ControllerProfile {
        synchronized(lock) {
            _profiles.value[id]?.let { return it }
            val all = _profiles.value.values
            val number = LedPalette.nextNumber(all.map { it.number })
            val profile = ControllerProfile(
                id = id,
                number = number,
                kind = kind,
                color = LedPalette.automaticColor(number, all.map { it.color }),
                lastSeen = now,
            )
            write(_profiles.value + (id to profile))
            return profile
        }
    }

    fun update(id: String, transform: (ControllerProfile) -> ControllerProfile) {
        synchronized(lock) {
            val current = _profiles.value[id] ?: return
            val next = transform(current)
            if (next != current) write(_profiles.value + (id to next))
        }
    }

    fun forget(id: String) {
        synchronized(lock) {
            if (id in _profiles.value) write(_profiles.value - id)
        }
    }

    private fun write(map: Map<String, ControllerProfile>) {
        _profiles.value = map
        prefs.edit().putString(KEY_PROFILES, ProfileCodec.encode(map.values)).apply()
    }

    private companion object {
        const val KEY_PROFILES = "profiles"
    }
}
