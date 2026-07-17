package com.twocircle.bike.feature.search.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.data.repository.RegionsRepository
import com.twocircle.bike.data.filesystem.RegionAssets
import com.twocircle.bike.feature.search.engine.SearchEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Search screen view model.
 *
 * Wires a debounced query flow to [SearchEngine]. Debounce (250 ms) keeps the FTS
 * index off the CPU while the rider is mid-typing — important on a phone where every
 * query is a SQLite read. Results are produced on Dispatchers.IO via flowOn so the UI
 * thread is never blocked even on slow regions.
 *
 * Region binding: on first query (or on [refresh]), the engine is pointed at the
 * active region's search.db. If no region is installed, we short-circuit to
 * [SearchUiState.NoRegion] — search is offline-only by design; an online fallback
 * would violate the Offline-First contract.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchEngine: SearchEngine,
    private val regionsRepository: RegionsRepository,
    private val regionAssets: RegionAssets,
) : ViewModel() {

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    /** Current query value, exposed for the input field's `value`. */
    val query: StateFlow<String> = queryFlow.asStateFlow()

    init {
        observeQueries()
    }

    /** Update the search query (typically called on every keystroke). */
    fun onQueryChange(q: String) {
        queryFlow.value = q
        if (q.isBlank()) _state.value = SearchUiState.Idle
    }

    /** Re-bind to the active region (e.g. after a download completes). */
    fun refresh() {
        // Force re-evaluation of the current query against a possibly-new region.
        val current = queryFlow.value
        queryFlow.value = ""
        queryFlow.value = current
    }

    private fun observeQueries() {
        queryFlow
            .debounce(QUERY_DEBOUNCE_MS)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctUntilChanged()
            .onEach { q -> execute(q) }
            .launchIn(viewModelScope)
    }

    private suspend fun execute(query: String) {
        // Bind the engine to the active region if needed.
        if (!ensureRegionBound()) return
        _state.value = SearchUiState.Searching
        val anchor = currentAnchor()
        val results = try {
            searchEngine.search(
                query = query,
                anchorLat = anchor?.first,
                anchorLon = anchor?.second,
                limit = MAX_RESULTS,
            )
        } catch (e: Exception) {
            Timber.e(e, "Search failed")
            _state.value = SearchUiState.Error(Failure.Unknown(e))
            return
        }
        _state.value = if (results.isEmpty()) SearchUiState.Empty else SearchUiState.Results(results)
    }

    private suspend fun ensureRegionBound(): Boolean {
        val firstInstalled = regionsRepository.firstInstalledOrNull()
        if (firstInstalled == null) {
            _state.value = SearchUiState.NoRegion
            return false
        }
        val searchDb = regionAssets.searchDbPath(firstInstalled)
        return searchEngine.useRegion(searchDb).also { ok ->
            if (!ok) _state.value = SearchUiState.Error(Failure.Region.Corrupted(firstInstalled.id))
        }
    }

    /**
     * Anchor for proximity scoring. For v1 this is the centre of the active region's
     * bbox — good enough; a future step will use the current map centre / GPS position.
     */
    private suspend fun currentAnchor(): Pair<Double, Double>? {
        val region = regionsRepository.firstInstalledOrNull() ?: return null
        return ((region.boundsMinLat + region.boundsMaxLat) / 2.0) to
            ((region.boundsMinLon + region.boundsMaxLon) / 2.0)
    }

    override fun onCleared() {
        searchEngine.close()
        super.onCleared()
    }

    companion object {
        private const val QUERY_DEBOUNCE_MS = 250L
        private const val MAX_RESULTS = 30
    }
}
