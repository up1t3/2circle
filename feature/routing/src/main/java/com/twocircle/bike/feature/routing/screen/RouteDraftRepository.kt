package com.twocircle.bike.feature.routing.screen

import com.twocircle.bike.domain.PlannedRouteHolder
import com.twocircle.bike.domain.RouteDraftMutator
import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.Route
import com.twocircle.bike.domain.model.RoutingProfile
import com.twocircle.bike.domain.model.Waypoint
import com.twocircle.bike.domain.model.WaypointId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton holding the in-progress route draft (waypoints + profile + last planned route).
 *
 * **Why this exists:** [RouteBuilderViewModel] is scoped to the
 * `routes?addLat=…` NavBackStackEntry, which is destroyed on every
 * `popUpTo(map)` navigation. That means every "To route" tap created
 * a new VM with an empty list, wiping previous waypoints. Moving the
 * waypoint list into a `@Singleton` decouples its lifetime from
 * navigation — waypoints accumulate across taps, survive back-stack
 * pops, and persist until the user explicitly clears or plans.
 *
 * The repository is the single source of truth for the draft; the
 * ViewModel is a thin observer/mutator over it. "To route" / long-press
 * / POI "Add to route" all call [addWaypoint] here, without navigating
 * away from the current screen.
 *
 * Implements [PlannedRouteHolder] so :feature:map can render the planned route's
 * polyline without depending on :feature:routing — bound in [RoutingModule].
 */
@Singleton
class RouteDraftRepository @Inject constructor() : PlannedRouteHolder, RouteDraftMutator {

    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    override val waypoints: StateFlow<List<Waypoint>> = _waypoints.asStateFlow()

    private val _profile = MutableStateFlow(RoutingProfile.Touring)
    val profile: StateFlow<RoutingProfile> = _profile.asStateFlow()

    private val _plannedRoute = MutableStateFlow<Route?>(null)
    override val plannedRoute: StateFlow<Route?> = _plannedRoute.asStateFlow()

    override fun publishPlannedRoute(route: Route) {
        _plannedRoute.value = route
    }

    override fun clearPlannedRoute() {
        _plannedRoute.value = null
    }

    /** Add a waypoint to the end of the draft list. Role is auto-assigned. Returns the new id. */
    override fun addWaypoint(coord: Coord, name: String?, source: Waypoint.Source): WaypointId {
        val current = _waypoints.value
        val role = if (current.isEmpty()) Waypoint.Role.Start else Waypoint.Role.Via
        val newWp = Waypoint(
            id = WaypointId(UUID.randomUUID().toString()),
            coord = coord,
            name = name,
            role = role,
            source = source,
        )
        _waypoints.value = normaliseRoles(current + newWp)
        return newWp.id
    }

    /** Remove a waypoint by id; remaining roles are renormalised. */
    override fun removeWaypoint(id: WaypointId) {
        _waypoints.value = normaliseRoles(_waypoints.value.filterNot { it.id == id })
    }

    /** Reorder: move waypoint at [fromIndex] to [toIndex]. */
    fun moveWaypoint(fromIndex: Int, toIndex: Int) {
        val current = _waypoints.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val moved = current.removeAt(fromIndex)
        current.add(toIndex, moved)
        _waypoints.value = normaliseRoles(current)
    }

    /** Replace the coordinates of an existing waypoint (e.g. after a drag-to-move). */
    override fun updateWaypointCoord(id: WaypointId, coord: Coord) {
        _waypoints.value = _waypoints.value.map { wp ->
            if (wp.id == id) wp.copy(coord = coord) else wp
        }
        // Invalidate any previously planned route — geometry no longer matches the draft.
        clearPlannedRoute()
    }

    /** Replace the name of an existing waypoint (e.g. after offline reverse-geocode resolves it). */
    override fun updateWaypointName(id: WaypointId, name: String?) {
        _waypoints.value = _waypoints.value.map { wp ->
            if (wp.id == id) wp.copy(name = name) else wp
        }
    }

    /** Switch routing profile. */
    fun setProfile(profile: RoutingProfile) {
        _profile.value = profile
    }

    /** Clear all waypoints (e.g. after a successful plan or explicit "clear"). */
    fun clear() {
        _waypoints.value = emptyList()
        clearPlannedRoute()
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

