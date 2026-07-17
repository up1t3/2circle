package com.twocircle.bike.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One recorded ride. Immutable metadata; mutable status fields drive the UI.
 *
 * [status] is the crash-recovery state machine — see [TrackStatus]. The tracking service
 * transitions it explicitly so that on next boot we can detect an interrupted ride and
 * either resume or finalise it. This is the red-line "no point lost between GPS and disk"
 * guarantee.
 *
 * Denormalised aggregates ([distanceMeters], [ascentMeters], …) are updated as the ride
 * progresses, not just at the end — the live ETA engine and the foreground notification
 * both read them every second.
 */
@Entity(
    tableName = "tracks",
    indices = [
        Index("status"),
        Index("startedAtMs"),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Free-form note the rider can attach post-ride. */
    val note: String? = null,
    /** When recording started (epoch ms). */
    val startedAtMs: Long,
    /** When recording ended (epoch ms); null while in progress. */
    val endedAtMs: Long? = null,
    /** Last aggregate update (epoch ms); drives UI refresh decisions. */
    val updatedAtMs: Long = startedAtMs,
    val status: TrackStatus,
    /** Total distance covered, in metres. */
    val distanceMeters: Double = 0.0,
    /** Moving time excluding pauses, in seconds. */
    val movingSeconds: Long = 0L,
    /** Total elapsed time, in seconds. */
    val elapsedSeconds: Long = 0L,
    /** Cumulative uphill elevation, in metres. */
    val ascentMeters: Double = 0.0,
    /** Cumulative downhill elevation (absolute), in metres. */
    val descentMeters: Double = 0.0,
    /** Max speed observed, in m/s. */
    val maxSpeedMps: Double = 0.0,
    /** Average moving speed, in m/s. */
    val avgSpeedMps: Double = 0.0,
    /** Optional planned route this ride follows. */
    val routePlanId: String? = null,
    /** Source: live ride or imported GPX. */
    val source: TrackSource = TrackSource.Live,
)

/** Ride lifecycle. Ordering matters — see [TrackEntity.status] docs. */
enum class TrackStatus {
    /** Recording in progress now. */
    Recording,

    /** Recording paused (rest stop); not ended. */
    Paused,

    /** Recording ended normally; aggregates finalised. */
    Finished,

    /**
     * Recording was interrupted by crash / reboot / kill. On next launch we surface
     * this state and offer the rider to resume or finalise from what was persisted.
     */
    Interrupted,
}

enum class TrackSource { Live, GpxImport }
