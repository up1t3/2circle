package com.twocircle.bike.feature.map.style

/**
 * Road surface colour scheme — the user-visible identity of 2circle.
 *
 * "asphalt green, gravel orange, sand red" — how riders recognise surface at a glance.
 *
 * IMPORTANT: these values MUST stay in sync with `BikeColors` in :core:designsystem,
 * which drives the legend chips. If you change a colour here, update the legend too.
 * They're duplicated (not shared) because the style JSON uses hex strings and the
 * Compose layer uses Color ints — bridging them would force one layer to depend on the
 * other's representation.
 *
 * Colours are HSL-style hex strings understood by MapLibre's style JSON parser.
 */
object MapColors {
    const val ASPHALT = "#4CAF50"      // green
    const val COMPACTED = "#FF9800"    // orange
    const val DIRT = "#8D6E63"         // brown
    const val SAND = "#F44336"         // red
    const val GRASS = "#7CB342"        // light green
    const val ROCK = "#607D8B"         // blue-grey
    const val UNKNOWN = "#BDBDBD"      // grey

    /** Background — near-black, preserves night vision during overnight tours. */
    const val BACKGROUND = "#101418"
    const val WATER = "#0F2535"
    const val LAND = "#1A1F25"
}

/**
 * Maps an OSM `surface=*` tag value to its colour. Pure function — unit tested.
 *
 * Used both at style-build time (to bake the match expression into the style JSON) and
 * in the legend UI. Mirrors `surfaceFromOsm` in :core:domain but lives here because the
 * style layer speaks MapLibre's domain (raw OSM tags), not our domain enum.
 */
fun colorForSurface(osmSurface: String?): String = when (osmSurface?.lowercase()) {
    "asphalt", "concrete", "chipseal", "concrete:lanes", "concrete:plates",
    "paving_stones", "sett", "metal",
    -> MapColors.ASPHALT
    "compacted", "gravel", "fine_gravel", "pebblestone", "wood", "woodchips"
    -> MapColors.COMPACTED
    "dirt", "ground", "earth", "soil", "mud", "unhewn_cobblestone"
    -> MapColors.DIRT
    "sand" -> MapColors.SAND
    "grass", "grass_paver" -> MapColors.GRASS
    "rock" -> MapColors.ROCK
    else -> MapColors.UNKNOWN
}
