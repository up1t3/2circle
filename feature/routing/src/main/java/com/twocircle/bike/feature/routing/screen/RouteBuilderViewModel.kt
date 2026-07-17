package com.twocircle.bike.feature.routing.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.common.outcome.onFailure
import com.twocircle.bike.common.outcome.onSuccess
import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.RoutePlanId
import com.twocircle.bike.domain.model.RoutingProfile
import com.twocircle.bike.domain.model.Waypoint
import com.twocircle.bike.domain.model.WaypointId
import com.twocircle.bike.feature.routing.engine.RoutingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Route Builder view model.
 *
 * Manages an ordered waypoint list and triggers the routing engine when ≥2 waypoints
 * are present. Re-plans are explicit (caller invokes [planRoute]) — we never auto-plan
 * on every keystroke, because cloud routing calls cost real money/time and the rider
 * often adjusts several waypoints before wanting a fresh route.
 *
 * Waypoint sources: [addWaypointManual] (long-press), [addWaypointSearch] (search hit),
 * [addWaypointGps] (current location). GPX import is added in Step 7 (tracks feature).
 */
@HiltViewModel
class RouteBuilderViewModel @Inject constructor(
    private val engine: RoutingEngine,
) : ViewModel() {

    private val _state = MutableStateFlow<RouteBuilderUiState>(RouteBuilderUiState.Idle)
    val state: StateFlow<RouteBuilderUiState> = _state.asStateFlow()

    /** Current waypoints (empty list when in Idle state). */
    val waypoints: List<Waypoint>
        get() = when (val s = _state.value) {
            is RouteBuilderUiState.Draft -> s.waypoints
            is RouteBuilderUiState.Planning -> s.waypoints
            is RouteBuilderUiState.Planned -> s.waypoints
            is RouteBuilderUiState.Error -> s.waypoints
            RouteBuilderUiState.Idle -> emptyList()
        }

    val profile: RoutingProfile
        get() = _state.value.profile

    /** Add a manual (long-press) waypoint. */
    fun addWaypointManual(coord: Coord, name: String? = null) =
        addWaypoint(coord, name, Waypoint.Source.Manual)

    /** Add a search-result waypoint. */
    fun addWaypointSearch(coord: Coord, name: String?) =
        addWaypoint(coord, name, Waypoint.Source.Search)

    /** Add the current GPS position as a waypoint. */
    fun addWaypointGps(coord: Coord) =
        addWaypoint(coord, null, Waypoint.Source.Gps)

    private fun addWaypoint(coord: Coord, name: String?, source: Waypoint.Source) {
        val current = waypoints
        val role = when {
            current.isEmpty() -> Waypoint.Role.Start
            else -> Waypoint.Role.Via
        }
        val newWp = Waypoint(
            id = WaypointId(UUID.randomUUID().toString()),
            coord = coord,
            name = name,
            role = role,
            source = source,
        )
        val updated = current + newWp
        // Re-stamp the last as End if we now have ≥2 (keeps roles tidy).
        val normalised = normaliseRoles(updated)
        transitionToDraft(normalised)
    }

    /** Remove a waypoint by id; remaining roles are renormalised. */
    fun removeWaypoint(id: WaypointId) {
        val updated = waypoints.filterNot { it.id == id }
        if (updated.isEmpty()) {
            _state.value = RouteBuilderUiState.Idle
        } else {
            transitionToDraft(normaliseRoles(updated))
        }
    }

    /** Reorder: move waypoint at [fromIndex] to [toIndex]. */
    fun moveWaypoint(fromIndex: Int, toIndex: Int) {
        val current = waypoints.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val moved = current.removeAt(fromIndex)
        current.add(toIndex, moved)
        transitionToDraft(normaliseRoles(current))
    }

    /** Switch routing profile. Invalidates any planned route (must re-plan). */
    fun setProfile(profile: RoutingProfile) {
        val wps = waypoints
        if (wps.isEmpty()) {
            // No waypoints: nothing to do (profile is on state, but Idle uses default).
            return
        }
        transitionToDraft(wps, profile)
    }

    /** Trigger a (re)plan via the routing engine. */
    fun planRoute() {
        val wps = waypoints
        if (wps.size < 2) return
        val activeProfile = profile
        _state.value = RouteBuilderUiState.Planning(wps, activeProfile)
        viewModelScope.launch {
            val planId = RoutePlanId(UUID.randomUUID().toString())
            engine.route(wps, activeProfile, planId)
                .onSuccess { route ->
                    _state.value = RouteBuilderUiState.Planned(wps, route, activeProfile)
                }
                .onFailure { failure ->
                    Timber.w("Routing failed: $failure")
                    _state.value = RouteBuilderUiState.Error(wps, failure, activeProfile)
                }
        }
    }

    private fun transitionToDraft(waypoints: List<Waypoint>, profile: RoutingProfile = this.profile) {
        _state.value = if (waypoints.isEmpty()) {
            RouteBuilderUiState.Idle
        } else {
            RouteBuilderUiState.Draft(waypoints, profile)
        }
    }

    /** Ensure the first waypoint is Start, the last is End, the rest are Via. */
    private fun normaliseRoles(waypoints: List<Waypoint>): List<Waypoint> {
        if (waypoints.isEmpty()) return waypoints
        return waypoints.mapIndexed { i, wp ->
            val role = when (i) {
                0 -> Waypoint.Role.Start
                waypoints.lastIndex -> Waypoint.Role.End
                else -> Waypoint.Role.Via
            }
            if (wp.role == role) wp else wp.copy(role = role)
        }
    }
}
