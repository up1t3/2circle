package com.twocircle.bike.domain.model

import kotlinx.serialization.Serializable

/**
 * A computed route: ordered geometry with per-segment metadata, plus aggregates.
 *
 * Produced by the routing engine (BRouter offline or cloud fallback). The aggregates
 * ([distanceMeters], [ascentMeters], [plannedSeconds]) are precomputed once and reused
 * by the live-ETA engine and the route preview UI — no recomputation per frame.
 *
 * Honest ETA: [plannedSeconds] already accounts for surface, gradient and rider-profile
 * penalties because BRouter's weight function encodes a physics-based travel-time model.
 * The live tracking layer compares actual progress against this plan.
 */
@Serializable
data class Route(
    val id: RoutePlanId,
    val profile: RoutingProfile,
    val segments: List<Segment>,
    val waypoints: List<Waypoint>,
) {
    /** Total length in metres. */
    val distanceMeters: Double get() = segments.sumOf { it.distanceMeters }

    /** Total planned travel time in seconds. */
    val plannedSeconds: Long get() = segments.sumOf { it.plannedSeconds }

    /** Total uphill elevation gain in metres. */
    val ascentMeters: Double get() = segments.sumOf { it.ascentMeters }

    /** Total downhill elevation loss in metres (absolute). */
    val descentMeters: Double get() = segments.sumOf { it.descentMeters }

    /** Breakdown by surface for preview UI ("12 km asphalt, 4 km gravel…"). */
    val surfaceBreakdown: Map<Surface, Double>
        get() = segments.groupingBy { it.surface }.fold(0.0) { acc, s -> acc + s.distanceMeters }

    /** Flat list of all coordinates for rendering the polyline. */
    val geometry: List<Coord> get() = segments.flatMap { it.geometry }
}

/**
 * One hop between two consecutive waypoints, already decorated with weights.
 *
 * [geometry] is a dense list of coords (typically one point per ~30-90 m on the ground),
 * enough to render the line smoothly at bicycle-map zoom levels.
 */
@Serializable
data class Segment(
    val from: Waypoint,
    val to: Waypoint,
    val geometry: List<Coord>,
    val distanceMeters: Double,
    val plannedSeconds: Long,
    val ascentMeters: Double,
    val descentMeters: Double,
    val surface: Surface,
    val smoothness: Smoothness,
) {
    init {
        require(geometry.size >= 2) { "segment must have ≥2 points, got ${geometry.size}" }
        require(distanceMeters >= 0.0) { "distance cannot be negative" }
        require(plannedSeconds >= 0L) { "time cannot be negative" }
    }
}

/**
 * BRouter profile bundle, matching files shipped in assets/routing-profiles/.
 *
 * Localised display names live in `:core:designsystem` (`RoutingProfile.displayNameRes()`)
 * — kept out of this pure-Kotlin domain layer so it stays free of Android resource deps.
 */
@Serializable
enum class RoutingProfile(val brouterProfileFile: String) {
    Touring("trekking.brf"),
    Road("fastbike.brf"),
    Mtb("mtb.brf"),
}

/**
 * Live progress snapshot during a ride, fed into the live-ETA engine.
 *
 * Emitted by the tracking service on every persisted GPS point.
 */
@Serializable
data class RideProgress(
    val routeId: RoutePlanId,
    val position: Coord,
    val timestampMs: Long,
    val speedMps: Double,
    /** Distance along route already covered, in metres. */
    val coveredDistanceMeters: Double,
    /** Elapsed riding time in seconds (excludes pauses). */
    val elapsedSeconds: Long,
)
