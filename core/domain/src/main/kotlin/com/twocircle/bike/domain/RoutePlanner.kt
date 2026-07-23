package com.twocircle.bike.domain

/**
 * Triggers a route (re)plan over the current draft waypoints.
 *
 * Lives in :core:domain so :feature:map can request auto-planning when waypoints change
 * without depending on :feature:routing (where the BRouter engine lives). The concrete
 * implementation is [com.twocircle.bike.feature.routing.screen.RouteBuilderViewModel]-backed
 * and bound via Hilt — same module-boundary pattern as [PlannedRouteHolder] /
 * [NavigationSink] / [RouteDraftMutator].
 *
 * The planner reads the draft waypoints from the shared [RouteDraftMutator] singleton, so
 * the caller doesn't pass them in — it just says "plan now". Results surface through
 * [PlannedRouteHolder.plannedRoute] (the polyline layer observes that).
 */
interface RoutePlanner {
    /**
     * Build a route through the current draft waypoints. No-op if fewer than 2 waypoints.
     * Safe to call repeatedly; the engine deduplicates / cancels stale work as needed.
     */
    fun planRoute()
}
