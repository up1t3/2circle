package com.twocircle.bike.feature.regions.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.twocircle.bike.data.db.entity.RegionInstallState
import com.twocircle.bike.feature.regions.download.DownloadState

/**
 * Regions screen — the "Regions" bottom-nav tab.
 *
 * Shows the catalog merged with local install state. Each row offers download (when not
 * installed), progress (when downloading), or delete (when installed). The empty state
 * prompts the user to fetch the catalog.
 */
@Composable
fun RegionsScreen(
    modifier: Modifier = Modifier,
    viewModel: RegionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Regions", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = viewModel::refresh) { Text("Refresh") }
        }

        when (val s = state) {
            RegionsUiState.Loading -> Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) { CircularProgressIndicator() }
            RegionsUiState.NoRegion -> EmptyState()
            is RegionsUiState.Error -> ErrorText(s.failure)
            is RegionsUiState.Loaded -> RegionList(
                rows = s.rows,
                progress = s.progress,
                onDownload = { row -> row.entry?.let { viewModel.download(it) } },
                onDelete = { row -> viewModel.delete(row.id) },
            )
        }
    }
}

@Composable
private fun RegionList(
    rows: List<RegionRow>,
    progress: Map<String, com.twocircle.bike.feature.regions.download.DownloadProgress>,
    onDownload: (RegionRow) -> Unit,
    onDelete: (RegionRow) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows, key = { it.id }) { row ->
            RegionCard(
                row = row,
                progress = progress[row.id],
                onDownload = { onDownload(row) },
                onDelete = { onDelete(row) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun RegionCard(
    row: RegionRow,
    progress: com.twocircle.bike.feature.regions.download.DownloadProgress?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${Format.fileSize(row.sizeBytes)}  ·  v${row.version}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            when (row.installState) {
                RegionInstallState.Installed -> OutlinedButton(onClick = onDelete) { Text("Delete") }
                RegionInstallState.NotInstalled -> Button(onClick = onDownload) { Text("Download") }
                RegionInstallState.Failed -> Button(onClick = onDownload) { Text("Retry") }
                else -> Text(
                    row.installState.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        progress?.let { p ->
            val label = when (p.state) {
                DownloadState.Downloading -> "Downloading… ${(p.fraction * 100).toInt()}%"
                DownloadState.Verifying -> "Verifying…"
                DownloadState.Extracting -> "Extracting…"
                DownloadState.Installed -> "Installed"
                DownloadState.Failed -> "Failed: ${p.error ?: "unknown"}"
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 6.dp),
                color = if (p.state == DownloadState.Failed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
            if (p.state == DownloadState.Downloading) {
                LinearProgressIndicator(
                    progress = { p.fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Text(
        text = "No regions available. Tap Refresh to fetch the catalog.",
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
}

@Composable
private fun ErrorText(failure: com.twocircle.bike.common.outcome.Failure) {
    Text(
        text = "Couldn't load regions: ${failure.javaClass.simpleName}",
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.error,
    )
}
