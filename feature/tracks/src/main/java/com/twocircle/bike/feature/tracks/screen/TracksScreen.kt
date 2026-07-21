package com.twocircle.bike.feature.tracks.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twocircle.bike.common.format.Format
import com.twocircle.bike.designsystem.R
import com.twocircle.bike.designsystem.l10n.LocalUnitStrings
import com.twocircle.bike.designsystem.l10n.messageRes
import com.twocircle.bike.feature.tracks.model.TrackListItem
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date
import java.util.Locale

/**
 * Tracks list screen — the "Tracks" bottom-nav tab.
 *
 * Shows every recorded ride (live imports excluded in v1). Tapping a row opens the
 * detail screen for export / review.
 */
@Composable
fun TracksScreen(
    onTrackSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TracksViewModel = hiltViewModel(),
) {
    val state by viewModel.listState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    // SAF picker for GPX import. Opens the system file picker for .gpx files.
    val gpxLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importGpx(uri, context)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.tracks_title),
                style = MaterialTheme.typography.titleLarge,
            )
            androidx.compose.material3.TextButton(onClick = {
                gpxLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream"))
            }) {
                Text(stringResource(R.string.tracks_import_gpx))
            }
        }
        when (val s = state) {
            TracksUiState.Loading -> Loading()
            is TracksUiState.Loaded -> {
                if (s.items.isEmpty()) Empty()
                else TrackList(s.items, onTrackSelected)
            }
            is TracksUiState.Error -> ErrorText(s.failure)
        }
    }
}

@Composable
private fun TrackList(items: List<TrackListItem>, onSelect: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items, key = { it.id }) { item ->
            TrackRow(item, onClick = { onSelect(item.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun TrackRow(item: TrackListItem, onClick: () -> Unit) {
    val units = LocalUnitStrings.current
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDate(item.startedAtMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                text = stringResource(
                    R.string.tracks_row_stats_fmt,
                    Format.distance(item.distanceMeters, meter = units.meter, kilometer = units.kilometer),
                    Format.duration(
                        Duration.ofSeconds(item.durationSeconds),
                        second = units.second,
                        minute = units.minute,
                        hour = units.hour,
                    ),
                    Format.elevation(item.ascentMeters, meter = units.meter),
                ),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun Loading() {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(4) {
            com.twocircle.bike.designsystem.components.RideSkeletonRow()
        }
    }
}

@Composable
private fun Empty() {
    com.twocircle.bike.designsystem.components.EmptyState(
        illustration = com.twocircle.bike.designsystem.R.drawable.empty_no_tracks,
        title = stringResource(R.string.tracks_empty_hint),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorText(failure: com.twocircle.bike.common.outcome.Failure) {
    Text(
        text = stringResource(failure.messageRes()),
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.error,
    )
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))
