package com.twocircle.bike.feature.tracks.screen

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twocircle.bike.common.format.Format
import com.twocircle.bike.designsystem.components.TelemetryStat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date
import java.util.Locale

/**
 * Track detail screen — single ride review + GPX export.
 *
 * Loads summary + raw points on entry via [TracksViewModel.loadDetail]. The export
 * button writes a GPX file via FileProvider and launches ACTION_SEND so the rider can
 * share it to Strava, email, cloud drive, etc.
 *
 * v1 deliberately doesn't render the track polyline here — that needs the MapLibre
 * overlay work which lands with map-route integration. For now the detail screen shows
 * the same telemetry as the live HUD plus the export affordance.
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(trackId) { viewModel.loadDetail(trackId) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val s = state) {
            TrackDetailUiState.Loading -> Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) { CircularProgressIndicator() }
            TrackDetailUiState.NotFound -> Text(
                "Track not found.",
                color = MaterialTheme.colorScheme.error,
            )
            is TrackDetailUiState.Error -> Text(
                "Couldn't load track: ${s.failure.javaClass.simpleName}",
                color = MaterialTheme.colorScheme.error,
            )
            is TrackDetailUiState.Loaded -> {
                val summary = s.detail.summary
                Text(summary.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = formatDate(summary.startedAtMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )

                HorizontalDivider()

                TelemetryBreakdown(
                    distance = summary.distanceMeters,
                    durationSec = summary.movingSeconds,
                    ascent = summary.ascentMeters,
                    descent = summary.descentMeters,
                    avgSpeed = summary.avgSpeedMps,
                    pointCount = s.detail.points.size,
                )

                Button(
                    onClick = {
                        scope.launch {
                            val uri = viewModel.exportGpx(trackId)
                            if (uri != null) {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/gpx+xml"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Share GPX"))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.IosShare, contentDescription = null)
                    Text("  Export GPX")
                }

                Button(
                    onClick = {
                        viewModel.delete(trackId)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete track") }
            }
        }
    }
}

@Composable
private fun TelemetryBreakdown(
    distance: Double,
    durationSec: Long,
    ascent: Double,
    descent: Double,
    avgSpeed: Double,
    pointCount: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TelemetryStat(Format.distance(distance), "distance")
                TelemetryStat(Format.duration(Duration.ofSeconds(durationSec)), "moving")
                TelemetryStat(Format.speed(avgSpeed), "avg")
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TelemetryStat(Format.elevation(ascent), "ascent", valueColor = MaterialTheme.colorScheme.primary)
                TelemetryStat(Format.elevation(-descent), "descent")
                TelemetryStat("$pointCount", "points")
            }
        }
    }
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))
