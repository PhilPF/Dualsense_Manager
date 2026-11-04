package com.philpf.dualsensemanager.device

import android.content.Context
import android.content.SharedPreferences
import com.philpf.dualsensemanager.core.ControllerKind
import com.philpf.dualsensemanager.core.ControllerProfile
import com.philpf.dualsensemanager.core.LedPalette
import com.philpf.dualsensemanager.core.Player
import com.philpf.dualsensemanager.core.PlayerCodec
import com.philpf.dualsensemanager.core.Players
import com.philpf.dualsensemanager.core.ProfileCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Remembers every controller the app has seen and every player. Thread-safe. */
class ProfileStore(context: Context, name: String = "controllers") {

    private val prefs: SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val lock = Any()

    private val _profiles = MutableStateFlow(
        ProfileCodec.decode(prefs.getString(KEY_PROFILES, null)).associateBy { it.id },
    )
    val profiles: StateFlow<Map<String, ControllerProfile>> = _profiles.asStateFlow()

    private val _players = MutableStateFlow(
        PlayerCodec.decode(prefs.getString(KEY_PLAYERS, null)).associateBy { it.id },
    )
    val players: StateFlow<Map<String, Player>> = _players.asStateFlow()

    fun get(id: String): ControllerProfile? = _profiles.value[id]

    fun player(id: String): Player? = _players.value[id]

    /** The light-bar colour a controller should show: its player's, or its own. */
    fun colorOf(profile: ControllerProfile): Int =
        profile.playerId?.let { _players.value[it]?.color } ?: profile.color

    /** Colours in use by other controllers and by players, for picking automatic colours. */
    fun colorsInUse(exceptController: String? = null): List<Int> =
        _profiles.value.values.filter { it.id != exceptController }.map { it.color } +
            _players.value.values.map { it.color }

    /**
     * The profile for [id], created on first sight with the smallest free number and an
     * automatic colour that no other controller or player uses.
     */
    fun ensure(id: String, kind: ControllerKind, now: Long): ControllerProfile {
        synchronized(lock) {
            _profiles.value[id]?.let { return it }
            val number = LedPalette.nextNumber(_profiles.value.values.map { it.number })
            val profile = ControllerProfile(
                id = id,
                number = number,
                kind = kind,
                color = LedPalette.automaticColor(number, colorsInUse()),
                lastSeen = now,
            )
            writeProfiles(_profiles.value + (id to profile))
            return profile
        }
    }

    fun update(id: String, transform: (ControllerProfile) -> ControllerProfile) {
        synchronized(lock) {
            val current = _profiles.value[id] ?: return
            val next = transform(current)
            if (next != current) writeProfiles(_profiles.value + (id to next))
        }
    }

    fun forget(id: String) {
        synchronized(lock) {
            if (id in _profiles.value) writeProfiles(_profiles.value - id)
        }
    }

    fun createPlayer(name: String, color: Int): Player {
        synchronized(lock) {
            val player = Player(id = UUID.randomUUID().toString(), name = name, color = color and 0xFFFFFF)
            writePlayers(_players.value + (player.id to player))
            return player
        }
    }

    fun updatePlayer(id: String, transform: (Player) -> Player) {
        synchronized(lock) {
            val current = _players.value[id] ?: return
            val next = transform(current)
            if (next != current) writePlayers(_players.value + (id to next))
        }
    }

    fun deletePlayer(id: String) {
        synchronized(lock) {
            if (id !in _players.value) return
            writeProfiles(Players.unassign(_profiles.value, id))
            writePlayers(_players.value - id)
        }
    }

    /** Gives [playerId] (or nobody) to [controllerId]; the player leaves any other controller. */
    fun assignPlayer(controllerId: String, playerId: String?) {
        synchronized(lock) {
            if (controllerId !in _profiles.value) return
            if (playerId != null && playerId !in _players.value) return
            val next = Players.assign(_profiles.value, controllerId, playerId)
            if (next != _profiles.value) writeProfiles(next)
        }
    }

    private fun writeProfiles(map: Map<String, ControllerProfile>) {
        _profiles.value = map
        prefs.edit().putString(KEY_PROFILES, ProfileCodec.encode(map.values)).apply()
    }

    private fun writePlayers(map: Map<String, Player>) {
        _players.value = map
        prefs.edit().putString(KEY_PLAYERS, PlayerCodec.encode(map.values)).apply()
    }

    private companion object {
        const val KEY_PROFILES = "profiles"
        const val KEY_PLAYERS = "players"
    }
}
