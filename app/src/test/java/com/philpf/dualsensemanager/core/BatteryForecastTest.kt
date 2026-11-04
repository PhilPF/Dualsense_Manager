package com.philpf.dualsensemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.floor

class BatteryForecastTest {
    private val minute = 60_000L

    /** What the driver reports for a charge [c] (in %): the band's middle, min(10 floor(c / 10) + 5, 100). */
    private fun reported(c: Double): Int = minOf(10 * floor(c / 10.0).toInt() + 5, 100)

    /** Readings once a minute from minute 0 to [minutes], of a charge c(t) = [start] + [perHour] t. */
    private fun simulate(start: Double, perHour: Double, minutes: Int, status: ChargeStatus): BatteryTrack? {
        var track: BatteryTrack? = null
        for (m in 0..minutes) {
            val c = start + perHour * m / 60.0
            track = BatteryForecast.track(track, BatteryReading(reported(c), status), m * minute)
        }
        return track
    }

    @Test
    fun drainingAtAConstantRate() {
        // 78.3 % falling 12 points an hour: crosses 70, 60 and 50 at 41.5, 91.5 and 141.5 min,
        // seen at the next readings.
        val track = simulate(78.3, -12.0, 150, ChargeStatus.DISCHARGING)!!
        assertEquals(listOf(Crossing(42 * minute, 70), Crossing(92 * minute, 60), Crossing(142 * minute, 50)), track.crossings)
        assertEquals(12.0, BatteryForecast.rate(track)!!, 1e-9)
        // Left: 48.3 % at 12 % an hour, 241.5 min; estimated 48.4 / 12 h = 242 min, shown as 240.
        assertEquals(BatteryEta(240, charging = false), BatteryForecast.eta(track, learnedRate = 0f, now = 150 * minute))
    }

    @Test
    fun chargingAtAConstantRate() {
        // 22.2 % rising 30 points an hour: crosses 30, 40 and 50 at 15.6, 35.6 and 55.6 min.
        val track = simulate(22.2, 30.0, 60, ChargeStatus.CHARGING)!!
        assertEquals(listOf(Crossing(16 * minute, 30), Crossing(36 * minute, 40), Crossing(56 * minute, 50)), track.crossings)
        // To full: 48 points at 30 an hour, 96 min, shown as 100.
        assertEquals(BatteryEta(100, charging = true), BatteryForecast.eta(track, learnedRate = 0f, now = 60 * minute))
    }

    @Test
    fun oneCrossingIsNotEnoughWithoutALearnedRate() {
        val track = simulate(78.3, -12.0, 60, ChargeStatus.DISCHARGING)!!
        assertEquals(1, track.crossings.size)
        assertNull(BatteryForecast.eta(track, learnedRate = 0f, now = 60 * minute))
    }

    @Test
    fun aLearnedRateStandsInBeforeAnyCrossing() {
        val track = BatteryForecast.track(null, BatteryReading(55, ChargeStatus.DISCHARGING), 0L)!!
        // r = 10 % an hour, D = 0.5 h in band [50, 60): charge now 50 + (10 - 5) / 2 = 52.5,
        // left 5.25 h = 315 min, shown as 320.
        assertEquals(BatteryEta(320, charging = false), BatteryForecast.eta(track, learnedRate = 10f, now = 30 * minute))
    }

    @Test
    fun stayingInABandBoundsTheRate() {
        val track = BatteryForecast.track(null, BatteryReading(55, ChargeStatus.DISCHARGING), 0L)!!
        // Learned 30 % an hour, but an hour later the charge is still in [50, 60): r <= 10 / 1.
        // Then the charge now is 50 + (10 - 10) / 2 = 50, and 50 / 10 = 5 h.
        assertEquals(BatteryEta(300, charging = false), BatteryForecast.eta(track, learnedRate = 30f, now = 60 * minute))
    }

    @Test
    fun aLearnedRateAndThisConnectionsAreAveraged() {
        val track = simulate(78.3, -12.0, 150, ChargeStatus.DISCHARGING)!!
        // Two measured steps at 12, and a learned 24 weighing as two steps: 18 % an hour.
        // Charge now 50 - 18 * 8 / 60 = 47.6; 47.6 / 18 h = 158.7 min, shown as 160.
        assertEquals(BatteryEta(160, charging = false), BatteryForecast.eta(track, learnedRate = 24f, now = 150 * minute))
    }

    @Test
    fun aReadingThatGoesBackDropsTheCrossingsItContradicts() {
        var track = BatteryForecast.track(null, BatteryReading(65, ChargeStatus.DISCHARGING), 0L)
        track = BatteryForecast.track(track, BatteryReading(55, ChargeStatus.DISCHARGING), 10 * minute)
        assertEquals(listOf(Crossing(10 * minute, 60)), track!!.crossings)
        track = BatteryForecast.track(track, BatteryReading(65, ChargeStatus.DISCHARGING), 11 * minute)
        assertEquals(emptyList<Crossing>(), track!!.crossings)
        track = BatteryForecast.track(track, BatteryReading(55, ChargeStatus.DISCHARGING), 20 * minute)
        assertEquals(listOf(Crossing(20 * minute, 60)), track!!.crossings)
    }

    @Test
    fun aNewTrackWhenChargingStartsAndNoneWhenFull() {
        val draining = simulate(78.3, -12.0, 150, ChargeStatus.DISCHARGING)
        val charging = BatteryForecast.track(draining, BatteryReading(45, ChargeStatus.CHARGING), 151 * minute)!!
        assertEquals(BatteryTrack(charging = true, since = 151 * minute, band = 40), charging)
        assertNull(BatteryForecast.track(charging, BatteryReading(100, ChargeStatus.FULL), 200 * minute))
        // A missing reading keeps the track.
        assertEquals(charging, BatteryForecast.track(charging, null, 152 * minute))
    }

    @Test
    fun eachNewStepMovesTheLearnedRate() {
        val one = BatteryTrack(charging = false, since = 0L, band = 60, crossings = listOf(Crossing(0L, 70)))
        val two = one.copy(band = 50, crossings = one.crossings + Crossing(60 * minute, 60))
        assertEquals(10f, BatteryForecast.learn(0f, one, two), 1e-6f)
        assertEquals(17.5f, BatteryForecast.learn(20f, one, two), 1e-6f)
        // No new step, or a glitch (10 points in a minute), changes nothing.
        assertEquals(20f, BatteryForecast.learn(20f, two, two), 1e-6f)
        val glitch = one.copy(band = 50, crossings = one.crossings + Crossing(1 * minute, 60))
        assertEquals(20f, BatteryForecast.learn(20f, one, glitch), 1e-6f)
    }

    @Test
    fun minutesAreRoundedAndWritten() {
        assertEquals(5, BatteryForecast.rounded(3))
        assertEquals(35, BatteryForecast.rounded(37))
        assertEquals(40, BatteryForecast.rounded(38))
        assertEquals(60, BatteryForecast.rounded(64))
        assertEquals(70, BatteryForecast.rounded(65))
        assertEquals(600, BatteryForecast.rounded(599))
        assertEquals(660, BatteryForecast.rounded(630))
        assertEquals("~3h 20m", BatteryForecast.short(200))
        assertEquals("~35m", BatteryForecast.short(35))
        assertEquals("~11h", BatteryForecast.short(660))
        assertEquals("about 3 h 20 min left", BatteryForecast.spoken(BatteryEta(200, charging = false)))
        assertEquals("full in about 40 min", BatteryForecast.spoken(BatteryEta(40, charging = true)))
    }
}
