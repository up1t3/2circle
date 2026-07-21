package com.twocircle.bike.feature.routing.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.twocircle.bike.designsystem.components.ElevationProfile
import com.twocircle.bike.designsystem.components.SurfaceBreakdownBar
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twocircle.bike.common.format.Format
import com.twocircle.bike.designsystem.R
import com.twocircle.bike.designsystem.l10n.LocalUnitStrings
import com.twocircle.bike.designsystem.l10n.displayNameRes
import com.twocircle.bike.designsystem.l10n.messageRes
import com.twocircle.bike.domain.model.RoutingProfile
import com.twocircle.bike.domain.model.Waypoint
import java.time.Duration

/**
 * Route Builder screen.
 *
 * Shows an ordered list of waypoints from the shared [RouteDraftRepository]
 * (a `@Singleton`), a profile selector, and the planned-route preview when
 * computed. Waypoints persist across navigation — they survive back-stack
 * pops and screen switches because the repository outlives any individual
 * ViewModel or NavBackStackEntry.
 *
 * Adding waypoints happens from other screens (Search "To route", Map
 * long-press, POI "Add to route") which call the repository directly.
 * The user can then switch to this screen to review, reorder, and plan.
 */
@Composable
fun RouteBuilderScreen(
    modifier: Modifier = Modifier,
    onOpenSavedRoutes: () -> Unit = {},
    viewModel: RouteBuilderViewModel = hiltViewModel(),
) {
    val waypoints by viewModel.waypoints.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val planState by viewModel.planState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.route_builder_title),
                style = MaterialTheme.typography.titleLarge,
            )
            androidx.compose.material3.TextButton(onClick = onOpenSavedRoutes) {
                Text(stringResource(R.string.saved_routes_title))
            }
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(bottom = 8.dp))

        ProfileSelector(current = profile, onSelect = viewModel::setProfile)

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        WaypointList(
            waypoints = waypoints,
            planState = planState,
            onRemove = viewModel::removeWaypoint,
            onMove = viewModel::moveWaypoint,
            modifier = Modifier.weight(1f),
        )
        PlanButton(
            canPlan = waypoints.size >= 2,
            isPlanning = planState is PlanState.Planning,
            isPlanned = planState is PlanState.Planned,
            onPlan = viewModel::planRoute,
        )

        // Save button — visible when a route is planned. Persists the route to Room
        // so it survives app restart and can be re-loaded from "Saved Routes".
        if (planState is PlanState.Planned) {
            androidx.compose.material3.OutlinedButton(
                onClick = { viewModel.saveCurrentRoute() },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(com.twocircle.bike.designsystem.R.string.route_action_save))
            }
        }
    }
}

@Composable
private fun ProfileSelector(current: RoutingProfile, onSelect: (RoutingProfile) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoutingProfile.entries.forEach { p ->
            FilterChip(
                selected = p == current,
                onClick = { onSelect(p) },
                label = { Text(stringResource(p.displayNameRes())) },
            )
        }
    }
}

