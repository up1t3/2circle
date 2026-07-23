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
 * POI category colour scheme — keyed by [com.twocircle.bike.feature.search.engine.PlaceKind.osmValue]
 * so the [com.twocircle.bike.feature.map.view.PoiMarkerLayer] match-expression and any
 * future call site share one source of truth.
 *
 * Categories are grouped by rider-relevant meaning (medical, water, food, lodging, …)
 * rather than OSM tag family. The fallback amber matches the previous single-colour
 * scheme so places without a dedicated colour (cities, villages, "other") stay legible.
 */
object PoiColors {
    const val MEDICAL = "#E53935"        // pharmacy, hospital — red
    const val FUEL = "#5E35B1"           // fuel — purple
    const val WATER = "#039BE5"          // water, spring — blue
    const val FOOD = "#FB8C00"           // cafe, restaurant — orange
    const val SHOP = "#8D6E63"           // shop — brown
    const val LODGING = "#7CB342"        // hotel, campsite — green
    const val MONEY = "#43A047"          // atm — dark green
    const val BICYCLE = "#00897B"        // bicycle_service, bicycle_rental — teal
    const val SIGHT = "#FDD835"          // viewpoint, mountain_pass — yellow
    const val DEFAULT = "#FFC107"        // city/town/village/hamlet/other — legacy amber

    /**
     * Flat list of `(osmValue, hex)` pairs for building a MapLibre `match` expression.
     * Order is irrelevant — `match` is exact-key lookup, not priority.
     */
    val byKind: List<Pair<String, String>> = listOf(
        "pharmacy" to MEDICAL,
        "hospital" to MEDICAL,
        "fuel" to FUEL,
        "water" to WATER,
        "spring" to WATER,
        "cafe" to FOOD,
        "restaurant" to FOOD,
        "shop" to SHOP,
        "hotel" to LODGING,
        "campsite" to LODGING,
        "atm" to MONEY,
        "bicycle_service" to BICYCLE,
        "bicycle_rental" to BICYCLE,
        "viewpoint" to SIGHT,
        "mountain_pass" to SIGHT,
    )
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
