package com.twocircle.bike.feature.routing.mapper

import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.Route
import com.twocircle.bike.domain.model.RoutePlanId
import com.twocircle.bike.domain.model.RoutingProfile
import com.twocircle.bike.domain.model.Segment
import com.twocircle.bike.domain.model.Smoothness
import com.twocircle.bike.domain.model.Surface
import com.twocircle.bike.domain.model.Waypoint
import com.twocircle.bike.domain.model.WaypointId
import com.twocircle.bike.feature.routing.api.BRouterResponse

/**
 * Maps a BRouter GeoJSON response into our domain [Route].
 *
 * BRouter returns a single FeatureCollection for the whole request — it doesn't split
 * by waypoint pair. For v1 we treat the entire response as one big [Segment] between
 * the first and last waypoint; per-pair splitting would require multiple BRouter
 * round-trips, which is wasteful for the preview use case.
 *
 * "Honest ETA": the [Segment.plannedSeconds] comes directly from BRouter's `cost`
 * property, which encodes a physics-based travel-time model (rider power vs gradient +
 * surface + aero). We deliberately do not recompute this — BRouter's weights are the
 * source of truth, and reimplementing them would drift from the planner's intent.
 */
object RouteMapper {

    /**
     * @param response the BRouter GeoJSON response
     * @param waypoints the waypoints the route was built from (in order)
     * @param planId identifier for the resulting [Route]
     * @param profile profile used (preserved on the route for live-ETA comparison)
     */
    fun map(
        response: BRouterResponse,
        waypoints: List<Waypoint>,
        planId: RoutePlanId,
        profile: RoutingProfile,
    ): Result<Route> = runCatching {
        require(waypoints.size >= 2) {
            "Route requires at least 2 waypoints, got ${waypoints.size}"
        }
        val feature = response.features.firstOrNull()
            ?: error("BRouter response has no features")
        val geometry = feature.geometry
            ?: error("BRouter feature has no geometry")
        require(geometry.type == "LineString") {
            "Expected LineString geometry, got ${geometry.type}"
        }

        val coords = geometry.coordinates.map { c ->
            require(c.size >= 2) { "Coordinate must have ≥2 elements, got ${c.size}" }
            Coord(
                lat = c[1], // GeoJSON is [lon, lat, ele?]
                lon = c[0],
                ele = c.getOrNull(2),
            )
        }
        require(coords.size >= 2) { "Route geometry must have ≥2 points" }

        val props = feature.properties
        val distanceMeters = props.trackLength ?: estimateDistance(coords)
        val plannedSeconds = (props.cost ?: estimateSeconds(distanceMeters, profile)).toLong()
        val ascent = props.filteredAscend ?: estimateAscent(coords)
        val descent = computeDescent(coords)

        val segment = Segment(
            from = waypoints.first(),
            to = waypoints.last(),
            geometry = coords,
            distanceMeters = distanceMeters,
            plannedSeconds = plannedSeconds,
            ascentMeters = ascent,
            descentMeters = descent,
            surface = Surface.Unknown, // Per-segment surface needs tag parsing (later)
            smoothness = Smoothness.Unknown,
        )

        Route(
            id = planId,
            profile = profile,
            segments = listOf(segment),
            waypoints = waypoints,
        )
    }

    /** Haversine fallback when BRouter omits track-length. */
    private fun estimateDistance(coords: List<Coord>): Double {
        if (coords.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until coords.size) {
            total += haversineMeters(coords[i - 1], coords[i])
        }
        return total
    }

    /** Crude ETA fallback (~18 km/h touring) when BRouter omits cost. */
    private fun estimateSeconds(distanceMeters: Double, profile: RoutingProfile): Double {
        val speedMs = when (profile) {
            RoutingProfile.Road -> 7.0   // 25 km/h
            RoutingProfile.Touring -> 5.0 // 18 km/h
            RoutingProfile.Mtb -> 4.0     // 14 km/h
        }
        return distanceMeters / speedMs
    }

    /** Sum positive elevation deltas. */
    private fun estimateAscent(coords: List<Coord>): Double {
        val eles = coords.mapNotNull { it.ele }
        if (eles.size < 2) return 0.0
        var gain = 0.0
        for (i in 1 until eles.size) {
            val d = eles[i] - eles[i - 1]
            if (d > 0) gain += d
        }
        return gain
    }

    /** Sum negative elevation deltas (absolute). */
    private fun computeDescent(coords: List<Coord>): Double {
        val eles = coords.mapNotNull { it.ele }
        if (eles.size < 2) return 0.0
        var drop = 0.0
        for (i in 1 until eles.size) {
            val d = eles[i] - eles[i - 1]
            if (d < 0) drop += -d
        }
        return drop
    }

    private fun haversineMeters(a: Coord, b: Coord): Double {
        // Local copy to avoid coupling feature:routing to :core:common for one helper.
        val r = 6_371_008.8
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val dp = Math.toRadians(b.lat - a.lat)
        val dl = Math.toRadians(b.lon - a.lon)
        val h = Math.sin(dp / 2).let { it * it } +
            Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2).let { it * it }
        return 2 * r * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
    }
}

/** Internal: synthesize a stable waypoint ID from its position in the list. */
internal fun syntheticWaypointId(index: Int): WaypointId = WaypointId("wp-$index")
