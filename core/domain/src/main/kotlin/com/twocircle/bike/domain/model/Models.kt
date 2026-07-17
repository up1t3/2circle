package com.twocircle.bike.domain.model

import kotlinx.serialization.Serializable

/**
 * A geographic coordinate with optional elevation, in WGS84.
 * Domain layer deals in this type; adapters (Room, GPX, MapLibre) map to/from it.
 *
 * [ele] is in metres above mean sea level (geoid). For bicycle routing, the DEM
 * lookup happens at the routing/storage layer; domain models just carry the value.
 */
@Serializable
data class Coord(
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
) {
    init {
        require(lat in -90.0..90.0) { "lat $lat out of range" }
        require(lon in -180.0..180.0) { "lon $lon out of range" }
    }
}

/**
 * A user-defined point in a planned route: start, via, or end.
 *
 * [source] records how it was added so the UI can render appropriate affordances
 * (e.g. show "spring" icon for a POI-sourced waypoint).
 */
@Serializable
data class Waypoint(
    val id: WaypointId,
    val coord: Coord,
    val name: String? = null,
    val role: Role = Role.Via,
    val source: Source = Source.Manual,
) {
    enum class Role { Start, Via, End }
    enum class Source {
        /** User long-pressed on the map. */
        Manual,

        /** Found via offline search by name. */
        Search,

        /** "Use my current location" button. */
        Gps,

        /** Imported from a GPX file. */
        GpxImport,
    }
}

@Serializable
@JvmInline
value class WaypointId(val value: String)

@Serializable
@JvmInline
value class TrackId(val value: String)

@Serializable
@JvmInline
value class RegionId(val value: String)

@Serializable
@JvmInline
value class RoutePlanId(val value: String)

/**
 * Surface classification derived from OSM `surface=*` tag.
 * Drives map road colouring and routing penalty.
 *
 * Order from fastest to slowest; keep ordinal semantics — routing weights rely on it.
 */
@Serializable
enum class Surface {
    Asphalt,     // asphalt, concrete, paving_stones
    Compacted,  // compacted, gravel, fine_gravel
    Dirt,       // dirt, ground, earth
    Sand,       // sand
    Grass,      // grass, grass_paver
    Rock,       // rock, pebblestone
    Unknown,
}

/**
 * Smoothness (OSM `smoothness=*`) — ride comfort, distinct from surface material.
 * Used as a secondary routing penalty.
 */
@Serializable
enum class Smoothness {
    Excellent, Good, Intermediate, Bad, VeryBad, Horrible, Impassable, Unknown
}

/** Map from raw OSM surface tag to our classification. Pure function — unit tested. */
fun surfaceFromOsm(tag: String?): Surface = when (tag?.lowercase()) {
    "asphalt", "concrete", "chipseal", "concrete:lanes", "concrete:plates",
    "paving_stones", "sett", "metal",
    -> Surface.Asphalt
    "compacted", "gravel", "fine_gravel", "pebblestone", "wood", "woodchips",
    -> Surface.Compacted
    "dirt", "ground", "earth", "soil", "mud", "unhewn_cobblestone",
    -> Surface.Dirt
    "sand" -> Surface.Sand
    "grass", "grass_paver" -> Surface.Grass
    "rock" -> Surface.Rock
    else -> Surface.Unknown
}

/** Map from raw OSM smoothness tag to our classification. */
fun smoothnessFromOsm(tag: String?): Smoothness = when (tag?.lowercase()) {
    "excellent" -> Smoothness.Excellent
    "good" -> Smoothness.Good
    "intermediate" -> Smoothness.Intermediate
    "bad" -> Smoothness.Bad
    "very_bad" -> Smoothness.VeryBad
    "horrible" -> Smoothness.Horrible
    "impassable" -> Smoothness.Impassable
    else -> Smoothness.Unknown
}
