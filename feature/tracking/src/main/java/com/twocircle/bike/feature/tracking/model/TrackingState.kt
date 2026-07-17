package com.twocircle.bike.feature.tracking.model

/**
 * Tracking session state — what the foreground notification and HUD read.
 *
 * Mirrors [TrackAggregates] plus the sampler's current mode and the active track id.
 * Kept as a flat data class so the service can build a notification from it in one read.
 */
data class TrackingState(
    val trackId: String?,
    val aggregates: TrackAggregates,
    val sampler: SamplerState,
    val isRecording: Boolean,
)

/** Convenience: a fresh state for a track that's about to start. */
fun initialTrackingState(): TrackingState = TrackingState(
    trackId = null,
    aggregates = TrackAggregates(),
    sampler = SamplerState.Acquiring,
    isRecording = false,
)
