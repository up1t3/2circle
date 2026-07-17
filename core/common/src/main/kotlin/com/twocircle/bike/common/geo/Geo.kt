package com.twocircle.bike.common.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** WGS84 ellipsoid constants used by haversine / projected calculations. */
object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /**
     * Great-circle distance between two WGS84 points, in metres.
     *
     * Haversine — accurate to ~0.3% on bicycle scales (<1000 km), which is well
     * below GPS jitter on a phone. No need for Vincenty here.
     */
    fun distanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val phi1 = lat1.toRad()
        val phi2 = lat2.toRad()
        val dPhi = (lat2 - lat1).toRad()
        val dLambda = (lon2 - lon1).toRad()
        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    /** Initial bearing from point 1 to point 2, in degrees clockwise from north [0, 360). */
    fun bearingDeg(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val phi1 = lat1.toRad()
        val phi2 = lat2.toRad()
        val dLambda = (lon2 - lon1).toRad()
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        val theta = atan2(y, x)
        return ((theta.toDeg()) + 360.0) % 360.0
    }
}

private fun Double.toRad(): Double = this * PI / 180.0
private fun Double.toDeg(): Double = this * 180.0 / PI

/**
 * Projected Mercator-ish bounding box around a centre, by extending [radiusMeters]
 * along cardinal directions. Used for "show me everything near the map centre".
 *
 * Returns [minLat, minLon, maxLat, maxLon] in degrees.
 */
fun boundingBox(
    centerLat: Double, centerLon: Double, radiusMeters: Double,
): DoubleArray {
    val dLat = radiusMeters / 111_320.0
    val dLon = radiusMeters / (111_320.0 * cos(centerLat * PI / 180.0))
    return doubleArrayOf(
        centerLat - dLat, centerLon - dLon,
        centerLat + dLat, centerLon + dLon,
    )
}

/** Arc length between two consecutive track points — convenience for [Geo.distanceMeters]. */
internal fun segmentLength(a: LatLon, b: LatLon): Double =
    Geo.distanceMeters(a.lat, a.lon, b.lat, b.lon)

/** Simple WGS84 coordinate holder. */
data class LatLon(val lat: Double, val lon: Double) {
    init {
        require(lat in -90.0..90.0) { "lat $lat out of range" }
        require(lon in -180.0..180.0) { "lon $lon out of range" }
    }
}

/** Slope (gradient) along a segment in percent; positive = uphill. */
fun gradientPct(distanceMeters: Double, elevationDeltaMeters: Double): Double {
    if (distanceMeters <= 0.0) return 0.0
    return (elevationDeltaMeters / distanceMeters) * 100.0
}

/** Cumulative uphill elevation gain across a list of (distance, elevation) samples. */
fun totalAscentMeters(elevations: List<Double>): Double {
    if (elevations.size < 2) return 0.0
    var gain = 0.0
    for (i in 1 until elevations.size) {
        val d = elevations[i] - elevations[i - 1]
        if (d > 0) gain += d
    }
    return gain
}

/** Same as [totalAscentMeters] but for descents. */
fun totalDescentMeters(elevations: List<Double>): Double {
    if (elevations.size < 2) return 0.0
    var drop = 0.0
    for (i in 1 until elevations.size) {
        val d = elevations[i] - elevations[i - 1]
        if (d < 0) drop += -d
    }
    return drop
}
