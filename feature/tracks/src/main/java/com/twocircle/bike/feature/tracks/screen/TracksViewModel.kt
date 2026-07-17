package com.twocircle.bike.feature.tracks.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.common.outcome.Outcome
import com.twocircle.bike.data.repository.TracksRepository
import com.twocircle.bike.feature.tracks.gpx.GpxExporter
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
}
