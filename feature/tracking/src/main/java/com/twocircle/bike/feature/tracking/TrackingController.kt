package com.twocircle.bike.feature.tracking

import com.twocircle.bike.data.db.entity.TrackEntity
import com.twocircle.bike.data.db.entity.TrackStatus
import com.twocircle.bike.data.repository.TracksRepository
import com.twocircle.bike.feature.tracking.model.PointSample
import com.twocircle.bike.feature.tracking.model.SamplerState
import com.twocircle.bike.feature.tracking.model.TrackAggregates
import com.twocircle.bike.feature.tracking.model.TrackingState
import com.twocircle.bike.feature.tracking.model.initialTrackingState
import com.twocircle.bike.feature.tracking.model.withAcceptedSample
import com.twocircle.bike.feature.tracking.persistence.PointPipeline
import com.twocircle.bike.feature.tracking.sampler.PollingPolicy
import com.twocircle.bike.feature.tracking.sampler.SampleFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.ArrayDeque
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates one tracking session: GPS → filter → pipeline + aggregate fold.
 *
 * The Service (foreground) owns the GPS subscription; it feeds raw fixes here via
 * [onSample]. The controller decides acceptance, updates aggregates, and enqueues
 * accepted samples into the pipeline. The Service periodically calls
 * [pipeline.flushBatch] to drain the buffer.
 *
 * Why split this from the Service? Because (a) it's pure Kotlin (testable without the
 * Service framework) and (b) the controller survives Service recreation — the OS can
 * tear down and rebuild the Service object, but the singleton controller keeps the
 * session state intact, so a transient Service death doesn't end a ride.
 *
 * Lifecycle:
 *  - [start] → creates a Track, binds the pipeline, marks Recording.
 *  - [resume] → re-binds the pipeline after a crash-recovery restart.
 *  - [pause] / [resume] → toggle between Recording and Paused (rest stops).
 *  - [stop] → flushes, finalises aggregates, marks Finished.
 */
