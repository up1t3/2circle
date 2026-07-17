package com.twocircle.bike.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.twocircle.bike.data.db.entity.TrackEntity
import com.twocircle.bike.data.db.entity.TrackStatus
import kotlinx.coroutines.flow.Flow

/**
 * Track metadata DAO. Aggregates ([distanceMeters], [ascentMeters], …) are updated
 * in place as the ride progresses — the live ETA engine and notification read these
 * every tick, so a derived aggregate row would be both stale and slow.
 */
@Dao
interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Update
    suspend fun update(track: TrackEntity)

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun byId(id: String): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY startedAtMs DESC")
    fun all(): Flow<List<TrackEntity>>

    /**
     * Find tracks in a recoverable state. On launch, the app offers the rider to resume
     * or finalise any such track — the "no point lost" guarantee.
     */
    @Query("SELECT * FROM tracks WHERE status IN (:statuses) ORDER BY startedAtMs DESC")
    suspend fun withStatus(statuses: List<TrackStatus>): List<TrackEntity>

    @Query("UPDATE tracks SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: TrackStatus)

    /** Incremental aggregate update without overwriting unrelated fields. */
    @Query(
        """
        UPDATE tracks SET
            distanceMeters = :distanceMeters,
            movingSeconds  = :movingSeconds,
            elapsedSeconds = :elapsedSeconds,
            ascentMeters   = :ascentMeters,
            descentMeters  = :descentMeters,
            maxSpeedMps    = MAX(maxSpeedMps, :maxSpeedMps),
            avgSpeedMps    = :avgSpeedMps,
            endedAtMs      = :endedAtMs,
            updatedAtMs    = :updatedAtMs
        WHERE id = :id
        """,
    )
    suspend fun updateAggregates(
        id: String,
        distanceMeters: Double,
        movingSeconds: Long,
        elapsedSeconds: Long,
        ascentMeters: Double,
        descentMeters: Double,
        maxSpeedMps: Double,
        avgSpeedMps: Double,
        endedAtMs: Long?,
        updatedAtMs: Long,
    )

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: String)
}
