package com.twocircle.bike.designsystem.l10n

import androidx.annotation.StringRes
import com.twocircle.bike.designsystem.R
import com.twocircle.bike.domain.model.RoutingProfile
import com.twocircle.bike.domain.model.Waypoint

/**
 * Localised display labels for domain enums.
 *
 * Domain enums (`:core:domain`) cannot reference Android resources (pure-Kotlin module),
 * so we keep the @StringRes mapping here in :core:designsystem and the UI calls these
 * extensions before rendering. Avoids leaking enum `.name` (Start/Via/End → Старт/Транзит/Финиш)
 * or hardcoded Russian (Туринг/Шоссе/МТБ) into a non-locale-aware layer.
 */
@StringRes
fun RoutingProfile.displayNameRes(): Int = when (this) {
    RoutingProfile.Touring -> R.string.profile_touring
    RoutingProfile.Road -> R.string.profile_road
    RoutingProfile.Mtb -> R.string.profile_mtb
}

@StringRes
fun Waypoint.Role.displayNameRes(): Int = when (this) {
    Waypoint.Role.Start -> R.string.waypoint_role_start
    Waypoint.Role.Via -> R.string.waypoint_role_via
    Waypoint.Role.End -> R.string.waypoint_role_end
}

@StringRes
fun Waypoint.Source.displayNameRes(): Int = when (this) {
    Waypoint.Source.Manual -> R.string.waypoint_source_manual
    Waypoint.Source.Search -> R.string.waypoint_source_search
    Waypoint.Source.Gps -> R.string.waypoint_source_gps
    Waypoint.Source.GpxImport -> R.string.waypoint_source_gpx_import
}
