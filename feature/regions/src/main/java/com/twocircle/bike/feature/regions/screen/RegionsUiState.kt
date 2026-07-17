package com.twocircle.bike.feature.regions.screen

import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.data.db.entity.RegionInstallState
import com.twocircle.bike.feature.regions.download.DownloadProgress
import com.twocircle.bike.feature.regions.manifest.RegionEntry

/**
 * One row in the regions list — joins the catalog entry with the local install state.
 *
 * The catalog (what's available to download) and the local DB (what's installed) are
 * separate sources; this type is the merged projection the UI renders. A region present
 * in the catalog but not locally has [installState] = NotInstalled; a region installed
 * locally but somehow missing from the catalog (rare, but possible after a backend
 * cleanup) still shows up via the local row.
 */
data class RegionRow(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val version: Int,
    val installState: RegionInstallState,
    val entry: RegionEntry?, // null when only local, no catalog match
)

sealed interface RegionsUiState {
    data object Loading : RegionsUiState
    data object NoRegion : RegionsUiState
    data class Loaded(val rows: List<RegionRow>, val progress: Map<String, DownloadProgress>) : RegionsUiState
    data class Error(val failure: Failure) : RegionsUiState
}
