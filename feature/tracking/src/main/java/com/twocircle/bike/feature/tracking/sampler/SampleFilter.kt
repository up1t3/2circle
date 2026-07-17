package com.twocircle.bike.feature.tracking.sampler

import com.twocircle.bike.common.geo.Geo
import com.twocircle.bike.feature.tracking.model.PointSample

/**
 * Filter that decides whether a raw GPS sample deserves a slot in the persistence queue.
 *
 * GPS on a phone is noisy: jitter, multipath, "fly-away" fixes when the receiver
 * re-acquires. Storing every sample would (a) bloat the DB, (b) corrupt distance and
 * elevation aggregates, and (c) make the rendered track zigzag. We filter in three ways:
 *
 * 1. Accuracy: discard fixes worse than [MAX_ACCURACY_METERS] — they're likely wrong.
 * 2. Minimum spacing: drop samples that are essentially at the same place as the last
 *    accepted one (within [MIN_SPACING_METERS]). This kills jitter while stationary.
 * 3. Plausibility: discard "teleports" — a sample implying a speed above
 *    [MAX_PLAUSIBLE_SPEED_MPS] is almost certainly a fly-away fix.
 *
 * Pure functions for unit testing.
 */
object SampleFilter {

    /** Fixes worse than this are discarded. 50 m is generous; Strava uses ~similar. */
    const val MAX_ACCURACY_METERS = 50.0f

    /** Don't record a new point if it's within this radius of the last accepted one. */
    const val MIN_SPACING_METERS = 3.0

    /** Discard fixes implying faster-than-this speed (assumed GPS glitch). 40 m/s ≈ 144 km/h. */
    const val MAX_PLAUSIBLE_SPEED_MPS = 40.0

    /**
     * @param candidate the incoming sample.
     * @param lastAccepted the most recent sample that passed the filter (null if none).
     * @return the candidate if it should be persisted, null to drop.
     */
    fun accept(
        candidate: PointSample,
        lastAccepted: PointSample?,
    ): PointSample? {
        // 1. Accuracy gate.
        val acc = candidate.accuracyMeters
        if (acc != null && acc > MAX_ACCURACY_METERS) return null

        if (lastAccepted == null) return candidate

        // 2. Minimum spacing — kill stationary jitter.
        val dist = Geo.distanceMeters(
            lastAccepted.lat, lastAccepted.lon,
            candidate.lat, candidate.lon,
        )
        if (dist < MIN_SPACING_METERS) return null

        // 3. Plausibility — teleport detection.
        val dtMs = candidate.timestampMs - lastAccepted.timestampMs
        if (dtMs > 0) {
            val impliedSpeed = dist / (dtMs / 1000.0)
            if (impliedSpeed > MAX_PLAUSIBLE_SPEED_MPS) return null
        }

        return candidate
    }
}
