package com.twocircle.bike.feature.tracking.model

/**
 * Incremental ride aggregates — the live values shown in the foreground notification
 * and the on-bike telemetry HUD.
 *
 * Updated sample-by-sample rather than recomputed from the whole track, because the
 * full track can grow to tens of thousands of points over a long tour and recomputing
 * every tick would burn CPU and battery.
 *
 * All fields are doubles/longs so the type is trivially serialisable for state
 * preservation across configuration changes.
 */
data class TrackAggregates(
    val distanceMeters: Double = 0.0,
    val movingSeconds: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val ascentMeters: Double = 0.0,
    val descentMeters: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val avgSpeedMps: Double = 0.0,
    val startedAtMs: Long = 0L,
    val lastAcceptedAtMs: Long = 0L,
    val pointCount: Int = 0,
)

/**
 * Fold a newly accepted sample into the aggregates.
 *
 * Pure: given the current aggregates + the previously-accepted sample + the new sample,
 * produces the updated aggregates. No Android, no I/O — fully unit-testable.
 *
 * Edge cases handled:
 *  - First sample (no prior): just record timestamps and point count.
 *  - Missing elevation: ascent/descent unchanged for this step.
 *  - Backward clock jump (rare, but happens on GPS roll-over): clamp to no-op.
 */
fun TrackAggregates.withAcceptedSample(
    previous: PointSample?,
    new: PointSample,
    nowMs: Long,
): TrackAggregates {
    if (previous == null) {
        return copy(
            startedAtMs = new.timestampMs,
            lastAcceptedAtMs = new.timestampMs,
            pointCount = 1,
            maxSpeedMps = (new.speedMps?.toDouble() ?: 0.0).coerceAtLeast(0.0),
            elapsedSeconds = ((nowMs - new.timestampMs) / 1000L).coerceAtLeast(0L),
        )
    }
    val segmentDistance = haversineMeters(previous, new)
    val dtSec = ((new.timestampMs - previous.timestampMs) / 1000.0).coerceAtLeast(0.0)

    // Moving time accumulates only if speed is non-trivial; this matches the rider's
    // intuition that stationary time at a rest stop isn't "riding time".
    val isMoving = (new.speedMps ?: 0.0f).toDouble() >= 1.0
    val addedMovingSec = if (isMoving) dtSec.toLong() else 0L

    val (ascent, descent) = if (previous.ele != null && new.ele != null) {
        val dEle = new.ele - previous.ele
        if (dEle > 0) ascentMeters + dEle to descentMeters
        else ascentMeters to descentMeters + (-dEle)
    } else ascentMeters to descentMeters

    val newMaxSpeed = maxOf(maxSpeedMps, new.speedMps?.toDouble() ?: 0.0)
    val totalDist = distanceMeters + segmentDistance
    val totalMoving = movingSeconds + addedMovingSec
    val avg = if (totalMoving > 0) totalDist / totalMoving else 0.0

    return copy(
        distanceMeters = totalDist,
        movingSeconds = totalMoving,
        elapsedSeconds = ((nowMs - startedAtMs) / 1000L).coerceAtLeast(0L),
        ascentMeters = ascent,
        descentMeters = descent,
        maxSpeedMps = newMaxSpeed,
        avgSpeedMps = avg,
        lastAcceptedAtMs = new.timestampMs,
        pointCount = pointCount + 1,
    )
}

private fun haversineMeters(a: PointSample, b: PointSample): Double {
    val r = 6_371_008.8
    val p1 = Math.toRadians(a.lat)
    val p2 = Math.toRadians(b.lat)
    val dp = Math.toRadians(b.lat - a.lat)
    val dl = Math.toRadians(b.lon - a.lon)
    val h = Math.sin(dp / 2).let { it * it } +
        Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2).let { it * it }
    return 2 * r * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}
