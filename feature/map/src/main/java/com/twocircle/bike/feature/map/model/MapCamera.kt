package com.twocircle.bike.feature.map.model

import kotlinx.serialization.Serializable

/**
 * Camera state for the map. Pure Kotlin — no MapLibre types leak here, so this can be
 * held in a ViewModel, saved across config changes, and tested without the SDK.
 *
 * MapLibre's own CameraPosition is an Android-specific mutable builder; we keep an
 * immutable snapshot here and translate to/from the SDK at the binding boundary.
 *
 * [bearing] is in degrees clockwise from north; [tilt] in degrees from straight-down.
 */
@Serializable
data class MapCamera(
    val lat: Double,
    val lon: Double,
    val zoom: Double = 12.0,
    val bearing: Double = 0.0,
    val tilt: Double = 0.0,
) {
    init {
        require(lat in -90.0..90.0) { "lat $lat out of range" }
        require(lon in -180.0..180.0) { "lon $lon out of range" }
        require(zoom in 0.0..22.0) { "zoom $zoom out of range" }
        require(bearing in 0.0..360.0) { "bearing $bearing out of range" }
        require(tilt in 0.0..60.0) { "tilt $tilt out of range" }
    }

    companion object {
        /** Default starting view: Kyiv, zoom 11 — a sensible first-launch centre. */
        val DEFAULT = MapCamera(lat = 50.4501, lon = 30.5234, zoom = 11.0)
    }
}

/** A simple latitude/longitude bounding box. */
@Serializable
data class MapBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    init {
        require(south <= north) { "south > north" }
        require(west <= east) { "west > east" }
    }

    fun contains(lat: Double, lon: Double): Boolean =
        lat in south..north && lon in west..east
}
