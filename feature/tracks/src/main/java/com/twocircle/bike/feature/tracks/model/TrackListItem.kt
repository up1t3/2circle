package com.twocircle.bike.feature.tracks.model

import com.twocircle.bike.data.db.entity.TrackPointEntity
import com.twocircle.bike.data.model.TrackSummary

/**
 * Full track detail: summary + raw points.
 *
 * Used by the detail screen to render the track polyline and the export action.
 * We don't denormalise aggregates here — [TrackSummary] already has them and the
 * detail screen just adds the geometry.
 */
data class TrackDetail(
    val summary: TrackSummary,
    val points: List<TrackPointEntity>,
)

/** Convenience: a flat list-item row for the tracks list screen. */
data class TrackListItem(
    val id: String,
    val name: String,
    val startedAtMs: Long,
    val distanceMeters: Double,
    val ascentMeters: Double,
    val durationSeconds: Long,
    val status: com.twocircle.bike.data.db.entity.TrackStatus,
)

fun TrackSummary.toListItem(): TrackListItem = TrackListItem(
    id = id,
    name = name,
    startedAtMs = startedAtMs,
    distanceMeters = distanceMeters,
    ascentMeters = ascentMeters,
    durationSeconds = movingSeconds,
    status = status,
)
