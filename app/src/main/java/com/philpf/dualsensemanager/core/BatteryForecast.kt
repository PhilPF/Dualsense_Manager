package com.philpf.dualsensemanager.core

import kotlin.math.abs
import kotlin.math.roundToInt

/** A moment the charge was exactly [level] % (a band boundary): at [time], epoch ms. */
data class Crossing(val time: Long, val level: Int)

/**
 * One controller's battery since it connected, or since it started or stopped charging.
 * [band] is the lower end of the 10 % band the charge is in now (the level shown), [since] when
 * the track began, [crossings] the band boundaries crossed since, oldest first.
 */
data class BatteryTrack(
    val charging: Boolean,
    val since: Long,
    val band: Int,
    val crossings: List<Crossing> = emptyList(),
)

/** About [minutes] until the battery is empty, or full while [charging]; already rounded for display. */
data class BatteryEta(val minutes: Int, val charging: Boolean)

/**
 * How long a DualSense's battery will last, or take to charge, from its coarse readings.
 *
 * The controller reports its charge only as the 10 % band [b, b + 10) it lies in (see
 * [BatteryText]). The moments the band changes carry the information: when the reading drops
 * from the band starting at b to the one below, the charge equals b at that moment (to within
 * the polling delay). Modelling the charge as linear in time, c(t) = c0 - r t, two or more such
 * crossings (t_i, b_i) give the rate r by least squares, and the last one fixes the charge:
 * c(now) = b_n - r (now - t_n). While charging, the same holds with rising bands.
 *
 * Before two crossings are seen, a rate learned on earlier connections stands in. The charge at
 * the first reading is then only known to lie in its band; if it has not left the band after a
 * time D, then under a uniform prior on the band the charge now has mean b + (10 - r D) / 2
 * (b + (10 + r D) / 2 while charging). Staying in a band also bounds the rate: since the last
 * crossing (or the first reading) the charge has moved by at most 10 points in time D, so
 * r <= 10 / D. That keeps the estimate consistent when use slows down, as an idle controller
 * drains more slowly.
 */
object BatteryForecast {
    private const val BAND = 10.0
    private const val HOUR_MS = 3_600_000.0

    /** Crossings kept per track: the rate follows recent use. */
    const val MAX_CROSSINGS = 6

    /** How many measured steps a learned rate counts for, against this connection's own. */
    private const val PRIOR_WEIGHT = 2.0

    /** Weight of each newly measured step in the learned rate. */
    private const val LEARNING = 0.25f

    /** Rates outside these bounds (% per hour) are glitches, not measurements. */
    private const val MIN_RATE = 1.0
    private const val MAX_RATE = 300.0

    /**
     * The track after [reading] at [now] (epoch ms): a new one when charging starts or stops,
     * none while the battery is full, not charging or unknown.
     */
    fun track(previous: BatteryTrack?, reading: BatteryReading?, now: Long): BatteryTrack? {
        val percent = reading?.percent ?: return previous
        val charging = when (reading.status) {
            ChargeStatus.DISCHARGING -> false
            ChargeStatus.CHARGING -> true
            else -> return null
        }
        val band = BatteryText.shown(percent)
        if (previous == null || previous.charging != charging) return BatteryTrack(charging, now, band)
        if (band == previous.band) return previous
        val forward = if (charging) band > previous.band else band < previous.band
        if (!forward) {
            // The reading went back: the charge sags under load and recovers. The crossings it
            // contradicts came early; drop them, and the next one counts.
            val kept = previous.crossings.filter { if (charging) it.level <= band else it.level > band }
            return previous.copy(band = band, crossings = kept)
        }
        // The boundary crossed last: the new band's top when falling, its bottom when rising.
        val level = if (charging) band else band + BAND.toInt()
        return previous.copy(band = band, crossings = (previous.crossings + Crossing(now, level)).takeLast(MAX_CROSSINGS))
    }

