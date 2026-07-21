package com.twocircle.bike.feature.poi.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.twocircle.bike.designsystem.R
import com.twocircle.bike.feature.poi.model.Poi

/**
 * Detail card shown when a POI marker is tapped on the map.
 *
 * Displays: name, category (localized), coordinates, and a "Add to route" button.
 * This is the "what am I looking at?" affordance that was missing — without it the
 * user saw markers with names but couldn't act on them or learn more.
 *
 * Hosts as a ModalBottomSheet content; the host controls visibility via
 * [PoiViewModel.selectedPoi].
 */
@Composable
fun PoiDetailSheet(
    poi: Poi,
    onAddToRoute: (Poi) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Name — the main identifier.
        Text(
            text = poi.name,
            style = MaterialTheme.typography.titleLarge,
        )

        HorizontalDivider()

        // Category with icon.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = stringResource(R.string.poi_detail_category),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Text(
                    text = stringResource(poi.kind.displayNameRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Coordinates — useful for cross-referencing with other apps.
        Text(
            text = stringResource(R.string.poi_detail_coords, poi.lat, poi.lon),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // Actions.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Button(
                onClick = { onAddToRoute(poi) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.poi_action_add_to_route))
            }
        }
    }
}
