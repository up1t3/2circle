package com.twocircle.bike.feature.map.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twocircle.bike.feature.map.location.LatLon
import com.twocircle.bike.feature.map.location.MyLocationController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the "my location" FAB on the map screen.
 *
 * Tracks the latest known position ([location]) and whether follow-mode is on. When
 * follow-mode is active, the map screen re-centres the camera on every [location]
 * emission; turning it off (e.g. the rider pans the map manually) stops that.
 *
 * [resolve] is a one-shot: ask FusedLocationProvider for the current best fix, push it
 * into [location], and (when [follow] is true) let the map subscriber do the rest.
 */
@HiltViewModel
class MyLocationViewModel @Inject constructor(
    private val controller: MyLocationController,
) : ViewModel() {

    private val _location = MutableStateFlow<LatLon?>(null)
    val location: StateFlow<LatLon?> = _location.asStateFlow()

    private val _following = MutableStateFlow(false)
    val following: StateFlow<Boolean> = _following.asStateFlow()

    /** One-shot location lookup. Updates [location]; safe to call repeatedly. */
    fun resolve() {
        viewModelScope.launch {
            val loc = controller.lastKnown() ?: return@launch
            _location.value = loc
        }
    }

    /**
     * Toggle follow mode. Turning it on also triggers an immediate [resolve] so the
     * camera jumps to the user right away rather than waiting for the next refresh.
     */
    fun toggleFollowing() {
        val newFollow = !_following.value
        _following.value = newFollow
        if (newFollow) resolve()
    }

    /** Stop following — called when the user manually pans the map. */
    fun stopFollowing() {
        _following.value = false
    }
}
