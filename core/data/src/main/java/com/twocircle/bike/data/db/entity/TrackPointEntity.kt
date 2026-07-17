package com.twocircle.bike.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One GPS sample in a track. The hottest write path in the app — the tracking service
 * inserts these at 1-4 Hz for hours on end.
 *
 * Schema is deliberately flat (no embedded objects, no JSON columns) so each insert is a
 * single cheap row operation. WAL mode + batched inserts keep this off the render thread.
 *
 * [seq] is a per-track monotonic counter. It exists so the live ETA engine can recover
 * ordering after a concurrent batch insert without relying on the autoincrement [rowId]
 * (which may interleave across tracks on a busy writer).
 *
 * [accuracyMeters] and [speedMps] come straight from FusedLocationProvider; the tracking
 * service does no smoothing here — that's the post-ride analyser's job.
 */
@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("trackId"),
        Index(value = ["trackId", "seq"], unique = true),
    ],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0L,
    val trackId: String,
    val seq: Long,
    val lat: Double,
    val lon: Double,
    /** Metres above sea level; null when DEM lookup hasn't run yet. */
    val ele: Double? = null,
    val accuracyMeters: Float? = null,
    val speedMps: Float? = null,
    val bearingDeg: Float? = null,
    val timestampMs: Long,
)
