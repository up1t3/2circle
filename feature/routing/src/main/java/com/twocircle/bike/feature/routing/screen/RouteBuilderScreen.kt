package com.twocircle.bike.feature.routing.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twocircle.bike.common.format.Format
import com.twocircle.bike.domain.model.RoutingProfile
import com.twocircle.bike.domain.model.Waypoint
import java.time.Duration

/**
 * Route Builder screen.
 *
 * Shows an ordered list of waypoints (Start/Via/End roles), a profile selector
 * (Touring/Road/MTB), and the planned-route preview when computed. Tap-to-remove and
 * profile switching are wired to the VM; drag-to-reorder lands in a later polish pass.
 *
 * Adding waypoints happens through the search screen and the map's long-press; both
 * navigate here with the chosen coord. For v1 we accept a single coord via [onAddCoord]
 * from the host; the search/map wiring is in the nav graph.
 */
@Composable
fun RouteBuilderScreen(
    pendingWaypoint: Pair<com.twocircle.bike.domain.model.Coord, String?>? = null,
    modifier: Modifier = Modifier,
    viewModel: RouteBuilderViewModel = hiltViewModel(),
) {
    // Consume a pending waypoint pushed from another screen (e.g. search → "To route").
    // LaunchedEffect with the coord identity as key ensures we add it once even across
    // recompositions; the VM dedupes via fresh WaypointId generation.
    androidx.compose.runtime.LaunchedEffect(pendingWaypoint?.first?.lat, pendingWaypoint?.first?.lon) {
        pendingWaypoint?.let { (coord, name) ->
            viewModel.addWaypointSearch(coord, name)
        }
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Route Builder",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        ProfileSelector(current = state.profile, onSelect = viewModel::setProfile)

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        WaypointList(
            state = state,
            onRemove = viewModel::removeWaypoint,
            modifier = Modifier.weight(1f),
        )

        PlanButton(state = state, onPlan = viewModel::planRoute)
    }
}

@Composable
private fun ProfileSelector(current: RoutingProfile, onSelect: (RoutingProfile) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoutingProfile.entries.forEach { p ->
            FilterChip(
                selected = p == current,
                onClick = { onSelect(p) },
                label = { Text(p.displayName) },
            )
        }
    }
}

@Composable
private fun WaypointList(
    state: RouteBuilderUiState,
    onRemove: (com.twocircle.bike.domain.model.WaypointId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val waypoints = when (state) {
        is RouteBuilderUiState.Draft -> state.waypoints
        is RouteBuilderUiState.Planning -> state.waypoints
        is RouteBuilderUiState.Planned -> state.waypoints
        is RouteBuilderUiState.Error -> state.waypoints
        RouteBuilderUiState.Idle -> emptyList()
    }
    if (waypoints.isEmpty()) {
        Text(
            text = "Add waypoints from the map (long-press) or search to start building a route.",
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        return
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 4.dp)) {
        items(waypoints, key = { it.id.value }) { wp ->
            WaypointRow(wp, onRemove = { onRemove(wp.id) })
            HorizontalDivider()
        }
        // Preview card pinned at the bottom of the list when route is planned.
        when (state) {
            is RouteBuilderUiState.Planned -> item { RoutePreviewCard(state.route) }
            is RouteBuilderUiState.Planning -> item { PlanningRow() }
            is RouteBuilderUiState.Error -> item { ErrorRow(state.failure) }
            else -> Unit
        }
    }
}

@Composable
private fun WaypointRow(waypoint: Waypoint, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = waypoint.name ?: String.format("%.4f, %.4f", waypoint.coord.lat, waypoint.coord.lon),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${waypoint.role} · ${waypoint.source}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Remove waypoint",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun RoutePreviewCard(route: com.twocircle.bike.domain.model.Route) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Planned route", style = MaterialTheme.typography.titleMedium)
            StatRow("Distance", Format.distance(route.distanceMeters))
            StatRow("Time", Format.duration(Duration.ofSeconds(route.plannedSeconds)))
            StatRow("Ascent", Format.elevation(route.ascentMeters))
            StatRow("Descent", Format.elevation(-route.descentMeters))
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
        Text("Planning route…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorRow(failure: com.twocircle.bike.common.outcome.Failure) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Couldn't plan route", style = MaterialTheme.typography.titleMedium)
            Text(
                text = failure.javaClass.simpleName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun PlanButton(state: RouteBuilderUiState, onPlan: () -> Unit) {
    val canPlan = when (state) {
        is RouteBuilderUiState.Draft -> state.waypoints.size >= 2
        is RouteBuilderUiState.Planned -> true // re-plan allowed after profile change
        is RouteBuilderUiState.Error -> true   // retry allowed
        else -> false
    }
    val isPlanning = state is RouteBuilderUiState.Planning
    Button(
        onClick = onPlan,
        enabled = canPlan && !isPlanning,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Text(if (isPlanning) "Planning…" else "Plan route")
    }
}
