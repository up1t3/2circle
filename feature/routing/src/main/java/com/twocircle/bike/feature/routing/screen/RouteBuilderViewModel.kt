package com.twocircle.bike.feature.routing.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twocircle.bike.common.outcome.onFailure
import com.twocircle.bike.common.outcome.onSuccess
import com.twocircle.bike.data.db.entity.RoutePlanEntity
import com.twocircle.bike.data.db.entity.SegmentEntity
import com.twocircle.bike.data.db.entity.WaypointEntity
import com.twocircle.bike.data.repository.RoutePlansRepository
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
 * Thin observer/mutator over [RouteDraftRepository] — the repository is a
 * `@Singleton` that survives navigation, so waypoints accumulate across
 * "To route" taps, long-press, and POI "Add to route" without being lost.
 *
 * The VM owns the *planning* state machine (Idle → Draft → Planning →
 * Planned/Error) but NOT the waypoint list itself — that lives in the
 * repository. This split fixes the critical bug where every navigation
 * to `routes?addLat=…` destroyed the previous VM (and its waypoints).
 *
 * Waypoint sources: [addWaypointManual] (long-press), [addWaypointSearch]
 * (search hit), [addWaypointGps] (current location). All delegate to the
 * shared repository so callers from other screens (Search, Map, POI) can
 * add waypoints without navigating to the Route Builder at all.
 */
@HiltViewModel
class RouteBuilderViewModel @Inject constructor(
    private val draftRepository: RouteDraftRepository,
    private val engine: RoutingEngine,
    private val plansRepository: RoutePlansRepository,
) : ViewModel() {

    /** Draft waypoints from the shared repository — survives navigation. */
    val waypoints: StateFlow<List<Waypoint>> = draftRepository.waypoints

    /** Routing profile from the shared repository. */
    val profile: StateFlow<RoutingProfile> = draftRepository.profile

    /** Planning state — VM-local, transient. */
    private val _planState = MutableStateFlow<PlanState>(PlanState.Idle)
    val planState: StateFlow<PlanState> = _planState.asStateFlow()

    /** Add a manual (long-press) waypoint via the shared repository. */
    fun addWaypointManual(coord: Coord, name: String? = null) =
        draftRepository.addWaypoint(coord, name, Waypoint.Source.Manual)

    /** Add a search-result waypoint via the shared repository. */
    fun addWaypointSearch(coord: Coord, name: String?) =
        draftRepository.addWaypoint(coord, name, Waypoint.Source.Search)

    /** Add the current GPS position as a waypoint via the shared repository. */
    fun addWaypointGps(coord: Coord) =
        draftRepository.addWaypoint(coord, null, Waypoint.Source.Gps)

    /** Удалить путевую точку по ID. */
    fun removeWaypoint(id: WaypointId) = draftRepository.removeWaypoint(id)

    /** Переместить путевую точку. */
    fun moveWaypoint(fromIndex: Int, toIndex: Int) = draftRepository.moveWaypoint(fromIndex, toIndex)

    /** Изменить профиль маршрутизации. */
    fun setProfile(profile: RoutingProfile) = draftRepository.setProfile(profile)
    /** Trigger a (re)plan via the routing engine. */
    fun planRoute() {
        val wps = waypoints.value
        if (wps.size < 2) return
        val activeProfile = profile.value
        _planState.value = PlanState.Planning
        viewModelScope.launch {
            val planId = RoutePlanId(UUID.randomUUID().toString())
            engine.route(wps, activeProfile, planId)
                .onSuccess { route ->
                    _planState.value = PlanState.Planned(route)
                }
                .onFailure { failure ->
                    Timber.w("Routing failed: $failure")
                    _planState.value = PlanState.Error(failure)
                }
        }
    }

    /** Clear the planning state (e.g. after dismissing an error). */
    fun clearPlanState() {
        _planState.value = PlanState.Idle
    }

    /**
     * Persist the current draft + planned route to the Room database.
     *
     * Called from the "Save route" button on the Route Builder screen. Saves the plan
     * metadata, waypoints, and segment geometry so the route survives app restart and
     * can be re-loaded later. Without this the entire route_plans/waypoints/route_segments
     * schema is dead code — written nowhere, read nowhere.
     */
    fun saveCurrentRoute() {
        val wps = waypoints.value
        if (wps.size < 2) return
        val activeProfile = profile.value
        val route = (_planState.value as? PlanState.Planned)?.route

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val planId = UUID.randomUUID().toString()
            val name = wps.firstOrNull()?.name?.let { first ->
                wps.lastOrNull()?.name?.let { last -> "$first → $last" }
            } ?: "Route ${now}"

            val plan = RoutePlanEntity(
                id = planId,
                name = name,
                profile = activeProfile.brouterProfileFile,
                createdAtMs = now,
                updatedAtMs = now,
                lastComputedAtMs = if (route != null) now else null,
                distanceMeters = route?.distanceMeters ?: 0.0,
                plannedSeconds = route?.plannedSeconds ?: 0L,
                ascentMeters = route?.ascentMeters ?: 0.0,
            )
            plansRepository.savePlan(plan)

            val waypointEntities = wps.mapIndexed { i, wp ->
                WaypointEntity(
                    routePlanId = planId,
                    orderIdx = i,
                    lat = wp.coord.lat,
                    lon = wp.coord.lon,
                    name = wp.name,
                    role = wp.role.name,
                    source = wp.source.name,
                )
            }
            plansRepository.replaceWaypoints(planId, waypointEntities)

            if (route != null) {
                val segmentEntities = route.segments.mapIndexed { i, seg ->
                    SegmentEntity(
                        routePlanId = planId,
                        orderIdx = i,
                        fromLat = seg.from.coord.lat,
                        fromLon = seg.from.coord.lon,
                        fromName = seg.from.name,
                        toLat = seg.to.coord.lat,
                        toLon = seg.to.coord.lon,
                        toName = seg.to.name,
                        geometryJson = seg.geometry.joinToString(",", "[", "]") { c ->
                            "[${c.lon},${c.lat}${c.ele?.let { ",$it" } ?: ""}]"
                        },
                        distanceMeters = seg.distanceMeters,
                        plannedSeconds = seg.plannedSeconds,
                        ascentMeters = seg.ascentMeters,
                        descentMeters = seg.descentMeters,
                        surface = seg.surface.name,
                        smoothness = seg.smoothness.name,
                    )
                }
                plansRepository.replaceSegments(planId, segmentEntities)
            }

            Timber.i("Route saved: %s (%s)", planId, name)
        }
    }
}

/**
 * Transient planning state — separate from the draft waypoints (which
 * live in [RouteDraftRepository] and persist across navigation).
 */
sealed interface PlanState {
    data object Idle : PlanState
    data object Planning : PlanState
    data class Planned(val route: com.twocircle.bike.domain.model.Route) : PlanState
    data class Error(val failure: com.twocircle.bike.common.outcome.Failure) : PlanState
}
