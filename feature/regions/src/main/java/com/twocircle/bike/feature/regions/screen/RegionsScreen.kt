package com.twocircle.bike.feature.regions.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import com.twocircle.bike.data.db.entity.RegionInstallState
import com.twocircle.bike.designsystem.R
import com.twocircle.bike.designsystem.l10n.LocalUnitStrings
import com.twocircle.bike.designsystem.l10n.messageRes
import com.twocircle.bike.feature.regions.download.DownloadProgress
import com.twocircle.bike.feature.regions.download.DownloadState

/**
 * Regions screen — the "Regions" bottom-nav tab.
 */
@Composable
fun RegionsScreen(
    modifier: Modifier = Modifier,
    viewModel: RegionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeRegionId by viewModel.activeRegionId.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.regions_title),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedButton(onClick = viewModel::refresh) {
                Text(stringResource(R.string.action_refresh))
            }
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
                activeRegionId = activeRegionId,
                onSelectActive = { viewModel.setActiveRegion(it) },
                onDownload = { row -> row.entry?.let { viewModel.download(it) } },
                onDelete = { row -> viewModel.delete(row.id) },
            )
        }
    }
}

@Composable
private fun RegionList(
    rows: List<RegionRow>,
    progress: Map<String, DownloadProgress>,
    activeRegionId: String?,
    onSelectActive: (String) -> Unit,
    onDownload: (RegionRow) -> Unit,
    onDelete: (RegionRow) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows, key = { it.id }) { row ->
            val isActive = (activeRegionId == row.id || (activeRegionId == null && rows.indexOf(row) == 0 && row.installState == RegionInstallState.Installed))
            RegionCard(
                row = row,
                progress = progress[row.id],
                isActive = isActive,
                onSelectActive = { onSelectActive(row.id) },
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
    progress: DownloadProgress?,
    isActive: Boolean,
    onSelectActive: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val units = LocalUnitStrings.current
    val isInstalled = row.installState == RegionInstallState.Installed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isInstalled) Modifier.clickable(onClick = onSelectActive) else Modifier)
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (isInstalled) {
                    RadioButton(
                        selected = isActive,
                        onClick = onSelectActive,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.name, style = MaterialTheme.typography.titleMedium)
                        if (isInstalled && isActive) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "✓ ${stringResource(R.string.regions_active_badge)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.regions_row_meta_fmt,
                            Format.fileSize(
                                row.sizeBytes,
                                gb = units.gb,
                                mb = units.mb,
                                kb = units.kb,
                                byte = units.byte,
                            ),
                            row.version,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            when (row.installState) {
                RegionInstallState.Installed -> OutlinedButton(onClick = onDelete) {
                    Text(stringResource(R.string.action_delete))
                }
                RegionInstallState.NotInstalled -> Button(
                    onClick = onDownload,
                    enabled = row.entry != null,
                ) {
                    Text(stringResource(R.string.action_download))
                }
                RegionInstallState.Failed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (row.entry != null) {
                        Button(onClick = onDownload) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                    OutlinedButton(onClick = onDelete) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
                else -> Text(
                    stringResource(row.installState.displayNameRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        progress?.let { p ->
            val animatedProgress by animateFloatAsState(targetValue = p.fraction, label = "ПрогрессСкачивания")
            val label = when (p.state) {
                DownloadState.Downloading -> stringResource(
                    R.string.region_dl_downloading_fmt,
                    (p.fraction * 100).toInt(),
                )
                DownloadState.Verifying -> stringResource(R.string.region_dl_verifying)
                DownloadState.Extracting -> stringResource(R.string.region_dl_extracting)
                DownloadState.Installed -> stringResource(R.string.region_dl_installed)
                DownloadState.Failed -> stringResource(
                    R.string.region_dl_failed_fmt,
                    p.error ?: stringResource(R.string.region_dl_failed_unknown),
                )
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
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Text(
        text = stringResource(R.string.regions_empty_hint),
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
