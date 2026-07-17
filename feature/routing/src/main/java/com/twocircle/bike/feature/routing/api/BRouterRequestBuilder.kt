package com.twocircle.bike.feature.routing.api

import com.twocircle.bike.domain.model.Waypoint

/**
 * Builds the BRouter `lonlats` query parameter.
 *
 * Format: `lon1,lat1|lon2,lat2|...` — note lon-first (GeoJSON convention).
 *
 * Precision: 6 decimals gives ~10 cm resolution, well below GPS jitter; fewer decimals
 * make URLs shorter and easier to share. We hardcode 6 here rather than using the
 * default Double.toString, which can emit 15+ digits.
 */
object BRouterRequestBuilder {

    fun buildLonLats(waypoints: List<Waypoint>): String =
        waypoints.joinToString("|") { wp ->
            "${formatCoord(wp.coord.lon)},${formatCoord(wp.coord.lat)}"
        }

    /** Maps our RoutingProfile enum to the BRouter profile string. */
    fun profileName(profile: com.twocircle.bike.domain.model.RoutingProfile): String =
        when (profile) {
            com.twocircle.bike.domain.model.RoutingProfile.Touring -> "trekking"
            com.twocircle.bike.domain.model.RoutingProfile.Road -> "fastbike"
            com.twocircle.bike.domain.model.RoutingProfile.Mtb -> "mundo"
        }

    private fun formatCoord(v: Double): String =
        String.format(java.util.Locale.US, "%.6f", v)
}
