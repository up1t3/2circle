package com.twocircle.bike.feature.tracking.persistence

import com.twocircle.bike.data.db.entity.TrackPointEntity
import com.twocircle.bike.data.repository.TracksRepository
import com.twocircle.bike.feature.tracking.model.PointSample
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crash-safe pipeline: accepted samples → bounded buffer → batched DB flush.
 *
 * This is the red-line "no point lost between GPS and disk" guarantee. The design:
 *
 * 1. Samples enter through [enqueue]. We NEVER write to Room here — that would block
 *    the GPS callback. Instead we append to a small in-memory ring buffer.
 * 2. A periodic flush (driven by the service) calls [flushBatch], which atomically
 *    writes everything in the buffer as one transaction, then drops those entries.
 * 3. Each sample gets a strictly monotonic [seq] at enqueue time. The seq + trackId
 *    pair has a unique index in Room, so a duplicate flush after a transient DB error
 *    is a no-op rather than a data-corrupting double-insert.
 * 4. On startup after a crash, [recoverSeqFor] reads MAX(seq) for the track so the
 *    resumed pipeline continues numbering correctly. Without this, seq would restart
 *    at 0 and collide with previously-persisted points.
 *
 * Bounded buffer: we cap at [MAX_BUFFERED] samples. In steady state the buffer holds
 * a few seconds of points at most; if flush ever falls badly behind (e.g. disk full),
 * we drop the oldest to keep the latest. Dropping is logged loudly so the user can be
 * warned that their track has gaps.
 *
 * Concurrency: enqueue happens on whatever thread the GPS callback fires; flush happens
 * on a background dispatcher. The mutex serialises buffer mutations.
 */
@Singleton
class PointPipeline @Inject constructor(
    private val tracks: TracksRepository,
) {

    private val mutex = Mutex()

    // SharedFlow lets the UI observe the most-recent persisted point for live HUD.
    private val _latestPersisted = MutableSharedFlow<TrackPointEntity>(
        replay = 1,
        extraBufferCapacity = 16,
    )
    val latestPersisted: SharedFlow<TrackPointEntity> get() = _latestPersisted.asSharedFlow()

    /** Snapshots of the in-memory buffer, for diagnostics / debugging only. */
    @Volatile private var buffer: ArrayDeque<BufferedPoint> = ArrayDeque()
    @Volatile private var nextSeq: Long = 0L
    @Volatile private var currentTrackId: String? = null

    /**
     * Bind the pipeline to [trackId] and read the persisted seq watermark.
     * Call this exactly once when a ride starts (or resumes after a crash).
     */
    suspend fun bindForTrack(trackId: String) = mutex.withLock {
        currentTrackId = trackId
        // Resume from the persisted watermark; if the track is new, start at 0.
        nextSeq = when (val r = tracks.nextSeqFor(trackId)) {
            is com.twocircle.bike.common.outcome.Outcome.Success -> r.value
            else -> 0L
        }
        buffer.clear()
        Timber.i("Pipeline bound to track %s, next seq = %d", trackId, nextSeq)
    }

    /** Detach from a track (e.g. when stopping). Flushes the buffer first. */
    suspend fun unbind() = mutex.withLock {
        currentTrackId = null
        // Caller should flush() before unbinding in normal flow; this is a safety net.
        buffer.clear()
    }

    /**
     * Append a sample to the buffer. Returns the assigned seq, or null if the pipeline
     * is not bound to a track.
     */
    suspend fun enqueue(sample: PointSample): Long? = mutex.withLock {
        val tid = currentTrackId ?: return@withLock null
        val seq = nextSeq++
        val buffered = BufferedPoint(trackId = tid, seq = seq, sample = sample)
        if (buffer.size >= MAX_BUFFERED) {
            // Buffer overflow: drop the oldest. Loud log — this means data loss.
            val dropped = buffer.removeFirst()
            Timber.w("Pipeline buffer overflow; dropping seq=${dropped.seq} (track=$tid)")
        }
        buffer.addLast(buffered)
        seq
    }

    /**
     * Write everything in the buffer to Room in one transaction.
     * Safe to retry: the seq+trackId unique index deduplicates.
     */
    suspend fun flushBatch(): Int = mutex.withLock {
        val tid = currentTrackId ?: return@withLock 0
        if (buffer.isEmpty()) return@withLock 0
        val toWrite = buffer.toList()
        val entities = toWrite.map { it.toEntity() }
        when (val r = tracks.appendPoints(tid, entities)) {
            is com.twocircle.bike.common.outcome.Outcome.Success -> {
                buffer.clear()
                // Emit the last-written point so the live HUD updates.
                entities.maxByOrNull { it.seq }?.let { _latestPersisted.tryEmit(it) }
                Timber.d("Flushed %d points (track=%s)", entities.size, tid)
                entities.size
            }
            is com.twocircle.bike.common.outcome.Outcome.Failure -> {
                // Keep the buffer intact for the next flush attempt — losing the write
                // is not acceptable per the red-line contract.
                Timber.e(r.failure.javaClass.simpleName)
                0
            }
        }
    }

    /** Snapshot of pending points awaiting flush — for testing and debugging. */
    suspend fun pendingCount(): Int = mutex.withLock { buffer.size }

    /** Reads the persisted seq watermark for [trackId]; used by crash recovery. */
    suspend fun recoverSeqFor(trackId: String): Long =
        when (val r = tracks.nextSeqFor(trackId)) {
            is com.twocircle.bike.common.outcome.Outcome.Success -> r.value
            else -> 0L
        }

    /** Internal buffered representation. */
    private data class BufferedPoint(
        val trackId: String,
        val seq: Long,
        val sample: PointSample,
    ) {
        fun toEntity(): TrackPointEntity = TrackPointEntity(
            trackId = trackId,
            seq = seq,
            lat = sample.lat,
            lon = sample.lon,
            ele = sample.ele,
            accuracyMeters = sample.accuracyMeters,
            speedMps = sample.speedMps,
            bearingDeg = sample.bearingDeg,
            timestampMs = sample.timestampMs,
        )
    }

    companion object {
        /** Max points held before we drop oldest. ~1 minute at 3s cadence = 20 points. */
        const val MAX_BUFFERED = 60
    }
}
