package com.twocircle.bike.feature.tracks.screen

import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.feature.tracks.model.TrackDetail
import com.twocircle.bike.feature.tracks.model.TrackListItem

/**
 * Tracks list screen state.
 */
sealed interface TracksUiState {
    data object Loading : TracksUiState
    data class Loaded(val items: List<TrackListItem>) : TracksUiState
    data class Error(val failure: Failure) : TracksUiState
}

/**
 * Track detail screen state.
 */
sealed interface TrackDetailUiState {
    data object Loading : TrackDetailUiState
    data class Loaded(val detail: TrackDetail) : TrackDetailUiState
    data object NotFound : TrackDetailUiState
    data class Error(val failure: Failure) : TrackDetailUiState
}
