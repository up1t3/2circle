package com.twocircle.bike.feature.search.screen

import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.feature.search.model.ScoredResult

/**
 * Search screen UI state.
 *
 * - [Idle] — no query yet; show a hint.
 * - [Searching] — query in flight (debounced); keep showing previous results to avoid flicker.
 * - [Results] — ranked list ready.
 * - [Empty] — query ran, no matches.
 * - [NoRegion] — no offline region available; the search index is unavailable.
 * - [Error] — typed failure surfaced.
 */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Searching : SearchUiState
    data class Results(val items: List<ScoredResult>) : SearchUiState
    data object Empty : SearchUiState
    data object NoRegion : SearchUiState
    data class Error(val failure: Failure) : SearchUiState
}
