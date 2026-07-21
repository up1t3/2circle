package com.twocircle.bike.feature.tracks.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.common.outcome.Outcome
import com.twocircle.bike.data.repository.TracksRepository
import com.twocircle.bike.feature.tracks.gpx.GpxExporter
import com.twocircle.bike.feature.tracks.gpx.GpxParser
import com.twocircle.bike.feature.tracks.model.TrackDetail
import com.twocircle.bike.feature.tracks.model.TrackListItem
import com.twocircle.bike.feature.tracks.model.toGpxDocument
import com.twocircle.bike.feature.tracks.model.toListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class TracksViewModel @Inject constructor(
    private val tracks: TracksRepository,
    private val exporter: GpxExporter,
    private val gpxParser: GpxParser,
) : ViewModel() {

    /** Reactive list of all rides, mapped to list-item rows. */
    val listState: StateFlow<TracksUiState> = tracks.allTracksFlow()
        .map { rows -> TracksUiState.Loaded(rows.map { it.toListItem() }) as TracksUiState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TracksUiState.Loading,
        )

    private val _detailState = MutableStateFlow<TrackDetailUiState>(TrackDetailUiState.Loading)
    val detailState: StateFlow<TrackDetailUiState> = _detailState.asStateFlow()

    /** Load the detail (summary + raw points) for [trackId]. */
    fun loadDetail(trackId: String) {
        viewModelScope.launch {
            _detailState.value = TrackDetailUiState.Loading
            val summary = when (val r = tracks.byId(trackId)) {
                is Outcome.Success -> r.value
                is Outcome.Failure -> {
                    _detailState.value = TrackDetailUiState.Error(r.failure)
                    return@launch
                }
            }
            if (summary == null) {
                _detailState.value = TrackDetailUiState.NotFound
                return@launch
            }
            val points = when (val r = tracks.pointsFor(trackId)) {
                is Outcome.Success -> r.value
                is Outcome.Failure -> {
                    _detailState.value = TrackDetailUiState.Error(r.failure)
                    return@launch
                }
            }
            _detailState.value = TrackDetailUiState.Loaded(
                TrackDetail(summary = com.twocircle.bike.data.model.TrackSummary(
                    id = summary.id, name = summary.name,
                    startedAtMs = summary.startedAtMs, endedAtMs = summary.endedAtMs,
                    status = summary.status, distanceMeters = summary.distanceMeters,
                    movingSeconds = summary.movingSeconds, ascentMeters = summary.ascentMeters,
                    descentMeters = summary.descentMeters, avgSpeedMps = summary.avgSpeedMps,
                ), points = points),
            )
        }
    }

    /**
     * Export a track to GPX. Returns the shareable file URI on success.
     */
    suspend fun exportGpx(trackId: String): android.net.Uri? {
        val loaded = (_detailState.value as? TrackDetailUiState.Loaded)?.detail
            ?: return null
        val doc = loaded.points.toGpxDocument(name = loaded.summary.name)
        val uri = exporter.export(doc, nameHint = loaded.summary.name)
        if (uri == null) Timber.w("Export returned null URI for %s", trackId)
        return uri
    }

    /** Delete a track by id. */
    fun delete(trackId: String) {
        viewModelScope.launch {
            when (val r = tracks.delete(trackId)) {
                is Outcome.Failure -> Timber.w("Delete failed: ${r.failure}")
                is Outcome.Success -> Timber.d("Deleted %s", trackId)
            }
        }
    }

    /**
     * Import a GPX file from a content URI (picked via SAF).
     *
     * Reads the file, parses it through [GpxParser], and creates a new TrackEntity
     * with all points. The track appears in the list immediately after import.
     */
    fun importGpx(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            try {
                val xml = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: run { Timber.w("GPX import: could not open stream for %s", uri); return@launch }
                val doc = gpxParser.parse(xml)
                val now = System.currentTimeMillis()
                val trackId = java.util.UUID.randomUUID().toString()
                val name = doc.name ?: "Imported ride ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(now))}"
                val points = doc.points.mapIndexed { i, pt ->
                    com.twocircle.bike.data.db.entity.TrackPointEntity(
                        trackId = trackId,
                        seq = i.toLong(),
                        lat = pt.lat,
                        lon = pt.lon,
                        ele = pt.ele,
                        timestampMs = pt.timeIso?.let {
                            runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
                        } ?: now + i,
                    )
                }
                tracks.importTrack(trackId, name, now, points)
                Timber.i("GPX imported: %s (%d points)", trackId, points.size)
            } catch (e: Exception) {
                Timber.e(e, "GPX import failed for %s", uri)
            }
        }
    }
}
