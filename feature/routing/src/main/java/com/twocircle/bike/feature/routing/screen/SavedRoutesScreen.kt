package com.twocircle.bike.feature.routing.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.twocircle.bike.data.db.entity.RoutePlanEntity
import com.twocircle.bike.data.repository.RoutePlansRepository
import com.twocircle.bike.designsystem.R
import com.twocircle.bike.designsystem.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lists saved route plans from the Room database.
 *
 * Each row shows the route name, distance, and creation date. Tapping a row
 * loads it into the Route Builder (Phase 0.4 will add full load/restore).
 * Delete removes it permanently.
 *
 * This screen closes the critical gap where planned routes were never persisted
 * — the entire route_plans/waypoints/segments schema was dead code until this
 * screen + RouteBuilderViewModel.saveCurrentRoute() were wired.
 */
@HiltViewModel
class SavedRoutesViewModel @Inject constructor(
    private val plansRepository: RoutePlansRepository,
) : androidx.lifecycle.ViewModel() {

    val plans = plansRepository.allPlansFlow()

    fun deletePlan(id: String) {
        viewModelScope.launch { plansRepository.deletePlan(id) }
    }
}

@Composable
fun SavedRoutesScreen(
    onPlanSelected: (RoutePlanEntity) -> Unit = {},
    viewModel: SavedRoutesViewModel = hiltViewModel(),
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle(emptyList())

    if (plans.isEmpty()) {
        EmptyState(
            illustration = com.twocircle.bike.designsystem.R.drawable.empty_no_tracks,
            title = stringResource(R.string.saved_routes_empty),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(plans, key = { it.id }) { plan ->
            SavedRouteRow(
                plan = plan,
                onClick = { onPlanSelected(plan) },
                onDelete = { viewModel.deletePlan(plan.id) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SavedRouteRow(
    plan: RoutePlanEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Route,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plan.name,
                style = MaterialTheme.typography.titleMedium,
            )
            if (plan.distanceMeters > 0) {
                Text(
                    text = "${"%.1f".format(plan.distanceMeters / 1000)} km · ${plan.profile}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
