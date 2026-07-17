package com.twocircle.bike.feature.map.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import com.twocircle.bike.feature.map.model.MapUiState
import com.twocircle.bike.feature.map.model.MapViewModel
import com.twocircle.bike.feature.map.view.BikeMap

/**
 * Map screen — the app's home tab.
 *
 * Branches on [MapUiState]:
 * - NoRegion → prompt to download a region (the regions screen is reached via bottom nav).
 * - Loading → spinner.
 * - Ready → render [BikeMap] with the generated style JSON.
 * - Error → show the typed failure with a retry button.
 *
 * The map fills the screen; overlays (telemetry, route controls) are added by the
 * tracking and routing features in later steps, layered on top of [BikeMap].
 */
@Composable
fun MapScreen(
    onOpenSearch: () -> Unit = {},
    onOpenRide: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when (val s = state) {
            is MapUiState.NoRegion -> NoRegionPrompt(modifier = Modifier.align(Alignment.Center))
            MapUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is MapUiState.Ready -> {
                BikeMap(
                    styleJson = s.styleJson,
                    initialCamera = s.initialCamera,
                    modifier = Modifier.fillMaxSize(),
                )
                // Floating search button — opens the offline search screen.
                FloatingActionButton(
                    onClick = onOpenSearch,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                    )
                }
                // Start-ride button — bottom end.
                FloatingActionButton(
                    onClick = onOpenRide,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "Start ride",
                    )
                }
            }
            is MapUiState.Error -> ErrorState(
                failure = s.failure,
                onRetry = viewModel::refresh,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun NoRegionPrompt(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Public,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "No offline region downloaded yet",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Open the Regions tab and download a region to start planning rides offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorState(
    failure: com.twocircle.bike.common.outcome.Failure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Couldn't load the map",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Text(
            text = failure.javaClass.simpleName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text("Retry") }
    }
}
