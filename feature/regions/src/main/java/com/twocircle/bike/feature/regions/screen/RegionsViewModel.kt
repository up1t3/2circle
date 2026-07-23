package com.twocircle.bike.feature.regions.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.common.outcome.Outcome
import com.twocircle.bike.data.db.entity.RegionInstallState
import com.twocircle.bike.data.repository.RegionsRepository
import com.twocircle.bike.feature.regions.download.RegionCatalog
import com.twocircle.bike.feature.regions.download.RegionDownloader
import com.twocircle.bike.feature.regions.download.toEntity
import com.twocircle.bike.feature.regions.manifest.RegionEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Regions screen view model.
 *
 * Merges two sources into [RegionsUiState.Loaded.rows]:
 *  - the remote catalog (what's available to download)
 *  - the local DB (what's already installed, including Failed/Downloading states)
 *
 * The merge is by region id. The local row wins on [installState] (because it reflects
 * reality); the catalog row wins on size/version (because it reflects what the backend
 * currently publishes).
 */
@HiltViewModel
class RegionsViewModel @Inject constructor(
    private val catalog: RegionCatalog,
    private val downloader: RegionDownloader,
    private val regions: RegionsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<RegionsUiState>(RegionsUiState.Loading)
    val state: StateFlow<RegionsUiState> = _state.asStateFlow()

    /** Active region ID selected by the user. */
    val activeRegionId: StateFlow<String?> = regions.activeRegionId

    fun setActiveRegion(id: String) = regions.setActiveRegion(id)

    private var cachedCatalog: List<RegionEntry> = emptyList()

    init {
        observeLocalAndProgress()
        refresh()
    }

    /** Re-fetch the catalog from the backend. */
    fun refresh() {
        viewModelScope.launch {
            when (val r = catalog.fetch()) {
                is Outcome.Success -> {
                    cachedCatalog = r.value.regions
                    val validIds = r.value.regions.map { it.id }.toSet()
                    val local = regions.allFlow().firstOrNull() ?: emptyList()
                    local.forEach { entity ->
                        if (entity.id !in validIds && entity.installState != RegionInstallState.Installed) {
                            regions.delete(entity.id)
                        }
                    }
                    // Force re-emission: combine() doesn't observe cachedCatalog (a
                    // plain field, not a flow), so without this nudge the rows list
                    // wouldn't refresh after a successful fetch.
                    emitMergedRows()
                }
                is Outcome.Failure -> {
                    if (cachedCatalog.isEmpty()) _state.value = RegionsUiState.Error(r.failure)
                    else Timber.w("Catalog refresh failed; keeping cached: ${r.failure}")
                }
            }
        }
    }

    /**
     * Recompute the merged projection and push it to [_state].
     *
     * Called whenever [cachedCatalog] changes — combine() in [observeLocalAndProgress]
     * doesn't see this field (it's not a flow), so catalog updates need an explicit
     * re-emit. Local-DB changes are still handled reactively via the combine.
     */
    private suspend fun emitMergedRows() {
        val local = regions.allFlow().firstOrNull() ?: emptyList()
        val rows = mergeRows(local, cachedCatalog)
        _state.value = if (rows.isEmpty() && cachedCatalog.isEmpty()) {
            RegionsUiState.NoRegion
        } else {
            RegionsUiState.Loaded(rows, downloader.progress.value)
        }
    }

    /** Trigger a download for [entry]. */
    fun download(entry: RegionEntry) {
        viewModelScope.launch {
            downloader.download(entry)
            rebuildRows()
        }
    }

    /** Delete an installed region (frees disk). */
    fun delete(regionId: String) {
        viewModelScope.launch {
            regions.delete(regionId)
            rebuildRows()
        }
    }

    private fun observeLocalAndProgress() {
        combine(regions.allFlow(), downloader.progress) { local, progress ->
            // When either source changes, rebuild the merged projection.
            val rows = mergeRows(local, cachedCatalog)
            val finalState: RegionsUiState = if (rows.isEmpty() && cachedCatalog.isEmpty()) {
                RegionsUiState.NoRegion
            } else {
                RegionsUiState.Loaded(rows, progress)
            }
            _state.value = finalState
        }.launchIn(viewModelScope)
    }

    private suspend fun rebuildRows() {
        // The combine in observeLocalAndProgress reacts to downloader.progress and
        // regions.allFlow changes, both of which download()/delete() already trigger
        // via DB upserts. So nothing explicit is needed here beyond what refresh and
        // download already do — this method is a stable call-site for future rebuilds.
        Timber.d("rebuildRows: %d cached entries", cachedCatalog.size)
    }

    private fun mergeRows(
        local: List<com.twocircle.bike.data.db.entity.RegionEntity>,
        catalogEntries: List<RegionEntry>,
    ): List<RegionRow> {
        val byId = linkedMapOf<String, RegionRow>()
        // Catalog first (preserves publication order).
        catalogEntries.forEach { e ->
            byId[e.id] = RegionRow(
                id = e.id, name = e.name, sizeBytes = e.sizeBytes,
                version = e.version, installState = RegionInstallState.NotInstalled, entry = e,
            )
        }
        // Local overrides installState and fills any region not in the catalog.
        local.forEach { entity ->
            val catalogEntry = catalogEntries.firstOrNull { it.id == entity.id }
            val merged = (byId[entity.id] ?: RegionRow(
                id = entity.id, name = entity.name, sizeBytes = entity.sizeBytes,
                version = entity.version, installState = entity.installState, entry = null,
            )).copy(installState = entity.installState)
            byId[entity.id] = merged
        }
        return byId.values.toList()
    }
}
