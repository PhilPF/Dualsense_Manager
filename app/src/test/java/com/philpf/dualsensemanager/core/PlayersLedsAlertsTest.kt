package com.philpf.dualsensemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayersLedsAlertsTest {

    private fun profile(id: String, number: Int, playerId: String? = null) =
        ControllerProfile(id, number, ControllerKind.DUALSENSE, 0x0030FF, playerId = playerId)

    @Test
    fun defaultPlayerNamesFillGaps() {
        assertEquals("Player 1", Players.defaultName(emptyList()))
        assertEquals("Player 3", Players.defaultName(listOf("Player 1", "Ana", "Player 2")))
        assertEquals("Player 2", Players.defaultName(listOf("Player 1", "Player 3")))
        assertEquals("Player 1", Players.defaultName(listOf("Player one", "player 1x")))
    }

    @Test
    fun aPlayerUsesOneControllerAtATime() {
        val profiles = mapOf("a" to profile("a", 1, "ana"), "b" to profile("b", 2), "c" to profile("c", 3, "ben"))
        val moved = Players.assign(profiles, "b", "ana")
        assertNull(moved.getValue("a").playerId)
        assertEquals("ana", moved.getValue("b").playerId)
        assertEquals("ben", moved.getValue("c").playerId)

        val cleared = Players.assign(moved, "c", null)
        assertNull(cleared.getValue("c").playerId)
        assertEquals("ana", cleared.getValue("b").playerId)

        val removed = Players.unassign(profiles, "ana")
        assertNull(removed.getValue("a").playerId)
        assertEquals("ben", removed.getValue("c").playerId)
    }

    @Test
    fun playerLedPatternsMatchThePs5() {
        assertEquals(setOf(3), PlayerLeds.pattern(1))
        assertEquals(setOf(2, 4), PlayerLeds.pattern(2))
        assertEquals(setOf(1, 3, 5), PlayerLeds.pattern(3))
        assertEquals(setOf(1, 2, 4, 5), PlayerLeds.pattern(4))
        assertEquals(setOf(1, 2, 3, 4, 5), PlayerLeds.pattern(5))
        assertEquals(setOf(3), PlayerLeds.pattern(6))
        // Every pattern is symmetric, so it reads the same whichever end the driver counts from.
        for (n in 1..5) assertEquals(PlayerLeds.pattern(n), PlayerLeds.pattern(n).map { 6 - it }.toSet())
    }

    @Test
    fun playerLedPositionsFromLightNames() {
        assertEquals(1, PlayerLeds.position("input12:white:player-1"))
        assertEquals(5, PlayerLeds.position("player5"))
        assertNull(PlayerLeds.position("input12:white:player-6"))
        assertNull(PlayerLeds.position("input12:rgb:indicator"))
        assertNull(PlayerLeds.position("input12:white:micmute"))
    }

    private fun run(vararg readings: BatteryReading?): List<BatteryAlert?> {
        var state = AlertState()
        return readings.map { r ->
            val (next, alert) = BatteryAlerts.step(state, r)
            state = next
            alert
        }
    }

    private fun discharging(p: Int) = BatteryReading(p, ChargeStatus.DISCHARGING)
    private fun charging(p: Int) = BatteryReading(p, ChargeStatus.CHARGING)
    private val full = BatteryReading(100, ChargeStatus.FULL)

    @Test
    fun lowThenCriticalOncePerDischarge() {
        assertEquals(
            listOf(null, BatteryAlert.LOW, null, null, BatteryAlert.CRITICAL, null),
            run(discharging(25), discharging(15), discharging(25), discharging(15), discharging(5), discharging(5)),
        )
    }

    @Test
    fun chargingResetsLowAlerts() {
        assertEquals(
            listOf(BatteryAlert.LOW, null, BatteryAlert.LOW),
            run(discharging(15), charging(15), discharging(15)),
        )
    }

    @Test
    fun fullOnlyAfterChargingAndOnceUntilUnplugged() {
        assertEquals(listOf(null, null), run(full, full))
        assertEquals(
            listOf(null, BatteryAlert.FULL, null, null, null, null, BatteryAlert.FULL),
            run(charging(95), full, charging(95), full, discharging(95), charging(95), full),
        )
    }

    @Test
    fun readingsWithoutALevelChangeNothing() {
        assertEquals(
            listOf(BatteryAlert.LOW, null, null, null),
            run(discharging(15), BatteryReading(null, ChargeStatus.UNKNOWN), null, discharging(15)),
        )
    }

    @Test
    fun firstReadingAlreadyCriticalSkipsLow() {
        assertEquals(listOf(BatteryAlert.CRITICAL, null), run(discharging(5), discharging(15)))
    }
}
