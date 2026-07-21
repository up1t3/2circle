package com.twocircle.bike.feature.map.style

/**
 * Road surface colour scheme — the user-visible identity of 2circle.
 *
 * "asphalt green, gravel orange, sand red" — how riders recognise surface at a glance.
 */
object MapColors {
    const val ASPHALT = "#4CAF50"      // green
    const val COMPACTED = "#FF9800"    // orange
    const val DIRT = "#8D6E63"         // brown
    const val SAND = "#F44336"         // red
    const val GRASS = "#7CB342"        // light green
    const val ROCK = "#607D8B"         // blue-grey
    const val UNKNOWN = "#BDBDBD"      // grey

    // Deprecated legacy aliases for backwards compatibility with tests
    const val BACKGROUND = DarkMapColors.BACKGROUND
    const val WATER = DarkMapColors.WATER
    const val LAND = DarkMapColors.LAND
}

object DarkMapColors {
    const val BACKGROUND = "#2A343C"
    const val WATER = "#2C5F7E"
    const val LAND = "#2E3A2A"
    const val TEXT_COLOR = "#FFFFFF"
    const val TEXT_HALO = "#000000"
}

object LightMapColors {
    const val BACKGROUND = "#E8ECF0"
    const val WATER = "#A8D0E8"
    const val LAND = "#D2E3C8"
    const val TEXT_COLOR = "#1E252D"
    const val TEXT_HALO = "#FFFFFF"
}

/**
 * Maps an OSM `surface=*` tag value to its colour. Pure function — unit tested.
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