@Composable
private fun WaypointList(
    waypoints: List<Waypoint>,
    planState: PlanState,
    onRemove: (com.twocircle.bike.domain.model.WaypointId) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (waypoints.isEmpty()) {
        com.twocircle.bike.designsystem.components.EmptyState(
            illustration = com.twocircle.bike.designsystem.R.drawable.empty_no_tracks,
            title = stringResource(R.string.route_empty_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 4.dp)) {
        // Подсказка, если точек меньше 2
        if (waypoints.size < 2) {
            item {
                Text(
                    text = stringResource(R.string.route_need_more_hint),
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        itemsIndexed(waypoints, key = { _, wp -> wp.id.value }) { index, wp ->
            WaypointRow(
                waypoint = wp,
                index = index,
                maxIndex = waypoints.lastIndex,
                onRemove = { onRemove(wp.id) },
                onMove = onMove
            )
            HorizontalDivider()
        }
        // Отображение состояния планирования
        when (planState) {
            is PlanState.Planned -> item { RoutePreviewCard(planState.route) }
            is PlanState.Planning -> item { PlanningRow() }
            is PlanState.Error -> item { ErrorRow(planState.failure) }
            PlanState.Idle -> Unit
        }
    }
}

@Composable
private fun WaypointRow(
    waypoint: Waypoint,
    index: Int,
    maxIndex: Int,
    onRemove: () -> Unit,
    onMove: (Int, Int) -> Unit
) {
    var dragOffsetY by remember { mutableStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { 72.dp.toPx() }
    val animatedOffsetY by animateFloatAsState(targetValue = dragOffsetY)

    val roleColor = when (waypoint.role) {
        Waypoint.Role.Start -> MaterialTheme.colorScheme.primary
        Waypoint.Role.End -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val roleTextColor = when (waypoint.role) {
        Waypoint.Role.Start -> MaterialTheme.colorScheme.onPrimary
        Waypoint.Role.End -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
            .graphicsLayer {
                shadowElevation = if (dragOffsetY != 0f) 8.dp.toPx() else 0f
            }
            .background(if (dragOffsetY != 0f) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = stringResource(R.string.route_cd_drag_handle),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier
                .padding(start = 4.dp)
                .size(24.dp)
                .pointerInput(index, maxIndex) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount.y
                            if (dragOffsetY > rowHeightPx && index < maxIndex) {
                                onMove(index, index + 1)
                                dragOffsetY -= rowHeightPx
                            } else if (dragOffsetY < -rowHeightPx && index > 0) {
                                onMove(index, index - 1)
                                dragOffsetY += rowHeightPx
                            }
                        },
                        onDragEnd = { dragOffsetY = 0f },
                        onDragCancel = { dragOffsetY = 0f }
                    )
                }
        )

        Icon(
            imageVector = Icons.Outlined.Place,
            contentDescription = null,
            tint = roleColor,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = waypoint.name ?: stringResource(
                    R.string.route_waypoint_coord_fmt,
                    waypoint.coord.lat,
                    waypoint.coord.lon,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = roleColor,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(waypoint.role.displayNameRes()),
                        style = MaterialTheme.typography.labelSmall,
                        color = roleTextColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                
                Text(
                    text = "· ${stringResource(waypoint.source.displayNameRes())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.cd_route_remove_waypoint),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun RoutePreviewCard(route: com.twocircle.bike.domain.model.Route) {
    val units = LocalUnitStrings.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.route_preview_title), style = MaterialTheme.typography.titleMedium)
            
            // Двухколоночная сетка для вывода статистики
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.stat_distance),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = Format.distance(route.distanceMeters, meter = units.meter, kilometer = units.kilometer),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.stat_time),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = Format.duration(
                                Duration.ofSeconds(route.plannedSeconds),
                                second = units.second,
                                minute = units.minute,
                                hour = units.hour,
                            ),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.stat_ascent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "↑ ",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = Format.elevation(route.ascentMeters, meter = units.meter),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.stat_descent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "↓ ",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = Format.elevation(-route.descentMeters, meter = units.meter),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Полоса типов дорожного покрытия
            Text(
                text = stringResource(R.string.route_section_surfaces),
                style = MaterialTheme.typography.titleSmall
            )
            SurfaceBreakdownBar(surfaceBreakdown = route.surfaceBreakdown)

            HorizontalDivider()

            // График высот
            Text(
                text = stringResource(R.string.route_section_elevations),
                style = MaterialTheme.typography.titleSmall
            )
            ElevationProfile(
                elevations = route.geometry.mapNotNull { it.ele },
                height = 80.dp
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PlanningRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
        Text(stringResource(R.string.route_planning_status), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorRow(failure: com.twocircle.bike.common.outcome.Failure) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.route_error_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(failure.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun PlanButton(
    canPlan: Boolean,
    isPlanning: Boolean,
    isPlanned: Boolean,
    onPlan: () -> Unit,
) {
    // Четыре состояния согласно спецификации §5.5.4:
    // Disabled — canPlan=false: серый фон, текст-подсказка
    // Enabled — canPlan=true, не planned: primary фон, "Построить маршрут"
    // Planning — isPlanning: primary фон, спиннер + "Прокладка…"
    // Planned — isPlanned: primary фон, "Перестроить"
    val buttonText = when {
        isPlanning -> R.string.route_action_planning
        isPlanned -> R.string.route_replan
        canPlan -> R.string.route_action_plan
        else -> R.string.route_need_more_hint
    }
    Button(
        onClick = onPlan,
        enabled = canPlan && !isPlanning,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        if (isPlanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp).padding(end = 8.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        }
        Text(stringResource(buttonText))
    }
}