    /**
     * This track's own rate in % per hour: the least-squares slope of its crossings, or null
     * with fewer than two.
     */
    fun rate(track: BatteryTrack): Double? {
        val points = track.crossings
        if (points.size < 2) return null
        val t0 = points.first().time
        val ts = points.map { (it.time - t0) / HOUR_MS }
        val ls = points.map { it.level.toDouble() }
        val tMean = ts.average()
        val lMean = ls.average()
        var covariance = 0.0
        var variance = 0.0
        for (i in points.indices) {
            covariance += (ts[i] - tMean) * (ls[i] - lMean)
            variance += (ts[i] - tMean) * (ts[i] - tMean)
        }
        if (variance <= 0.0) return null
        val rate = abs(covariance / variance)
        return rate.takeIf { it > 0.0 }
    }

    /**
     * About how long until the battery is empty (or full while charging), from [track] at [now]
     * and [learnedRate], the rate learned on earlier connections in % per hour (0 if none).
     * Null when there is too little to go on, or nothing left to charge.
     */
    fun eta(track: BatteryTrack?, learnedRate: Float, now: Long): BatteryEta? {
        track ?: return null
        val own = rate(track)
        val steps = track.crossings.size - 1
        var r = when {
            own != null && learnedRate > 0f -> (steps * own + PRIOR_WEIGHT * learnedRate) / (steps + PRIOR_WEIGHT)
            own != null -> own
            learnedRate > 0f -> learnedRate.toDouble()
            else -> return null
        }
        val last = track.crossings.lastOrNull()
        val hours = (now - (last?.time ?: track.since)).coerceAtLeast(0L) / HOUR_MS
        if (hours > 0.0) r = minOf(r, BAND / hours)
        if (r <= 0.0) return null
        val charge = when {
            last != null && track.charging -> last.level + r * hours
            last != null -> last.level - r * hours
            track.charging -> track.band + (BAND + r * hours) / 2.0
            else -> track.band + (BAND - r * hours) / 2.0
        }
        val left = if (track.charging) 100.0 - charge else charge
        if (left <= 0.0) return null
        return BatteryEta(rounded((left / r * 60.0).roundToInt()), track.charging)
    }

    /**
     * The learned rate after the track moved from [before] to [after]: each newly measured step
     * (two crossings in a row) moves it a quarter of the way to that step's rate.
     */
    fun learn(learned: Float, before: BatteryTrack?, after: BatteryTrack?): Float {
        if (before == null || after == null || before.charging != after.charging) return learned
        val points = after.crossings
        if (points.size < 2 || points.last() == before.crossings.lastOrNull()) return learned
        val (a, b) = points[points.size - 2] to points.last()
        val hours = (b.time - a.time) / HOUR_MS
        if (hours <= 0.0) return learned
        val step = (abs(b.level - a.level) / hours).toFloat()
        if (step < MIN_RATE || step > MAX_RATE) return learned
        return if (learned <= 0f) step else learned + LEARNING * (step - learned)
    }

    /** Minutes rounded for display: to 5 below an hour, to 10 below ten hours, then to hours. */
    fun rounded(minutes: Int): Int = when {
        minutes < 60 -> maxOf(5, (minutes + 2) / 5 * 5)
        minutes < 600 -> (minutes + 5) / 10 * 10
        else -> (minutes + 30) / 60 * 60
    }

    /** "~3h 20m", "~35m", "~11h". */
    fun short(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "~${m}m"
            m == 0 -> "~${h}h"
            else -> "~${h}h ${m}m"
        }
    }

    /** "about 3 h 20 min left", "full in about 40 min": for screen readers. */
    fun spoken(eta: BatteryEta): String {
        val h = eta.minutes / 60
        val m = eta.minutes % 60
        val time = listOfNotNull(h.takeIf { it > 0 }?.let { "$it h" }, m.takeIf { it > 0 }?.let { "$it min" }).joinToString(" ")
        return if (eta.charging) "full in about $time" else "about $time left"
    }
}
