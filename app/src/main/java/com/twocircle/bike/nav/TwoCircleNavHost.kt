package com.twocircle.bike.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.twocircle.bike.R
import com.twocircle.bike.feature.auth.ui.LoginScreen
import com.twocircle.bike.feature.auth.ui.RegisterScreen
import com.twocircle.bike.feature.social.ui.feed.FeedScreen
import com.twocircle.bike.feature.social.ui.profile.ProfileScreen
import kotlinx.coroutines.launch

/**
 * Top-level navigation graph.
 *
 * Bottom nav (5 tabs): Карта / Маршруты / Треки / Лента / Профиль.
 */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun TwoCircleNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    val draftViewModel: com.twocircle.bike.feature.routing.screen.RouteBuilderViewModel =
        androidx.hilt.navigation.compose.hiltViewModel(
            viewModelStoreOwner = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!
                as androidx.lifecycle.ViewModelStoreOwner,
        )
    val focusViewModel: com.twocircle.bike.feature.map.screen.FocusViewModel =
        androidx.hilt.navigation.compose.hiltViewModel(
            viewModelStoreOwner = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!
                as androidx.lifecycle.ViewModelStoreOwner,
        )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            enterTransition = {
                androidx.compose.animation.slideInHorizontally(
                    initialOffsetX = { it / 3 },
                    animationSpec = androidx.compose.animation.core.tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250))
            },
            exitTransition = {
                androidx.compose.animation.slideOutHorizontally(
                    targetOffsetX = { -it / 3 },
                    animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                ) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200))
            },
            popEnterTransition = {
                androidx.compose.animation.slideInHorizontally(
                    initialOffsetX = { -it / 3 },
                    animationSpec = androidx.compose.animation.core.tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250))
            },
            popExitTransition = {
                androidx.compose.animation.slideOutHorizontally(
                    targetOffsetX = { it / 3 },
                    animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                ) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200))
            },
        ) {
            composable(
                TopLevel.MAP.route,
                enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) },
                exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) },
            ) {
                com.twocircle.bike.feature.map.screen.MapScreen(
                    onOpenSearch = { nav.navigate("search") },
                    onOpenRide = { nav.navigate("ride") },
                    onOpenSettings = { nav.navigate("settings") },
                    onLongPressAt = { lat, lon ->
                        draftViewModel.addWaypointManual(
                            com.twocircle.bike.domain.model.Coord(lat, lon),
                        )
                        scope.launch {
                            snackbarHostState.showSnackbar("Точка добавлена в маршрут")
                        }
                    },
                    onAddPoiToRoute = { poi ->
                        draftViewModel.addWaypointSearch(
                            com.twocircle.bike.domain.model.Coord(poi.lat, poi.lon),
                            poi.name,
                        )
                        scope.launch {
                            snackbarHostState.showSnackbar(poi.name)
                        }
                    },
                )
            }
            composable("search") {
                com.twocircle.bike.feature.search.screen.SearchScreen(
                    onResultSelected = { result ->
                        nav.popBackStack(TopLevel.MAP.route, inclusive = false)
                        focusViewModel.focusController.focusOn(
                            lat = result.hit.lat,
                            lon = result.hit.lon,
                            name = result.hit.name,
                        )
                    },
                    onAddToRoute = { result ->
                        draftViewModel.addWaypointSearch(
                            com.twocircle.bike.domain.model.Coord(result.hit.lat, result.hit.lon),
                            result.hit.name,
                        )
                        scope.launch {
                            snackbarHostState.showSnackbar(result.hit.name)
                        }
                    },
                )
            }
            composable("ride") {
                com.twocircle.bike.feature.tracking.screen.TrackingScreen()
            }
            composable(
                TopLevel.ROUTES.route,
                enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) },
                exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) },
            ) {
                com.twocircle.bike.feature.routing.screen.RouteBuilderScreen(
                    onOpenSavedRoutes = { nav.navigate("saved-routes") },
                )
            }
            composable("saved-routes") {
                com.twocircle.bike.feature.routing.screen.SavedRoutesScreen(
                    onPlanSelected = { nav.popBackStack() },
                )
            }
            composable(
                TopLevel.TRACKS.route,
                enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) },
                exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) },
            ) {
                com.twocircle.bike.feature.tracks.screen.TracksScreen(
                    onTrackSelected = { id -> nav.navigate("tracks/$id") },
                )
            }
            composable(
                route = "tracks/{trackId}",
                arguments = listOf(
                    navArgument("trackId") { type = NavType.StringType },
                ),
            ) { entry ->
                val trackId = entry.arguments?.getString("trackId").orEmpty()
                com.twocircle.bike.feature.tracks.screen.TrackDetailScreen(
                    trackId = trackId,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                TopLevel.FEED.route,
                enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) },
                exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) },
            ) {
                FeedScreen()
            }
            composable(
                TopLevel.PROFILE.route,
                enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) },
                exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) },
            ) {
                ProfileScreen(
                    onNavigateToSettings = { nav.navigate("settings") },
                    onNavigateToLogin = { nav.navigate("login") },
                )
            }
            composable("login") {
                LoginScreen(
                    onSuccess = { nav.popBackStack() },
                    onRegister = { nav.navigate("register") },
                )
            }
            composable("register") {
                RegisterScreen(
                    onSuccess = { nav.popBackStack(TopLevel.PROFILE.route, false) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("regions") {
                com.twocircle.bike.feature.regions.screen.RegionsScreen()
            }
            composable("settings") {
                com.twocircle.bike.ui.screens.SettingsScreen()
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
    FEED("feed", com.twocircle.bike.designsystem.R.string.nav_feed, Icons.Outlined.Group),
    PROFILE("profile", com.twocircle.bike.designsystem.R.string.nav_profile, Icons.Outlined.Person),
}
