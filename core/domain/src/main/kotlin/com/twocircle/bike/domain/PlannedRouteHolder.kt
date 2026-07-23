package com.twocircle.bike.domain

import com.twocircle.bike.domain.model.Route
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only contract for the most recently planned route, consumed by the map renderer.
 *
 * Lives in :core:domain so :feature:map (which renders the polyline) can observe the
 * planned route without depending on :feature:routing (which produces it). The concrete
 * implementation is [com.twocircle.bike.feature.routing.screen.RouteDraftRepository],
 * bound via Hilt — mirroring the [TrackOverlay] pattern.
 *
 * The map subscribes to [plannedRoute] and redraws the route line whenever a new plan
 * arrives. Emits `null` when no route has been planned or after [clearPlannedRoute].
 *
 * Why a holder interface instead of passing `Route` through nav arguments: the draft
 * repository is a `@Singleton` that already survives navigation, and Route geometry can
 * be large (thousands of points). A StateFlow avoids serialising it through a Bundle.
 */
interface PlannedRouteHolder {
    /** The most recently planned route, or `null` if none/cleared. */
    val plannedRoute: StateFlow<Route?>

    /** Replace the current planned route. Called by the routing VM after a successful plan. */
    fun publishPlannedRoute(route: Route)

    /** Drop the current planned route (hides the polyline and the "start navigation" affordance). */
    fun clearPlannedRoute()
}
