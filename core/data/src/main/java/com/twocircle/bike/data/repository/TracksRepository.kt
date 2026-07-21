package com.twocircle.bike.data.repository

import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.common.outcome.Outcome
import com.twocircle.bike.data.db.dao.TrackDao
import com.twocircle.bike.data.db.dao.TrackPointDao
import com.twocircle.bike.data.db.entity.TrackEntity
import com.twocircle.bike.data.db.entity.TrackPointEntity
import com.twocircle.bike.data.db.entity.TrackStatus
import com.twocircle.bike.data.model.TrackSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks domain repository. Boundary between UseCases (in :core:domain) and Room.
 *
 * All methods wrap DB calls in [Outcome] so failures surface as typed [Failure.Storage]
 * instead of leaking exceptions into the MVI state. The tracking service and tracks UI
 * consume these.
 */
@Singleton
class TracksRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
) {

    fun allTracksFlow(): Flow<List<TrackSummary>> =
        trackDao.all().map { rows -> rows.map { ittoSummary(it) } }

    suspend fun recoverableTracks(): Outcome<List<TrackSummary>> = try {
        val rows = trackDao.withStatus(listOf(TrackStatus.Recording, TrackStatus.Paused, TrackStatus.Interrupted))
        Outcome.Success(rows.map { ittoSummary(it) })
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }

    suspend fun create(track: TrackEntity): Outcome<Unit> = try {
        trackDao.upsert(track)
        Outcome.Success(Unit)
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }

    /** Fetch the full track row (used by crash-recovery resume). */
    suspend fun byId(id: String): Outcome<TrackEntity?> = try {
        Outcome.Success(trackDao.byId(id))
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }

    suspend fun setStatus(id: String, status: TrackStatus): Outcome<Unit> = try {
        trackDao.setStatus(id, status)
        Outcome.Success(Unit)
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }

    /**
     * Hot write path: batch-persist GPS samples. The tracking service holds points in a
     * bounded in-memory queue and flushes them periodically. [trackId] + monotonic [seq]
     * per point are the deduplication contract.
     */
    suspend fun appendPoints(trackId: String, points: List<TrackPointEntity>): Outcome<Unit> = try {
        if (points.isEmpty()) {
            Outcome.Success(Unit)
        } else {
            trackPointDao.insertAll(points)
            Outcome.Success(Unit)
        }
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }

    /** Where to continue seq numbering after a crash-recovery restart. */
    suspend fun nextSeqFor(trackId: String): Outcome<Long> = try {
        Outcome.Success(trackPointDao.maxSeqForTrack(trackId) + 1L)
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }

    suspend fun pointsFor(trackId: String): Outcome<List<TrackPointEntity>> = try {
        Outcome.Success(trackPointDao.pointsForTrack(trackId))
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }

    suspend fun pointCount(trackId: String): Outcome<Int> = try {
        Outcome.Success(trackPointDao.countForTrack(trackId))
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }

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
    ): Outcome<Unit> = try {
        trackDao.updateAggregates(
            id = id,
            distanceMeters = distanceMeters,
            movingSeconds = movingSeconds,
            elapsedSeconds = elapsedSeconds,
            ascentMeters = ascentMeters,
            descentMeters = descentMeters,
            maxSpeedMps = maxSpeedMps,
            avgSpeedMps = avgSpeedMps,
            endedAtMs = endedAtMs,
            updatedAtMs = System.currentTimeMillis(),
        )
        Outcome.Success(Unit)
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }

    suspend fun delete(id: String): Outcome<Unit> = try {
        trackDao.delete(id)
        Outcome.Success(Unit)
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }

    /**
     * Import a GPX file as a new track. Creates the TrackEntity + all TrackPointEntity rows
     * in one call. Called from the TracksViewModel when the user picks a .gpx file via SAF.
     */
    suspend fun importTrack(
        trackId: String,
        name: String,
        startedAtMs: Long,
        points: List<TrackPointEntity>,
    ): Outcome<Unit> = try {
        // Compute basic aggregates from points.
        var dist = 0.0
        var ascent = 0.0
        var descent = 0.0
        var prevEle: Double? = null
        var prevLat: Double? = null
        var prevLon: Double? = null
        for (pt in points) {
            if (prevLat != null && prevLon != null) {
                val earthRadius = 6371000.0
                val dLat = Math.toRadians(pt.lat - prevLat)
                val dLon = Math.toRadians(pt.lon - prevLon)
                val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(prevLat)) * Math.cos(Math.toRadians(pt.lat)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
                dist += earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            }
            if (pt.ele != null && prevEle != null) {
                val d = pt.ele - prevEle
                if (d > 0) ascent += d else descent += -d
            }
            prevEle = pt.ele
            prevLat = pt.lat
            prevLon = pt.lon
        }
        val now = System.currentTimeMillis()
        val track = TrackEntity(
            id = trackId,
            name = name,
            startedAtMs = startedAtMs,
            endedAtMs = now,
            status = TrackStatus.Finished,
            source = com.twocircle.bike.data.db.entity.TrackSource.GpxImport,
            distanceMeters = dist,
            ascentMeters = ascent,
            descentMeters = descent,
        )
        trackDao.upsert(track)
        if (points.isNotEmpty()) trackPointDao.insertAll(points)
        Outcome.Success(Unit)
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }

    private fun ittoSummary(e: TrackEntity): TrackSummary = TrackSummary(
        id = e.id,
        name = e.name,
        startedAtMs = e.startedAtMs,
        endedAtMs = e.endedAtMs,
        status = e.status,
        distanceMeters = e.distanceMeters,
        movingSeconds = e.movingSeconds,
        ascentMeters = e.ascentMeters,
        descentMeters = e.descentMeters,
        avgSpeedMps = e.avgSpeedMps,
    )
}
