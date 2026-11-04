package com.philpf.dualsensemanager.device

import com.philpf.dualsensemanager.core.BatteryReading
import com.philpf.dualsensemanager.core.ChargeStatus
import com.philpf.dualsensemanager.core.ControllerKind
import com.philpf.dualsensemanager.core.ControllerProfile
import com.philpf.dualsensemanager.core.LedPalette
import com.philpf.dualsensemanager.core.LiveController
import com.philpf.dualsensemanager.core.Player
import com.philpf.dualsensemanager.core.Players
import com.philpf.dualsensemanager.core.Transport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Sample controllers held in memory, for screenshots of debug builds (see MainActivity).
 * Nothing is saved and no real controller is touched.
 */
class DemoSource : ControllerSource {

    private val now = System.currentTimeMillis()
    private val ana = Player("demo-ana", "Ana", 0x00E5FF)
    private val ben = Player("demo-ben", "Ben", 0xFF5200)

    private val _players = MutableStateFlow(listOf(ana, ben).associateBy { it.id })
    override val players: StateFlow<Map<String, Player>> = _players.asStateFlow()

    private val _profiles = MutableStateFlow(
        listOf(
            ControllerProfile("demo-1", 1, ControllerKind.DUALSENSE, 0x0030FF, playerId = ana.id, lastSeen = now),
            ControllerProfile("demo-2", 2, ControllerKind.DUALSENSE, 0xFF1E8C, lastSeen = now),
            ControllerProfile("demo-3", 3, ControllerKind.DUALSENSE_EDGE, 0x00FF2A, lastSeen = now - 2 * DAY_MS, lastBattery = 35),
        ).associateBy { it.id },
    )
    override val profiles: StateFlow<Map<String, ControllerProfile>> = _profiles.asStateFlow()

    private val _live = MutableStateFlow(
        listOf(
            LiveController("demo-1", 1, ControllerKind.DUALSENSE, Transport.BLUETOOTH, BatteryReading(55, ChargeStatus.DISCHARGING), true),
            LiveController("demo-2", 2, ControllerKind.DUALSENSE, Transport.USB, BatteryReading(25, ChargeStatus.CHARGING), true),
        ),
    )
    override val live: StateFlow<List<LiveController>> = _live.asStateFlow()

    private val _previews = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val previews: StateFlow<Map<String, Int>> = _previews.asStateFlow()

    override fun preview(controllerId: String, rgb: Int) {
        _previews.update { it + (controllerId to (rgb and 0xFFFFFF)) }
    }

    override fun setColor(controllerId: String, rgb: Int) {
        val playerId = _profiles.value[controllerId]?.playerId
        if (playerId != null && playerId in _players.value) {
            _players.update { it + (playerId to it.getValue(playerId).copy(color = rgb and 0xFFFFFF)) }
        } else {
            updateProfile(controllerId) { it.copy(color = rgb and 0xFFFFFF, customColor = true) }
        }
        _previews.update { it - controllerId }
    }

    override fun useAutomaticColor(controllerId: String) {
        val taken = _profiles.value.values.filter { it.id != controllerId }.map { it.color } + _players.value.values.map { it.color }
        updateProfile(controllerId) { it.copy(color = LedPalette.automaticColor(it.number, taken), customColor = false) }
    }

    override fun assignPlayer(controllerId: String, playerId: String?) {
        _profiles.update { Players.assign(it, controllerId, playerId) }
    }

    override fun newPlayer(controllerId: String): String? {
        val profile = _profiles.value[controllerId] ?: return null
        val color = profile.playerId?.let { _players.value[it]?.color } ?: profile.color
        val player = Player("demo-${_players.value.size + 1}-${System.nanoTime()}", Players.defaultName(_players.value.values.map { it.name }), color)
        _players.update { it + (player.id to player) }
        assignPlayer(controllerId, player.id)
        return player.id
    }

    override fun renamePlayer(playerId: String, name: String) {
        _players.update { players -> players[playerId]?.let { players + (playerId to it.copy(name = name)) } ?: players }
    }

    override fun deletePlayer(playerId: String) {
        _profiles.update { Players.unassign(it, playerId) }
        _players.update { it - playerId }
    }

    override fun identify(controllerId: String) = Unit

    override fun forget(controllerId: String) {
        if (_live.value.none { it.id == controllerId }) _profiles.update { it - controllerId }
    }

    private fun updateProfile(id: String, transform: (ControllerProfile) -> ControllerProfile) {
        _profiles.update { profiles -> profiles[id]?.let { profiles + (id to transform(it)) } ?: profiles }
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
