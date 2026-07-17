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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twocircle.bike.common.format.Format
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

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Tracks", style = MaterialTheme.typography.titleLarge)
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
                text = "${Format.distance(item.distanceMeters)}  ·  ${Format.duration(Duration.ofSeconds(item.durationSeconds))}  ·  ${Format.elevation(item.ascentMeters)}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun Loading() {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) { CircularProgressIndicator() }
}

@Composable
private fun Empty() {
    Text(
        text = "No rides yet. Start one from the map screen.",
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
}

@Composable
private fun ErrorText(failure: com.twocircle.bike.common.outcome.Failure) {
    Text(
        text = "Couldn't load tracks: ${failure.javaClass.simpleName}",
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.error,
    )
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))
