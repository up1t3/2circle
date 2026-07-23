package com.twocircle.bike.feature.routing.screen

import com.google.common.truth.Truth.assertThat
import com.twocircle.bike.common.outcome.Outcome
import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.Route
import com.twocircle.bike.domain.model.RoutePlanId
import com.twocircle.bike.domain.model.RoutingProfile
import com.twocircle.bike.domain.model.Waypoint
import com.twocircle.bike.feature.routing.engine.RoutingEngine
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RouteDraftRepositoryTest {

    /**
     * Fake engine that always succeeds with an empty route. Enough for draft-state tests
     * (the real engine is exercised in its own BRouter integration tests). Using a hand
     * stub instead of Mockito keeps the unit-test module dependency-free.
     */
    private val fakeEngine = object : RoutingEngine {
        override suspend fun route(
            waypoints: List<Waypoint>,
            profile: RoutingProfile,
            planId: RoutePlanId,
        ): Outcome<Route> = Outcome.Success(
            Route(
                id = planId,
                profile = profile,
                segments = emptyList(),
                waypoints = waypoints,
            ),
        )
    }

    private val repo = RouteDraftRepository(engine = fakeEngine)

    @Test
    fun `first added waypoint becomes Start`() {
        val id = repo.addWaypoint(Coord(50.0, 30.0), "A", Waypoint.Source.Manual)
        val wps = repo.waypoints.value
        assertThat(wps).hasSize(1)
        assertThat(wps[0].id).isEqualTo(id)
        assertThat(wps[0].role).isEqualTo(Waypoint.Role.Start)
    }

    @Test
    fun `second added waypoint becomes End and first stays Start`() {
        repo.addWaypoint(Coord(50.0, 30.0), "A", Waypoint.Source.Manual)
        repo.addWaypoint(Coord(51.0, 31.0), "B", Waypoint.Source.Manual)
        val wps = repo.waypoints.value
        assertThat(wps).hasSize(2)
        assertThat(wps[0].role).isEqualTo(Waypoint.Role.Start)
        assertThat(wps[1].role).isEqualTo(Waypoint.Role.End)
    }

    @Test
    fun `three waypoints assign Start Via End`() {
        repo.addWaypoint(Coord(50.0, 30.0), "A", Waypoint.Source.Manual)
        repo.addWaypoint(Coord(51.0, 31.0), "B", Waypoint.Source.Manual)
        repo.addWaypoint(Coord(52.0, 32.0), "C", Waypoint.Source.Manual)
        val wps = repo.waypoints.value
        assertThat(wps.map { it.role })
            .containsExactly(Waypoint.Role.Start, Waypoint.Role.Via, Waypoint.Role.End)
            .inOrder()
    }

    @Test
    fun `updateWaypointCoord moves the right waypoint and clears planned route`() {
        val id = repo.addWaypoint(Coord(50.0, 30.0), "A", Waypoint.Source.Manual)
        repo.publishPlannedRoute(stubRoute())
        assertThat(repo.plannedRoute.value).isNotNull()

        repo.updateWaypointCoord(id, Coord(55.0, 35.0))

        val moved = repo.waypoints.value.first { it.id == id }
        assertThat(moved.coord).isEqualTo(Coord(55.0, 35.0))
        // Dragging a waypoint invalidates the previously planned geometry.
        assertThat(repo.plannedRoute.value).isNull()
    }

    @Test
    fun `updateWaypointName sets the name without touching coords`() {
        val id = repo.addWaypoint(Coord(50.0, 30.0), null, Waypoint.Source.Manual)
        repo.updateWaypointName(id, "Resolved spring")
        val wp = repo.waypoints.value.first { it.id == id }
        assertThat(wp.name).isEqualTo("Resolved spring")
        assertThat(wp.coord).isEqualTo(Coord(50.0, 30.0))
    }

    @Test
    fun `updateWaypointName accepts null to clear the name`() {
        val id = repo.addWaypoint(Coord(50.0, 30.0), "A", Waypoint.Source.Manual)
        repo.updateWaypointName(id, null)
        assertThat(repo.waypoints.value.first { it.id == id }.name).isNull()
    }

    @Test
    fun `removeWaypoint renormalises roles`() {
        repo.addWaypoint(Coord(50.0, 30.0), "A", Waypoint.Source.Manual)
        val middleId = repo.addWaypoint(Coord(51.0, 31.0), "B", Waypoint.Source.Manual)
        repo.addWaypoint(Coord(52.0, 32.0), "C", Waypoint.Source.Manual)

        repo.removeWaypoint(middleId)

        val wps = repo.waypoints.value
        assertThat(wps).hasSize(2)
        // After removing the Via, the survivors must be re-anchored as Start/End.
        assertThat(wps[0].role).isEqualTo(Waypoint.Role.Start)
        assertThat(wps[1].role).isEqualTo(Waypoint.Role.End)
    }

    @Test
    fun `clear drops waypoints and planned route`() {
        repo.addWaypoint(Coord(50.0, 30.0), "A", Waypoint.Source.Manual)
        repo.publishPlannedRoute(stubRoute())

        repo.clear()

        assertThat(repo.waypoints.value).isEmpty()
        assertThat(repo.plannedRoute.value).isNull()
    }

    @Test
    fun `publishPlannedRoute and clearPlannedRoute toggle the holder`() {
        assertThat(repo.plannedRoute.value).isNull()
        repo.publishPlannedRoute(stubRoute())
        assertThat(repo.plannedRoute.value).isNotNull()
        repo.clearPlannedRoute()
        assertThat(repo.plannedRoute.value).isNull()
    }

    @Test
    fun `planRoute is a no-op with fewer than two waypoints`() {
        repo.addWaypoint(Coord(50.0, 30.0), "A", Waypoint.Source.Manual)
        repo.planRoute()
        // Give the (cancelled-before-started) coroutine a chance to not run.
        runBlocking { kotlinx.coroutines.delay(50) }
        assertThat(repo.plannedRoute.value).isNull()
    }

    @Test
    fun `planRoute publishes a route through the engine when waypoints suffice`() {
        repo.addWaypoint(Coord(50.0, 30.0), "A", Waypoint.Source.Manual)
        repo.addWaypoint(Coord(51.0, 31.0), "B", Waypoint.Source.Manual)

        repo.planRoute()
        // The repo plans on Dispatchers.IO; wait for it to settle.
        kotlinx.coroutines.runBlocking { kotlinx.coroutines.delay(200) }

        val planned = repo.plannedRoute.value
        assertThat(planned).isNotNull()
        assertThat(planned!!.waypoints).hasSize(2)
    }

    private fun stubRoute(): com.twocircle.bike.domain.model.Route =
        com.twocircle.bike.domain.model.Route(
            id = com.twocircle.bike.domain.model.RoutePlanId("test"),
            profile = com.twocircle.bike.domain.model.RoutingProfile.Touring,
            segments = emptyList(),
            waypoints = emptyList(),
        )
}
