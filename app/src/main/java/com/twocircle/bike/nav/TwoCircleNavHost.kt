package com.twocircle.bike.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.twocircle.bike.R
import com.twocircle.bike.ui.screens.PlaceholderScreen

/**
 * Top-level navigation graph.
 *
 * Bottom nav: Карта / Маршруты / Треки / Регионы.
 * Each destination is a feature-module surface. Feature screens are injected as their
 * modules land in Steps 3–8; until then, [PlaceholderScreen] keeps the shell runnable
 * so the navigation contract is exercised end-to-end.
 */
@Composable
fun TwoCircleNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevel.entries.forEach { dest ->
                    val selected = current?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(dest.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = TopLevel.MAP.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevel.MAP.route) {
                com.twocircle.bike.feature.map.screen.MapScreen(
                    onOpenSearch = { nav.navigate("search") },
                    onOpenRide = { nav.navigate("ride") },
                )
            }
            composable("search") {
                com.twocircle.bike.feature.search.screen.SearchScreen(
                    onResultSelected = { result ->
                        // For v1: return to map. Step 5 wires this into the Route Builder.
                        nav.popBackStack()
                    },
                )
            }
            composable("ride") {
                com.twocircle.bike.feature.tracking.screen.TrackingScreen()
            }
            composable(TopLevel.ROUTES.route) {
                com.twocircle.bike.feature.routing.screen.RouteBuilderScreen()
            }
            composable(TopLevel.TRACKS.route) {
                com.twocircle.bike.feature.tracks.screen.TracksScreen(
                    onTrackSelected = { id -> nav.navigate("tracks/$id") },
                )
            }
            composable(
                route = "tracks/{trackId}",
                arguments = listOf(
                    androidx.navigation.navArgument("trackId") { type = androidx.navigation.NavType.StringType },
                ),
            ) { entry ->
                val trackId = entry.arguments?.getString("trackId").orEmpty()
                com.twocircle.bike.feature.tracks.screen.TrackDetailScreen(
                    trackId = trackId,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(TopLevel.REGIONS.route) {
                com.twocircle.bike.feature.regions.screen.RegionsScreen()
            }
            TopLevel.entries
                .filter { it != TopLevel.MAP && it != TopLevel.ROUTES && it != TopLevel.TRACKS && it != TopLevel.REGIONS }
                .forEach { dest ->
                    composable(dest.route) { PlaceholderScreen(dest.route) }
                }
        }
    }
}

enum class TopLevel(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    MAP("map", R.string.nav_map, Icons.Outlined.Map),
    ROUTES("routes", R.string.nav_routes, Icons.Outlined.PlayCircleOutline),
    TRACKS("tracks", R.string.nav_tracks, Icons.Outlined.Timeline),
    REGIONS("regions", R.string.nav_regions, Icons.Outlined.Public),
}
