package com.twocircle.bike.feature.poi.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twocircle.bike.feature.poi.model.Poi
import com.twocircle.bike.feature.poi.model.PoiCategory
import com.twocircle.bike.feature.poi.repository.PoiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the POI bottom sheet + map markers.
 *
 * - [selectedCategories] is the user's chip selection; toggling chips re-queries.
 * - [bbox] is the last map-bounds snapshot the map screen pushed in via [setBounds].
 *   We re-query whenever either chips or bbox change.
 * - [visible] toggles whether the markers show on the map at all (sheet can be open
 *   with markers hidden while the user is browsing categories).
 */
data class PoiUiState(
    val selectedCategories: Set<PoiCategory> = emptySet(),
    val bbox: BoundingBox? = null,
    val pois: List<Poi> = emptyList(),
    val loading: Boolean = false,
    val visible: Boolean = false,
    /** POI selected by tapping its marker on the map. Drives the detail sheet. */
    val selectedPoi: Poi? = null,
) {
    /** Map-camera bounds in WGS84 degrees. */
    data class BoundingBox(
        val minLat: Double, val minLon: Double,
        val maxLat: Double, val maxLon: Double,
    )
}

/**
 * Drives the POI bottom sheet and the map markers.
 *
 * Reactively re-queries when either the chip selection or the map bounds change — the
 * map screen pushes bounds via [setBounds] on camera idle, and the bottom sheet pushes
 * chip toggles via [toggleCategory]. The combined state is exposed as [state]; the map
 * layer subscribes to render markers.
 */
@HiltViewModel
class PoiViewModel @Inject constructor(
    private val repository: PoiRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PoiUiState())
    val state: StateFlow<PoiUiState> = _state.asStateFlow()

    fun toggleCategory(category: PoiCategory) {
        _state.update { s ->
            val newCats = if (category in s.selectedCategories) {
                s.selectedCategories - category
            } else {
                s.selectedCategories + category
            }
            s.copy(selectedCategories = newCats, visible = newCats.isNotEmpty() || s.visible)
        }
        refresh()
    }

    fun setVisible(visible: Boolean) {
        _state.update { it.copy(visible = visible) }
    }

    /** Called when the user taps a POI marker on the map. Drives the detail sheet. */
    fun selectPoi(poi: Poi) {
        _state.update { it.copy(selectedPoi = poi) }
    }

    /** Called when the detail sheet is dismissed. */
    fun clearSelection() {
        _state.update { it.copy(selectedPoi = null) }
    }

    /** Push the map's current visible bounds. Triggers a re-query if categories are active. */
    fun setBounds(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double) {
        val newBbox = PoiUiState.BoundingBox(minLat, minLon, maxLat, maxLon)
        if (_state.value.bbox == newBbox) return
        _state.value = _state.value.copy(bbox = newBbox)
        refresh()
    }

    private fun refresh() {
        val s = _state.value
        val bbox = s.bbox ?: return
        if (s.selectedCategories.isEmpty()) {
            _state.value = s.copy(pois = emptyList())
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val pois = repository.getInBounds(
                categories = s.selectedCategories,
                minLat = bbox.minLat, minLon = bbox.minLon,
                maxLat = bbox.maxLat, maxLon = bbox.maxLon,
            )
            _state.value = _state.value.copy(pois = pois, loading = false)
        }
    }
}
