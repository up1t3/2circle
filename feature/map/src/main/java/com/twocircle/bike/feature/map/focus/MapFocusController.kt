package com.twocircle.bike.feature.map.focus

import com.twocircle.bike.domain.model.Coord
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton bridge that lets the Search screen tell the Map screen to fly to a location.
 *
 * Why SharedFlow and not StateFlow: we want each search-result tap to fire a distinct
 * event — even if the user taps the same result twice, the camera should animate both
 * times. StateFlow de-duplicates consecutive equal values, which would silently eat
 * the second tap. SharedFlow with replay=0 delivers each emission exactly once to
 * whichever collector is active (MapScreen's LaunchedEffect).
 *
 * The "pending focus" is consumed by [MapScreen], which calls
 * `map.animateCamera(CameraUpdateFactory.newLatLngZoom(...))` and drops a temporary
 * marker. This closes the critical UX gap where tapping a search result just closed
 * the search screen without moving the map — the user's #1 complaint.
 */
@Singleton
class MapFocusController @Inject constructor() {

    private val _focusTarget = MutableSharedFlow<FocusTarget>(replay = 1, extraBufferCapacity = 1)
    val focusTarget: SharedFlow<FocusTarget> = _focusTarget.asSharedFlow()

    /** Request the map to fly to [lat]/[lon] and optionally show a marker with [name]. */
    fun focusOn(lat: Double, lon: Double, name: String? = null) {
        timber.log.Timber.d("MapFocusController: focusOn lat=%f lon=%f name=%s", lat, lon, name)
        _focusTarget.tryEmit(FocusTarget(lat = lat, lon = lon, name = name))
    }

    data class FocusTarget(
        val lat: Double,
        val lon: Double,
        val name: String?,
    )
}
