package com.twocircle.bike.feature.routing

import com.google.common.truth.Truth.assertThat
import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.RoutePlanId
import com.twocircle.bike.domain.model.RoutingProfile
import com.twocircle.bike.domain.model.Surface
import com.twocircle.bike.domain.model.Waypoint
import com.twocircle.bike.domain.model.WaypointId
import com.twocircle.bike.feature.routing.api.BRouterFeature
import com.twocircle.bike.feature.routing.api.BRouterGeometry
import com.twocircle.bike.feature.routing.api.BRouterProperties
import com.twocircle.bike.feature.routing.api.BRouterResponse
import com.twocircle.bike.feature.routing.mapper.RouteMapper
import org.junit.Test

class RouteMapperTest {

    private val planId = RoutePlanId("plan-1")
    private val profile = RoutingProfile.Touring

    private val waypoints = listOf(
        Waypoint(
            id = WaypointId("a"), coord = Coord(47.211, 8.731),
            role = Waypoint.Role.Start,
        ),
        Waypoint(
            id = WaypointId("b"), coord = Coord(47.219, 8.744),
            role = Waypoint.Role.End,
        ),
    )

    @Test
    fun `maps a full response with properties`() {
        val resp = BRouterResponse(
            type = "FeatureCollection",
            features = listOf(
                BRouterFeature(
                    type = "Feature",
                    properties = BRouterProperties(
                        cost = 1820.0,                  // ~30 min
                        trackLength = 9500.0,           // 9.5 km
                        filteredAscend = 145.0,         // 145 m uphill
                    ),
                    geometry = BRouterGeometry(
                        type = "LineString",
                        // [lon, lat, ele?]
                        coordinates = listOf(
                            listOf(8.731, 47.211, 410.0),
                            listOf(8.738, 47.215, 480.0),
                            listOf(8.744, 47.219, 450.0),
                        ),
                    ),
                ),
            ),
        )

        val route = RouteMapper.map(resp, waypoints, planId, profile).getOrThrow()

        assertThat(route.id).isEqualTo(planId)
        assertThat(route.profile).isEqualTo(profile)
        assertThat(route.waypoints).hasSize(2)
        assertThat(route.distanceMeters).isEqualTo(9500.0)
        assertThat(route.plannedSeconds).isEqualTo(1820L)
        assertThat(route.ascentMeters).isEqualTo(145.0)

        val geom = route.geometry
        assertThat(geom).hasSize(3)
        // GeoJSON [lon,lat] → Coord(lat,lon) swap.
        assertThat(geom[0].lat).isEqualTo(47.211)
        assertThat(geom[0].lon).isEqualTo(8.731)
        assertThat(geom[0].ele).isEqualTo(410.0)
    }

    @Test
    fun `descent is computed from geometry when ele is present`() {
        // elevations: 410 → 480 (+70) → 450 (−30). Ascent=70, descent=30.
        val resp = responseWithElevations(listOf(410.0, 480.0, 450.0))
        val route = RouteMapper.map(resp, waypoints, planId, profile).getOrThrow()
        assertThat(route.ascentMeters).isEqualTo(70.0)
        assertThat(route.descentMeters).isEqualTo(30.0)
    }

    @Test
    fun `fallback distance uses haversine when track-length missing`() {
        // Two coords ~1 km apart.
        val resp = responseWithCoordsNoProps(
            listOf(8.731, 47.211),
            listOf(8.744, 47.219),
        )
        val route = RouteMapper.map(resp, waypoints, planId, profile).getOrThrow()
        // ~1.4 km between the points — sanity check, not exact.
        assertThat(route.distanceMeters).isAtLeast(1000.0)
        assertThat(route.distanceMeters).isAtMost(2000.0)
    }

    @Test
    fun `fallback ETA uses profile-specific speed when cost missing`() {
        val resp = responseWithCoordsNoProps(
            listOf(8.731, 47.211),
            listOf(8.744, 47.219),
        )
        val touring = RouteMapper.map(resp, waypoints, planId, RoutingProfile.Touring).getOrThrow()
        val road = RouteMapper.map(resp, waypoints, planId, RoutingProfile.Road).getOrThrow()
        // Road profile → faster → fewer seconds for the same distance.
        assertThat(road.plannedSeconds).isLessThan(touring.plannedSeconds)
    }

    @Test
    fun `surface defaults to Unknown when tags not parsed`() {
        val resp = responseWithCoordsNoProps(
            listOf(8.731, 47.211),
            listOf(8.744, 47.219),
        )
        val route = RouteMapper.map(resp, waypoints, planId, profile).getOrThrow()
        assertThat(route.segments.first().surface).isEqualTo(Surface.Unknown)
    }

    @Test
    fun `surface breakdown aggregates by surface kind`() {
        val resp = responseWithCoordsNoProps(
            listOf(8.731, 47.211),
            listOf(8.744, 47.219),
        )
        val route = RouteMapper.map(resp, waypoints, planId, profile).getOrThrow()
        val breakdown = route.surfaceBreakdown
        assertThat(breakdown).containsKey(Surface.Unknown)
        assertThat(breakdown[Surface.Unknown]).isEqualTo(route.distanceMeters)
    }

    @Test
    fun `fails when waypoints are fewer than 2`() {
        val resp = responseWithCoordsNoProps(listOf(0.0, 0.0), listOf(1.0, 1.0))
        val result = RouteMapper.map(resp, listOf(waypoints.first()), planId, profile)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `fails when response has no features`() {
        val resp = BRouterResponse(type = "FeatureCollection", features = emptyList())
        val result = RouteMapper.map(resp, waypoints, planId, profile)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `fails when geometry is missing`() {
        val resp = BRouterResponse(
            type = "FeatureCollection",
            features = listOf(BRouterFeature(type = "Feature")),
        )
        val result = RouteMapper.map(resp, waypoints, planId, profile)
        assertThat(result.isFailure).isTrue()
    }

    private fun responseWithElevations(eles: List<Double>): BRouterResponse {
        val coords = listOf(8.731, 8.738, 8.744).zip(eles).map { (lon, ele) ->
            listOf(lon, 47.215, ele)
        }
        return BRouterResponse(
            type = "FeatureCollection",
            features = listOf(
                BRouterFeature(
                    type = "Feature",
                    properties = BRouterProperties(),
                    geometry = BRouterGeometry(type = "LineString", coordinates = coords),
                ),
            ),
        )
    }

    private fun responseWithCoordsNoProps(vararg coords: List<Double>): BRouterResponse =
        BRouterResponse(
            type = "FeatureCollection",
            features = listOf(
                BRouterFeature(
                    type = "Feature",
                    properties = BRouterProperties(),
                    geometry = BRouterGeometry(type = "LineString", coordinates = coords.toList()),
                ),
            ),
        )
}
