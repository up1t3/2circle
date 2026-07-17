package com.twocircle.bike.feature.routing.screen

import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.domain.model.Route
import com.twocircle.bike.domain.model.RoutingProfile
import com.twocircle.bike.domain.model.Waypoint

/**
 * Route Builder UI state.
 *
 * - [Idle] — empty plan; prompt to add waypoints.
 * - [Draft] — has ≥1 waypoint, not yet planned (route geometry not computed).
 * - [Planning] — engine call in flight.
 * - [Planned] — route computed; show preview.
 * - [Error] — typed failure.
 *
 * The [profile] lives inside the state so changing it is a single mutation that
 * invalidates the planned route (caller must re-plan after a profile switch).
 */
sealed interface RouteBuilderUiState {
    val profile: RoutingProfile

    data object Idle : RouteBuilderUiState {
        override val profile: RoutingProfile get() = RoutingProfile.Touring
    }

    data class Draft(
        val waypoints: List<Waypoint>,
        override val profile: RoutingProfile,
    ) : RouteBuilderUiState {
        init { require(waypoints.isNotEmpty()) }
    }

    data class Planning(
        val waypoints: List<Waypoint>,
        override val profile: RoutingProfile,
    ) : RouteBuilderUiState

    data class Planned(
        val waypoints: List<Waypoint>,
        val route: Route,
        override val profile: RoutingProfile,
    ) : RouteBuilderUiState {
        init { require(route.waypoints == waypoints) }
    }

    data class Error(
        val waypoints: List<Waypoint>,
        val failure: Failure,
        override val profile: RoutingProfile,
    ) : RouteBuilderUiState
}
