package com.twocircle.bike.feature.map.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.data.db.entity.RegionInstallState
import com.twocircle.bike.data.repository.RegionsRepository
import com.twocircle.bike.data.filesystem.RegionAssets
import com.twocircle.bike.domain.PlannedRouteHolder
import com.twocircle.bike.domain.RouteDraftMutator
import com.twocircle.bike.domain.RoutePlanner
import com.twocircle.bike.domain.usecase.ReverseGeocode
import com.twocircle.bike.feature.map.navigation.NavigationController
import com.twocircle.bike.feature.map.style.MapStyleProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

/**
 * Map screen view model.
 *
 * Reactively observes installed regions — when a region download completes (in the
 * Regions tab), the map screen picks it up automatically without needing a manual
 * refresh. This was a bug in the original one-shot implementation that required
 * restarting the app after downloading.
 *
 * Also exposes the [PlannedRouteHolder] (for the route polyline overlay + the
 * "start navigation" affordance), [NavigationController] (turn-by-turn engine), and
 * [ReverseGeocode] (resolves names for tapped waypoints) — all @Singleton dependencies
 * threaded through the VM so the Composable layer doesn't need its own Hilt entry point.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val regionsRepository: RegionsRepository,
    private val regionAssets: RegionAssets,
    @ApplicationContext private val appContext: Context,
    val trackOverlay: com.twocircle.bike.domain.TrackOverlay,
    val plannedRouteHolder: PlannedRouteHolder,
    val routeDraft: RouteDraftMutator,
    val routePlanner: RoutePlanner,
    val navigationController: NavigationController,
    val reverseGeocode: ReverseGeocode,
) : ViewModel() {

    private val _state = MutableStateFlow<MapUiState>(MapUiState.Loading)
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    init {
        // Reactively observe installed regions. When the DB changes (download completes,
        // region deleted), we re-resolve the active region automatically.
        regionsRepository.allFlow()
            .onEach { _ -> resolveActiveRegion() }
            .launchIn(viewModelScope)
    }

    private fun resolveActiveRegion() {
        viewModelScope.launch {
            val firstInstalled = regionsRepository.activeRegionOrNull()
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
            Timber.d("MapViewModel: mbtiles path=%s, exists=%b, size=%d",
                path,
                regionAssets.mbtilesPath(firstInstalled).exists(),
                regionAssets.mbtilesPath(firstInstalled).length(),
            )
            // Resolve the locale from the *app-level* language setting (SettingsScreen
            // switches it via AppCompatDelegate.setApplicationLocales), NOT the device's
            // system locale — otherwise a device on English system language shows OSM
            // `name:en` (transliterated) even when the user picked Russian in app settings.
            val locale = resolveAppLocale(appContext)
            val styleJson = MapStyleProvider.buildStyleJson(path, locale = locale)
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

    /** Rebuild style JSON when user or system changes theme. */
    fun rebuildStyle(isDark: Boolean) {
        viewModelScope.launch {
            val firstInstalled = regionsRepository.activeRegionOrNull() ?: return@launch
            if (!regionAssets.hasTiles(firstInstalled)) return@launch
            val path = regionAssets.mbtilesPath(firstInstalled).absolutePath
            val locale = resolveAppLocale(appContext)
            val styleJson = MapStyleProvider.buildStyleJson(path, locale = locale, isDark = isDark)
            val currentReady = _state.value as? MapUiState.Ready
            if (currentReady != null) {
                _state.value = currentReady.copy(styleJson = styleJson)
            }
        }
    }
}

/**
 * Resolve the user's chosen app language for OSM name-tag selection.
 *
 * Prefers the app-level locale override set from SettingsScreen
 * (AppCompatDelegate.setApplicationLocales) over the device system locale, so the map
 * labels honour the in-app language picker. On Android 13+ the override is synced to
 * the platform LocaleManager and surfaces in `configuration.locales` already; on older
 * versions we read the persisted override directly via `AppCompatDelegate`.
 *
 * Returns "ru"/"en"/"es" etc. matching OSM's `name:<lang>` tag convention.
 */
private fun resolveAppLocale(context: Context): Locale {
    val appLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
    if (!appLocales.isEmpty) {
        appLocales[0]?.let { return it }
    }
    // Empty list == "follow system": defer to whatever the device reports.
    return context.resources.configuration.locales[0]
}

