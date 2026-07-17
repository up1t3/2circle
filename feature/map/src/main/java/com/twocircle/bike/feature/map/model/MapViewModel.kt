package com.twocircle.bike.feature.map.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.data.repository.RegionsRepository
import com.twocircle.bike.data.filesystem.RegionAssets
import com.twocircle.bike.feature.map.style.MapStyleProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Map screen view model.
 *
 * On init, resolves which region to render. The strategy is simple for v1: if there is
 * exactly one installed region, use it; if there are several, the user picks later (the
 * regions screen); if there are none, show the [MapUiState.NoRegion] prompt.
 *
 * Once a region is selected, the style JSON is built from its mbtiles path. That JSON
 * string is the entire handoff to the BikeMap Composable — no SDK references cross the
 * VM boundary, which keeps the VM testable without Robolectric.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val regionsRepository: RegionsRepository,
    private val regionAssets: RegionAssets,
) : ViewModel() {

    private val _state = MutableStateFlow<MapUiState>(MapUiState.Loading)
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    init {
        resolveActiveRegion()
    }

    /** Re-resolve the active region (e.g. after a download completes). */
    fun refresh() = resolveActiveRegion()

    private fun resolveActiveRegion() {
        viewModelScope.launch {
            _state.value = MapUiState.Loading
            // For v1 we snapshot the first installed region. Reactive filtering is added
            // when the regions screen ships (Step 8) and selects an explicit active region.
            val firstInstalled = collectFirstInstalled()
            if (firstInstalled == null) {
                _state.value = MapUiState.NoRegion
                return@launch
            }
            if (!regionAssets.hasTiles(firstInstalled)) {
                _state.value = MapUiState.Error(
                    Failure.Region.Corrupted(firstInstalled.id),
                )
                return@launch
            }
            val path = regionAssets.mbtilesPath(firstInstalled).absolutePath
            val styleJson = MapStyleProvider.buildStyleJson(path)
            val camera = MapCamera(
                lat = (firstInstalled.boundsMinLat + firstInstalled.boundsMaxLat) / 2.0,
                lon = (firstInstalled.boundsMinLon + firstInstalled.boundsMaxLon) / 2.0,
                zoom = 10.0,
            )
            _state.value = MapUiState.Ready(
                styleJson = styleJson,
                initialCamera = camera,
                regionName = firstInstalled.name,
            )
        }
    }

    private suspend fun collectFirstInstalled(): com.twocircle.bike.data.db.entity.RegionEntity? = try {
        regionsRepository.firstInstalledOrNull()
    } catch (e: Exception) {
        Timber.e(e, "Failed to collect regions")
        null
    }
}
