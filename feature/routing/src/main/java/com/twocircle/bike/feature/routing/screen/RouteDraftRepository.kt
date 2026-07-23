package com.twocircle.bike.feature.routing.screen

import com.twocircle.bike.common.outcome.onFailure
import com.twocircle.bike.common.outcome.onSuccess
import com.twocircle.bike.domain.PlannedRouteHolder
import com.twocircle.bike.domain.RouteDraftMutator
import com.twocircle.bike.domain.RoutePlanner
import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.Route
import com.twocircle.bike.domain.model.RoutePlanId
import com.twocircle.bike.domain.model.RoutingProfile
import com.twocircle.bike.domain.model.Waypoint
import com.twocircle.bike.domain.model.WaypointId
import com.twocircle.bike.feature.routing.engine.RoutingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
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
 * Implements [PlannedRouteHolder] (so :feature:map can render the planned route's
 * polyline), [RouteDraftMutator] (add/move/rename waypoints), and [RoutePlanner]
 * (auto-plan when waypoints change) — all without :feature:map depending on
 * :feature:routing. Bound in [RoutingModule].
 */
@Singleton
class RouteDraftRepository @Inject constructor(
    private val engine: RoutingEngine,
) : PlannedRouteHolder, RouteDraftMutator, RoutePlanner {

    // Application-scoped coroutines: the repo is a singleton, so this scope lives for the
    // whole process. Used for background routing; the previous in-flight plan is cancelled
    // when a new one starts (debounce-friendly for rapid waypoint edits).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var planJob: Job? = null

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

    /**
     * Plan a route through the current draft waypoints via the routing engine.
     *
     * Implements [RoutePlanner] — called from :feature:map (auto-plan after ≥2 waypoints)
     * and :feature:routing's RouteBuilderViewModel (manual "Plan" button). Cancels any
     * previous in-flight plan first so rapid waypoint edits don't queue stale work.
     * On success the resulting [Route] is published via [publishPlannedRoute], which the
     * map's [com.twocircle.bike.feature.map.view.RouteOverlayLayer] observes.
     */
    override fun planRoute() {
        val wps = _waypoints.value
        if (wps.size < 2) return
        val activeProfile = _profile.value
        planJob?.cancel()
        planJob = scope.launch {
            val planId = RoutePlanId(UUID.randomUUID().toString())
            engine.route(wps, activeProfile, planId)
                .onSuccess { route ->
                    publishPlannedRoute(route)
                    Timber.i("Auto-plan ok: %d waypoints, %.1f km", wps.size, route.distanceMeters / 1000)
                }
                .onFailure { failure ->
                    Timber.w("Auto-plan failed: %s", failure)
                    // Keep the last successful route visible — a failed re-plan shouldn't
                    // blank the line the user is looking at.
                }
        }
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

