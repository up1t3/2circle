package com.twocircle.bike.feature.tracking.sampler

import com.twocircle.bike.feature.tracking.model.PointSample
import com.twocircle.bike.feature.tracking.model.SamplerState

/**
 * Adaptive GPS sampling policy.
 *
 * Goal: balance positional accuracy against battery life. On a long tour the GPS is the
 * single biggest power consumer after the screen, so we drop to a low cadence whenever
 * the rider is stationary (rest stop, mechanical, overnight).
 *
 * Strategy:
 *  - When moving faster than [MOVEMENT_THRESHOLD_MPS], sample every [ACTIVE_INTERVAL_MS]
 *    (≈3 s, the typical cadence Strava/Komoot use at biking speeds).
 *  - When speed is below the threshold for [STATIONARY_GRACE_MS] consecutive samples,
 *    drop to [STATIONARY_INTERVAL_MS] (≈30 s) — enough to detect movement promptly
 *    without burning power while the rider is having lunch.
 *  - When stationary, a single sample above the threshold flips us straight back to
 *    the active cadence so resume is recorded with no gap.
 *
 * Pure: takes the last few samples + clock, returns the new state + the interval to
 * use for the next fix. This makes the policy unit-testable without Android.
 */
object PollingPolicy {

    /** Below this speed (m/s) we treat the rider as stationary. ≈1.4 m/s = 5 km/h walking pace. */
    const val MOVEMENT_THRESHOLD_MPS = 1.4

    /** Active cadence while moving. */
    const val ACTIVE_INTERVAL_MS = 3_000L

    /** Low-power cadence while stationary. */
    const val STATIONARY_INTERVAL_MS = 30_000L

    /** High-accuracy request interval on first fix. */
    const val ACQUIRING_INTERVAL_MS = 2_000L

    /** How long we must observe low speed before entering Stationary state. */
    const val STATIONARY_GRACE_MS = 8_000L

    /**
     * Decide the next sampler state given the recent samples.
     *
     * @param currentState what we believe right now.
     * @param recent the most recent samples (last first); up to a handful used.
     * @param nowMs current wall clock.
     */
    fun next(
        currentState: SamplerState,
        recent: List<PointSample>,
        nowMs: Long,
    ): SamplerState {
        if (recent.isEmpty()) return SamplerState.Acquiring
        val latest = recent.first()
        val moving = (latest.speedMps ?: 0.0f).toDouble() >= MOVEMENT_THRESHOLD_MPS

        // If the latest sample shows movement, jump straight to Moving regardless of
        // prior state — this is the "resume detected" branch.
        if (moving) {
            return SamplerState.Moving(
                speedMps = latest.speedMps!!.toDouble(),
                intervalMs = ACTIVE_INTERVAL_MS,
            )
        }

        // Latest is slow. Decide whether to enter / remain Stationary.
        // Count consecutive slow samples ending at the head, and their time span.
        val slowStreak = countSlowStreak(recent)
        val streakStartMs = if (slowStreak > 0) {
            recent.drop(slowStreak - 1).minOfOrNull { it.timestampMs } ?: latest.timestampMs
        } else latest.timestampMs
        val slowSpan = nowMs - streakStartMs

        return when (currentState) {
            SamplerState.Acquiring -> {
                // We have a fix but the rider isn't moving yet — go Stationary to save power.
                if (slowSpan >= STATIONARY_GRACE_MS) stationaryState(nowMs)
                else SamplerState.Acquiring
            }
            is SamplerState.Moving -> {
                // Just stopped. If we've been slow long enough, drop to Stationary.
                if (slowSpan >= STATIONARY_GRACE_MS) stationaryState(nowMs)
                else currentState // grace period: keep the active cadence briefly
            }
            is SamplerState.Stationary -> currentState.copy(sinceMs = currentState.sinceMs)
        }
    }

    /** What interval should the sampler request next, given the state? */
    fun intervalFor(state: SamplerState): Long = when (state) {
        SamplerState.Acquiring -> ACQUIRING_INTERVAL_MS
        is SamplerState.Moving -> state.intervalMs
        is SamplerState.Stationary -> state.intervalMs
    }

    private fun stationaryState(nowMs: Long) = SamplerState.Stationary(
        sinceMs = nowMs,
        intervalMs = STATIONARY_INTERVAL_MS,
    )

    private fun countSlowStreak(recent: List<PointSample>): Int {
        var n = 0
        for (s in recent) {
            if ((s.speedMps ?: 0.0f).toDouble() < MOVEMENT_THRESHOLD_MPS) n++ else break
        }
        return n
    }
}
