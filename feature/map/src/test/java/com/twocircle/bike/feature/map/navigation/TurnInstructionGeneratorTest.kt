package com.twocircle.bike.feature.map.navigation

import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.Route
import com.twocircle.bike.domain.model.RoutePlanId
import com.twocircle.bike.domain.model.RoutingProfile
import com.twocircle.bike.domain.model.Segment
import com.twocircle.bike.domain.model.Smoothness
import com.twocircle.bike.domain.model.Surface
import com.twocircle.bike.domain.model.Waypoint
import com.twocircle.bike.domain.model.WaypointId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnInstructionGeneratorTest {

    @Test
    fun `generateFromRoute generates DEPART and ARRIVE instructions`() {
        val wpStart = Waypoint(id = WaypointId("wp1"), coord = Coord(55.751244, 37.618423), name = "Start")
        val wpEnd = Waypoint(id = WaypointId("wp2"), coord = Coord(55.753000, 37.620000), name = "End")
        val points = listOf(
            Coord(55.751244, 37.618423),
            Coord(55.752000, 37.619000),
            Coord(55.753000, 37.620000),
        )
        val segment = Segment(
            from = wpStart,
            to = wpEnd,
            geometry = points,
            distanceMeters = 500.0,
            plannedSeconds = 120L,
            ascentMeters = 10.0,
            descentMeters = 5.0,
            surface = Surface.Asphalt,
            smoothness = Smoothness.Good,
        )
        val route = Route(
            id = RoutePlanId("test-route"),
            profile = RoutingProfile.Touring,
            segments = listOf(segment),
            waypoints = listOf(wpStart, wpEnd),
        )

        val instructions = TurnInstructionGenerator.generateFromRoute(route)

        assertTrue("Instructions list must not be empty", instructions.isNotEmpty())
        assertEquals(ManeuverType.DEPART, instructions.first().type)
        assertEquals(ManeuverType.ARRIVE, instructions.last().type)
    }

    @Test
    fun `generate detects right turn maneuver`() {
        val points = listOf(
            Coord(55.7500, 37.6100),
            Coord(55.7500, 37.6200), // Moving East
            Coord(55.7490, 37.6200), // Moving South
        )

        val instructions = TurnInstructionGenerator.generate(points)

        assertTrue(instructions.size >= 2)
        val turnManeuver = instructions.firstOrNull { it.type == ManeuverType.RIGHT || it.type == ManeuverType.SHARP_RIGHT }
        assertNotNull("Should detect right turn maneuver", turnManeuver)
    }
}
