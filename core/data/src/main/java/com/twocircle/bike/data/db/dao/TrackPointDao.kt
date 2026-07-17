package com.twocircle.bike.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.twocircle.bike.data.db.entity.TrackPointEntity
import kotlinx.coroutines.flow.Flow

/**
 * Hot write path: GPS samples → disk. The tracking service batches points in memory and
 * flushes via [insertAll] in a single transaction to amortise I/O and avoid starving the
 * map renderer. [seq] uniqueness per track guarantees we never duplicate a sample even if
 * a flush is retried after a transient DB error.
 */
@Dao
interface TrackPointDao {

    /** Batch insert. Caller guarantees monotonic [TrackPointEntity.seq] within a track. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(points: List<TrackPointEntity>)

    /** Streaming read for the post-ride analyser. */
    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY seq ASC")
    suspend fun pointsForTrack(trackId: String): List<TrackPointEntity>

    /** Latest point — for the live UI ("you are here"). */
    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY seq DESC LIMIT 1")
    fun latestPointFlow(trackId: String): Flow<TrackPointEntity?>

    /** Count of persisted points — used to verify no samples were dropped on crash-recovery. */
    @Query("SELECT COUNT(*) FROM track_points WHERE trackId = :trackId")
    suspend fun countForTrack(trackId: String): Int

    /** Highest seq persisted so far; -1 if track is empty. Drives the next [insertAll] seq base. */
    @Query("SELECT IFNULL(MAX(seq), -1) FROM track_points WHERE trackId = :trackId")
    suspend fun maxSeqForTrack(trackId: String): Long
}
