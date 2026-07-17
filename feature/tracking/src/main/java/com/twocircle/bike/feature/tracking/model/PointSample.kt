package com.twocircle.bike.feature.tracking.model

/**
 * One normalised GPS sample as it enters the tracking pipeline.
 *
 * This is the internal type that flows through: FusedLocationProvider → sampler →
 * bounded queue → batched Room insert. It is deliberately decoupled from the Android
 * Location type so the pipeline's pure-Kotlin logic (filtering, deduplication,
 * spacing) is unit-testable without Robolectric.
 *
 * [seq] is assigned by the pipeline at the moment of persistence, not by the sampler —
 * samples may be dropped by the filter before they reach the queue, and seq must
 * reflect what actually hit disk (the crash-recovery contract).
 *
 * [accuracyMeters] is the GPS-reported horizontal accuracy; null when unknown. The
 * filter uses it to discard samples so bad that they'd corrupt aggregates.
 */
data class PointSample(
    val lat: Double,
    val lon: Double,
    /** Metres above sea level; null when the fix has no altitude. */
    val ele: Double?,
    val accuracyMeters: Float?,
    val speedMps: Float?,
    val bearingDeg: Float?,
    /** Epoch milliseconds when the fix was received. */
    val timestampMs: Long,
) {
    init {
        require(lat in -90.0..90.0) { "lat $lat out of range" }
        require(lon in -180.0..180.0) { "lon $lon out of range" }
        require(timestampMs >= 0L) { "timestamp must be non-negative" }
    }
}

/**
 * Sampler state — what we are doing right now.
 *
 * Drives the adaptive polling strategy: when the rider is moving we sample frequently
 * (high accuracy, ~2-4 s); when stationary (rest stop) we drop to a low-power cadence
 * to save battery. The state is itself pure data — the policy that produces it lives
 * in [com.twocircle.bike.feature.tracking.sampler.PollingPolicy].
 */
sealed interface SamplerState {
    /** Looking for first fix. */
    data object Acquiring : SamplerState
    /** Rider is moving; sample at the active cadence. */
    data class Moving(val speedMps: Double, val intervalMs: Long) : SamplerState
    /** Rider appears stationary; sample at a low cadence to detect resume. */
    data class Stationary(val sinceMs: Long, val intervalMs: Long) : SamplerState
}
