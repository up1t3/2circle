package com.twocircle.bike.data.model

import com.twocircle.bike.data.db.entity.TrackStatus

/**
 * Read projection for track list/detail UI. Maps from [com.twocircle.bike.data.db.entity.TrackEntity]
 * without exposing the full row (which includes transient fields the UI doesn't need).
 *
 * Kept in :core:data (not :core:domain) because it references the storage layer's
 * [TrackStatus]. Domain models remain storage-agnostic.
 */
data class TrackSummary(
    val id: String,
    val name: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val status: TrackStatus,
    val distanceMeters: Double,
    val movingSeconds: Long,
    val ascentMeters: Double,
    val descentMeters: Double,
    val avgSpeedMps: Double,
)
