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

/** Темная палитра (основная для велотуризма — сохраняет ночное зрение). */
object BikeDarkColors {
    val Background = Color(0xFF1E252D)
    val Surface = Color(0xFF262F38)
    val SurfaceVariant = Color(0xFF2A343C)
    val OnBackground = Color(0xFFE0E4E8)
    val OnSurface = Color(0xFFE0E4E8)
    val OnSurfaceMuted = Color(0xFF8B95A0)
    val Primary = Color(0xFF4CAF50)
    val OnPrimary = Color(0xFFFFFFFF)
    val Secondary = Color(0xFF2C5F7E)
    val Error = Color(0xFFEF4444)
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFFFC107)
}

/** Светлая палитра (дневной режим — хорошая читаемость на солнце). */
object BikeLightColors {
    val Background = Color(0xFFF5F7FA)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFE8ECF0)
    val OnBackground = Color(0xFF1E252D)
    val OnSurface = Color(0xFF1E252D)
    val OnSurfaceMuted = Color(0xFF6B7280)
    val Primary = Color(0xFF388E3C)
    val OnPrimary = Color(0xFFFFFFFF)
    val Secondary = Color(0xFF3B7CA8)
    val Error = Color(0xFFDC2626)
}

