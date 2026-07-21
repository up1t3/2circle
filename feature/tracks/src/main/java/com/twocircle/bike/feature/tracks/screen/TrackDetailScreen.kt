package com.twocircle.bike.feature.tracks.screen

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twocircle.bike.common.format.Format
import com.twocircle.bike.designsystem.R
import com.twocircle.bike.designsystem.components.ElevationProfile
import com.twocircle.bike.designsystem.components.StatGrid
import com.twocircle.bike.designsystem.components.StatItem
import com.twocircle.bike.designsystem.l10n.LocalUnitStrings
import com.twocircle.bike.designsystem.l10n.messageRes
import com.twocircle.bike.domain.model.Coord
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date
import java.util.Locale

/**
 * Track detail screen — single ride review + GPX export + elevation profile.
 */
@Composable
fun TrackDetailScreen(
    trackId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TracksViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.detailState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(trackId) { viewModel.loadDetail(trackId) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val s = state) {
            TrackDetailUiState.Loading -> Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) { CircularProgressIndicator() }

            TrackDetailUiState.NotFound -> Text(
                stringResource(R.string.track_detail_not_found),
                color = MaterialTheme.colorScheme.error,
            )

            is TrackDetailUiState.Error -> Text(
                stringResource(s.failure.messageRes()),
                color = MaterialTheme.colorScheme.error,
            )

            is TrackDetailUiState.Loaded -> {
                val summary = s.detail.summary
                val units = LocalUnitStrings.current

                Column {
                    Text(summary.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = formatDate(summary.startedAtMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }

                HorizontalDivider()

                // Мини-карта превью трека (200dp)
                if (s.detail.points.size >= 2) {
                    TrackPreviewMap(
                        points = s.detail.points.map { Coord(lat = it.lat, lon = it.lon) },
                    )
                }

                // Сетка статистических параметров 2×3
                val statItems = listOf(
                    StatItem(
                        value = Format.distance(summary.distanceMeters, meter = units.meter, kilometer = units.kilometer),
                        label = stringResource(R.string.stat_caption_distance),
                    ),
                    StatItem(
                        value = Format.duration(
                            Duration.ofSeconds(summary.movingSeconds),
                            second = units.second,
                            minute = units.minute,
                            hour = units.hour,
                        ),
                        label = stringResource(R.string.stat_caption_moving),
                    ),
                    StatItem(
                        value = Format.speed(summary.avgSpeedMps, kmh = units.kmh),
                        label = stringResource(R.string.stat_caption_avg),
                    ),
                    StatItem(
                        value = Format.elevation(summary.ascentMeters, meter = units.meter),
                        label = stringResource(R.string.stat_caption_ascent),
                        valueColor = MaterialTheme.colorScheme.primary,
                    ),
                    StatItem(
                        value = Format.elevation(-summary.descentMeters, meter = units.meter),
                        label = stringResource(R.string.stat_caption_descent),
                    ),
                    StatItem(
                        value = "${s.detail.points.size}",
                        label = stringResource(R.string.stat_caption_points),
                    )
                )
                StatGrid(items6 = statItems)

                // Профиль высот (Canvas 120dp)
                val elevations = s.detail.points.mapNotNull { it.ele }
                if (elevations.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.route_section_elevations),
                        style = MaterialTheme.typography.titleMedium
                    )
                    ElevationProfile(elevations = elevations, height = 120.dp)
                }

                Spacer(modifier = Modifier.padding(top = 8.dp))

                // Кнопки действий: Экспорт GPX и Удалить
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val uri = viewModel.exportGpx(trackId)
                            if (uri != null) {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/gpx+xml"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        share,
                                        context.getString(R.string.track_detail_share_chooser_title),
                                    ),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.IosShare, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.track_detail_action_export_gpx))
                }

                TextButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.track_detail_action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Диалог подтверждения удаления
                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text(stringResource(R.string.track_detail_delete_confirm_title)) },
                        text = { Text(stringResource(R.string.track_detail_delete_confirm_body)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showDeleteDialog = false
                                    viewModel.delete(trackId)
                                    onBack()
                                }
                            ) {
                                Text(
                                    stringResource(R.string.action_delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))
