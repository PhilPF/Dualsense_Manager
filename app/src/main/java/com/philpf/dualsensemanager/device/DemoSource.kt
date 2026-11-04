package com.philpf.dualsensemanager.device

import android.content.IntentSender
import com.philpf.dualsensemanager.core.BatteryEta
import com.philpf.dualsensemanager.core.BatteryReading
import com.philpf.dualsensemanager.core.ChargeStatus
import com.philpf.dualsensemanager.core.ControllerKind
import com.philpf.dualsensemanager.core.ControllerProfile
import com.philpf.dualsensemanager.core.LedPalette
import com.philpf.dualsensemanager.core.LiveController
import com.philpf.dualsensemanager.core.Numbering
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
            LiveController(
                "demo-1", FIRST_DEVICE_ID, ControllerKind.DUALSENSE, Transport.BLUETOOTH,
                BatteryReading(55, ChargeStatus.DISCHARGING), true, BatteryEta(200, charging = false),
            ),
            LiveController(
                "demo-2", 2, ControllerKind.DUALSENSE, Transport.USB,
                BatteryReading(25, ChargeStatus.CHARGING), true, BatteryEta(110, charging = true),
            ),
        ),
    )
    override val live: StateFlow<List<LiveController>> = _live.asStateFlow()

    private val _previews = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val previews: StateFlow<Map<String, Int>> = _previews.asStateFlow()

    override fun preview(controllerId: String, rgb: Int) {
        _previews.update { it + (controllerId to (rgb and 0xFFFFFF)) }
    }

    override fun endPreview(controllerId: String) {
        _previews.update { it - controllerId }
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

    override fun newPlayer(controllerId: String, name: String, color: Int): String? {
        if (controllerId !in _profiles.value) return null
        val player = Player("demo-${System.nanoTime()}", name.ifBlank { Players.defaultName(_players.value.values.map { it.name }) }, color and 0xFFFFFF)
        _players.update { it + (player.id to player) }
        assignPlayer(controllerId, player.id)
        _previews.update { it - controllerId }
        return player.id
    }

    override fun updatePlayer(playerId: String, name: String, color: Int) {
        _players.update { players -> players[playerId]?.let { players + (playerId to it.copy(name = name, color = color and 0xFFFFFF)) } ?: players }
        _previews.value = emptyMap()
    }

    override fun reorder(order: List<String>) {
        _profiles.update { Numbering.reorder(it, order, _players.value.values.map { p -> p.color }) }
    }

    override fun numberInOrder(order: List<String>) {
        _profiles.update { Numbering.ranked(it, order, _players.value.values.map { p -> p.color }) }
    }

    override fun nudge(controllerId: String) = Unit

    override fun deletePlayer(playerId: String) {
        _profiles.update { Players.unassign(it, playerId) }
        _players.update { it - playerId }
    }

    override fun identify(controllerId: String) = Unit

    override fun canDisconnect(controllerId: String): Boolean =
        _live.value.any { it.id == controllerId && it.transport == Transport.BLUETOOTH }

    /** Pretends the controller went away. */
    override fun disconnect(controllerId: String, askUser: (IntentSender) -> Unit, onResult: (DisconnectResult) -> Unit) {
        _live.update { list -> list.filterNot { it.id == controllerId } }
        onResult(DisconnectResult.Done)
    }

    override fun forget(controllerId: String) {
        if (_live.value.none { it.id == controllerId }) _profiles.update { it - controllerId }
    }

    private fun updateProfile(id: String, transform: (ControllerProfile) -> ControllerProfile) {
        _profiles.update { profiles -> profiles[id]?.let { profiles + (id to transform(it)) } ?: profiles }
    }

    companion object {
        /** Input-device id of the first sample controller. */
        const val FIRST_DEVICE_ID = 1
        private const val DAY_MS = 86_400_000L
    }
}
