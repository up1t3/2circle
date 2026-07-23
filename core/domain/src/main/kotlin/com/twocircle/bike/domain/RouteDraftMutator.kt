package com.twocircle.bike.domain

import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.Waypoint
import com.twocircle.bike.domain.model.WaypointId
import kotlinx.coroutines.flow.StateFlow

/**
 * Read + mutate contract for the in-progress route draft.
 *
 * Lives in :core:domain so :feature:map can add/move/rename waypoints (long-press,
 * tap-to-place, drag, POI "add to route") without depending on :feature:routing. The
 * concrete implementation is [com.twocircle.bike.feature.routing.screen.RouteDraftRepository],
 * bound via Hilt — mirroring the [TrackOverlay] / [PlannedRouteHolder] pattern.
 *
 * All mutators are synchronous and main-safe: the repository is an in-memory
 * `MutableStateFlow`, so writes are O(1) and immediately observable via [waypoints].
 */
interface RouteDraftMutator {
    /** Current draft waypoints; the first is Start, the last is End, the rest are Via. */
    val waypoints: StateFlow<List<Waypoint>>

    /**
     * Append a waypoint (manual / search / gps source). Role is auto-assigned.
     * Returns the new waypoint's id so callers can later [updateWaypointName] after a
     * background reverse-geocode resolves it.
     */
    fun addWaypoint(coord: Coord, name: String?, source: Waypoint.Source): WaypointId

    /** Replace an existing waypoint's coordinates (e.g. after a drag-to-move). */
    fun updateWaypointCoord(id: WaypointId, coord: Coord)

    /** Replace an existing waypoint's name (e.g. after offline reverse-geocode). */
    fun updateWaypointName(id: WaypointId, name: String?)

    /** Remove a waypoint by id; remaining roles are renormalised. */
    fun removeWaypoint(id: WaypointId)
}
