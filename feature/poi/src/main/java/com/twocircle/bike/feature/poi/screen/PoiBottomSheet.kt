package com.twocircle.bike.feature.poi.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twocircle.bike.designsystem.R
import com.twocircle.bike.feature.poi.model.Poi
import com.twocircle.bike.feature.poi.model.PoiCategory

/**
 * Modal bottom sheet content for the POI layer.
 *
 * Shows category chips (multi-select) at the top and the resulting POI list below.
 * Tapping a chip adds/removes the category from the query; the map markers update in
 * real time because the map screen subscribes to the same [PoiViewModel.state].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PoiBottomSheet(
    onPoiSelected: (Poi) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PoiViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = stringResource(R.string.poi_sheet_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.poi_sheet_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PoiCategory.entries.forEach { category ->
                FilterChip(
                    selected = category in state.selectedCategories,
                    onClick = { viewModel.toggleCategory(category) },
                    label = { Text(stringResource(category.displayNameRes)) },
                )
            }
        }

        if (state.selectedCategories.isNotEmpty()) {
            Text(
                text = stringResource(R.string.poi_count_fmt, state.pois.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }

        when {
            state.pois.isEmpty() && state.selectedCategories.isNotEmpty() -> {
                com.twocircle.bike.designsystem.components.EmptyState(
                    illustration = com.twocircle.bike.designsystem.R.drawable.empty_no_poi,
                    title = stringResource(R.string.poi_empty_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.pois.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.pois, key = { it.id }) { poi ->
                    PoiRow(poi, onClick = { onPoiSelected(poi) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PoiRow(poi: Poi, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .let { it },
    ) {
        Text(
            text = poi.name,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(poi.kind.displayNameRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
