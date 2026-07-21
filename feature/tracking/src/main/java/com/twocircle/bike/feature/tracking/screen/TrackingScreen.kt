package com.twocircle.bike.feature.tracking.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twocircle.bike.common.format.Format
import com.twocircle.bike.designsystem.R
import com.twocircle.bike.designsystem.components.TelemetryStat
import com.twocircle.bike.designsystem.l10n.LocalUnitStrings
import com.twocircle.bike.feature.tracking.model.TrackingState
import com.twocircle.bike.feature.tracking.service.LocationForegroundService
import java.time.Duration

/**
 * Live ride HUD.
 *
 * Shows the active telemetry (distance / time / speed / ascent) and the start/stop
 * controls. Start/stop talk to [LocationForegroundService] via intents — the activity
 * never directly drives the controller, because the controller's lifecycle belongs to
 * the service (which survives activity recreation).
 *
 * Telemetry reads from the controller's StateFlow, which the service updates on every
 * accepted sample. The HUD never touches GPS or the DB directly — it's a pure view.
 */
@Composable
fun TrackingScreen(
    modifier: Modifier = Modifier,
    viewModel: TrackingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.tracking_title_ride),
            style = MaterialTheme.typography.titleLarge,
        )

        TelemetryCard(state = state)

        Spacer(Modifier.weight(1f))

        Controls(
            isRecording = state.isRecording,
            onStart = { LocationForegroundService.start(context) },
            onStop = { LocationForegroundService.stop(context) },
        )
    }
}

@Composable
private fun TelemetryCard(state: TrackingState) {
    val agg = state.aggregates
    val units = LocalUnitStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TelemetryStat(
                    value = Format.distance(agg.distanceMeters, meter = units.meter, kilometer = units.kilometer),
                    caption = stringResource(R.string.stat_caption_distance),
                )
                TelemetryStat(
                    value = Format.speed(agg.avgSpeedMps, kmh = units.kmh),
                    caption = stringResource(R.string.stat_caption_avg),
                )
                TelemetryStat(
                    value = Format.duration(
                        Duration.ofSeconds(agg.movingSeconds),
                        second = units.second,
                        minute = units.minute,
                        hour = units.hour,
                    ),
                    caption = stringResource(R.string.stat_caption_moving),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TelemetryStat(
                    value = Format.elevation(agg.ascentMeters, meter = units.meter),
                    caption = stringResource(R.string.stat_caption_ascent),
                    valueColor = MaterialTheme.colorScheme.primary,
                )
                TelemetryStat(
                    value = Format.speed(agg.maxSpeedMps, kmh = units.kmh),
                    caption = stringResource(R.string.stat_caption_max),
                )
                TelemetryStat(
                    value = "${agg.pointCount}",
                    caption = stringResource(R.string.stat_caption_points),
                )
            }
        }
    }
}

@Composable
private fun Controls(isRecording: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isRecording) {
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Outlined.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tracking_action_stop))
            }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tracking_action_start))
            }
        }
    }
}
