package com.twocircle.bike.feature.routing.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * BRouter `/brouter` GeoJSON response DTO.
 *
 * BRouter returns a GeoJSON FeatureCollection where each feature is a route segment.
 * Properties carry the cost / time / elevation aggregates we need for the "honest ETA"
 * and the route preview.
 *
 * Coordinate convention: GeoJSON uses `[lon, lat, ele?]` ordering — note this is the
 * opposite of the `lat, lon` order we use internally. The mapper handles the swap.
 *
 * Properties of interest (per BRouter's geojson format):
 *  - "cost"           : total cost (roughly seconds for trekking profiles)
 *  - "filtered ascend": uphill elevation gain in metres (after noise filtering)
 *  - "plain ascend"   : uphill elevation gain in metres (raw)
 *  - "track-length"   : length in metres
 *  - "total-energy"   : energy in Joules (only some profiles)
 *
 * "cost" → plannedSeconds mapping: for the standard trekking/fastbike profiles BRouter
 * encodes travel time in seconds as the cost. We trust this rather than recomputing,
 * because BRouter's weights already factor in surface, gradient and rider profile.
 */
@Serializable
data class BRouterResponse(
    val type: String,
    val features: List<BRouterFeature> = emptyList(),
)

@Serializable
data class BRouterFeature(
    val type: String,
    val properties: BRouterProperties = BRouterProperties(),
    val geometry: BRouterGeometry? = null,
)

@Serializable
data class BRouterProperties(
    @SerialName("cost") val cost: Double? = null,
    @SerialName("filtered ascend") val filteredAscend: Double? = null,
    @SerialName("plain ascend") val plainAscend: Double? = null,
    @SerialName("track-length") val trackLength: Double? = null,
    @SerialName("total-energy") val totalEnergy: Double? = null,
    /**
     * Per-message tags — BRouter emits "way" messages with surface/incline tags as a
     * comma-separated string. Optional; the offline engine path parses these too.
     * Kept as raw JsonElement because the schema varies by profile.
     */
    val tags: JsonElement? = null,
)

@Serializable
data class BRouterGeometry(
    val type: String,
    /** Array of [lon, lat, ele?] arrays. */
    val coordinates: List<List<Double>> = emptyList(),
)

/**
 * BRouter error response — non-200 returns a plain-text body describing the failure.
 */
@Serializable
data class BRouterError(val message: String = "")
