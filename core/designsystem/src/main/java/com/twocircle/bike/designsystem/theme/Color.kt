package com.twocircle.bike.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * App palette.
 *
 * The map road-colouring scheme is the user-facing identity of the app —
 * "asphalt green, gravel orange, sand red" is how riders recognise surface at a glance.
 * These map exactly to the MapLibre style expression values used in :feature:map.
 *
 * Kept in one place so the legend UI and the map style stay in sync.
 */
object BikeColors {

    // Road surface classification — drives map rendering AND legend chips.
    val SurfaceAsphalt = Color(0xFF4CAF50)   // green
    val SurfaceCompacted = Color(0xFFFF9800) // orange
    val SurfaceDirt = Color(0xFF8D6E63)      // brown
    val SurfaceSand = Color(0xFFF44336)      // red
    val SurfaceGrass = Color(0xFF7CB342)     // light green
    val SurfaceRock = Color(0xFF607D8B)      // blue-grey
    val SurfaceUnknown = Color(0xFFBDBDBD)   // grey

    // Routing difficulty (derived from gradient + surface).
    val DifficultyEasy = Color(0xFF66BB6A)
    val DifficultyModerate = Color(0xFFFFCA28)
    val DifficultyHard = Color(0xFFFF7043)
    val DifficultyExtreme = Color(0xFFD32F2F)

    // Ride telemetry accents.
    val Speed = Color(0xFF29B6F6)
    val Elevation = Color(0xFFAB47BC)
    val HeartRate = Color(0xFFEF5350)
}

/** Dark palette (default for bicycle touring — preserves night vision). */
object BikeDarkColors {
    val Background = Color(0xFF101418)
    val Surface = Color(0xFF1A1F25)
    val OnBackground = Color(0xFFE6E6E6)
    val OnSurface = Color(0xFFE6E6E6)
    val Primary = Color(0xFF66BB6A)
    val OnPrimary = Color(0xFF003300)
    val Secondary = Color(0xFF29B6F6)
    val Error = Color(0xFFEF5350)
}

/** Light palette (day mode — high-contrast sunlight readability). */
object BikeLightColors {
    val Background = Color(0xFFF5F5F5)
    val Surface = Color(0xFFFFFFFF)
    val OnBackground = Color(0xFF1A1A1A)
    val OnSurface = Color(0xFF1A1A1A)
    val Primary = Color(0xFF2E7D32)
    val OnPrimary = Color(0xFFFFFFFF)
    val Secondary = Color(0xFF0277BD)
    val Error = Color(0xFFC62828)
}