@Singleton
class TrackingController @Inject constructor(
    private val tracks: TracksRepository,
    private val pipeline: PointPipeline,
) {

    private val _state = MutableStateFlow(initialTrackingState())
    val state: StateFlow<TrackingState> get() = _state.asStateFlow()

    // Recent samples for the polling policy (head = newest). Bounded.
    private val recentSamples: ArrayDeque<PointSample> = ArrayDeque(RECENT_WINDOW)
    private var lastAccepted: PointSample? = null

    /**
     * Begin a new ride. Returns the new track id, or null on storage failure.
     */
    suspend fun start(nowMs: Long = System.currentTimeMillis()): String? {
        require(!isActive()) { "Tracking already active" }
        val trackId = UUID.randomUUID().toString()
        val track = TrackEntity(
            id = trackId,
            name = "Ride ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(nowMs))}",
            startedAtMs = nowMs,
            status = TrackStatus.Recording,
        )
        val outcome = tracks.create(track)
        if (outcome !is com.twocircle.bike.common.outcome.Outcome.Success) {
            Timber.e("Failed to create track: $outcome")
            return null
        }
        pipeline.bindForTrack(trackId)
        recentSamples.clear()
        lastAccepted = null
        _state.value = TrackingState(
            trackId = trackId,
            aggregates = TrackAggregates(startedAtMs = nowMs, lastAcceptedAtMs = nowMs),
            sampler = SamplerState.Acquiring,
            isRecording = true,
        )
        return trackId
    }

    /**
     * Resume an interrupted ride (after a crash-recovery restart).
     * Rebinds the pipeline; the seq watermark is loaded by [PointPipeline.bindForTrack].
     */
    suspend fun resume(trackId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        require(!isActive()) { "Tracking already active" }
        when (val r = tracks.byId(trackId)) {
            is com.twocircle.bike.common.outcome.Outcome.Success -> {
                val existing = r.value ?: return false
                pipeline.bindForTrack(trackId)
                tracks.setStatus(trackId, TrackStatus.Recording)
                _state.value = TrackingState(
                    trackId = trackId,
                    aggregates = TrackAggregates(
                        distanceMeters = existing.distanceMeters,
                        movingSeconds = existing.movingSeconds,
                        elapsedSeconds = existing.elapsedSeconds,
                        ascentMeters = existing.ascentMeters,
                        descentMeters = existing.descentMeters,
                        maxSpeedMps = existing.maxSpeedMps,
                        avgSpeedMps = existing.avgSpeedMps,
                        startedAtMs = existing.startedAtMs,
                        lastAcceptedAtMs = existing.updatedAtMs,
                        pointCount = 0, // unknown after recovery; pipeline will accumulate
                    ),
                    sampler = SamplerState.Acquiring,
                    isRecording = true,
                )
                return true
            }
            else -> {
                Timber.w("Resume: track %s not found", trackId)
                return false
            }
        }
    }

    /** Feed a raw GPS sample. Decides acceptance, folds aggregates, enqueues if accepted. */
    suspend fun onSample(raw: PointSample, nowMs: Long = System.currentTimeMillis()) {
        if (!isActive()) return
        // Track recent samples for the polling policy (head = newest).
        pushRecent(raw)
        val sampler = PollingPolicy.next(_state.value.sampler, recentSamples.toList(), nowMs)

        val accepted = SampleFilter.accept(raw, lastAccepted)
        if (accepted != null) {
            val updatedAgg = _state.value.aggregates.withAcceptedSample(lastAccepted, accepted, nowMs)
            lastAccepted = accepted
            pipeline.enqueue(accepted)
            _state.value = _state.value.copy(aggregates = updatedAgg, sampler = sampler)
            // Mirror aggregates into the track row periodically (the service drives this).
        } else {
            _state.value = _state.value.copy(sampler = sampler)
        }
    }

    /** Pause (rest stop). The OS may now let the Service drop to a low-priority state. */
    suspend fun pause() {
        if (!isActive()) return
        val tid = _state.value.trackId ?: return
        tracks.setStatus(tid, TrackStatus.Paused)
        _state.value = _state.value.copy(isRecording = false)
    }

    /** Resume from a paused state. */
    suspend fun resumeFromPause() {
        val tid = _state.value.trackId ?: return
        tracks.setStatus(tid, TrackStatus.Recording)
        _state.value = _state.value.copy(isRecording = true)
    }

    /**
     * Finalise the ride. Flushes the pipeline and writes final aggregates to the track row.
     */
    suspend fun stop(nowMs: Long = System.currentTimeMillis()) {
        val tid = _state.value.trackId ?: return
        val agg = _state.value.aggregates
        pipeline.flushBatch()
        tracks.updateAggregates(
            id = tid,
            distanceMeters = agg.distanceMeters,
            movingSeconds = agg.movingSeconds,
            elapsedSeconds = agg.elapsedSeconds,
            ascentMeters = agg.ascentMeters,
            descentMeters = agg.descentMeters,
            maxSpeedMps = agg.maxSpeedMps,
            avgSpeedMps = agg.avgSpeedMps,
            endedAtMs = nowMs,
        )
        tracks.setStatus(tid, TrackStatus.Finished)
        pipeline.unbind()
        recentSamples.clear()
        lastAccepted = null
        _state.value = initialTrackingState()
    }

    /**
     * Flush the in-memory pipeline + persist current aggregates. The Service calls this
     * on a timer (e.g. every 10 s) so a crash never loses more than that window.
     */
    suspend fun checkpoint() {
        val tid = _state.value.trackId ?: return
        pipeline.flushBatch()
        val agg = _state.value.aggregates
        tracks.updateAggregates(
            id = tid,
            distanceMeters = agg.distanceMeters,
            movingSeconds = agg.movingSeconds,
            elapsedSeconds = agg.elapsedSeconds,
            ascentMeters = agg.ascentMeters,
            descentMeters = agg.descentMeters,
            maxSpeedMps = agg.maxSpeedMps,
            avgSpeedMps = agg.avgSpeedMps,
            endedAtMs = null,
        )
    }

    fun isActive(): Boolean = _state.value.isRecording && _state.value.trackId != null

    private fun pushRecent(sample: PointSample) {
        recentSamples.addFirst(sample)
        while (recentSamples.size > RECENT_WINDOW) recentSamples.removeLast()
    }

    companion object {
        /** Window of samples the polling policy considers. */
        private const val RECENT_WINDOW = 8
    }
}
