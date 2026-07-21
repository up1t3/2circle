package com.twocircle.bike.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.twocircle.bike.designsystem.R
import com.twocircle.bike.designsystem.theme.BikeColors

/**
 * A coloured swatch + label pair, used both on the map legend and in route previews.
 * Single source of "what colour means what surface" in the UI.
 */
@Composable
fun SurfaceSwatch(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Compact legend listing every surface category and its colour. */
@Composable
fun SurfaceLegend(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SurfaceSwatch(stringResource(R.string.surface_asphalt), BikeColors.SurfaceAsphalt)
        SurfaceSwatch(stringResource(R.string.surface_compacted), BikeColors.SurfaceCompacted)
        SurfaceSwatch(stringResource(R.string.surface_dirt), BikeColors.SurfaceDirt)
        SurfaceSwatch(stringResource(R.string.surface_sand), BikeColors.SurfaceSand)
        SurfaceSwatch(stringResource(R.string.surface_grass), BikeColors.SurfaceGrass)
        SurfaceSwatch(stringResource(R.string.surface_rock), BikeColors.SurfaceRock)
        SurfaceSwatch(stringResource(R.string.surface_unknown), BikeColors.SurfaceUnknown)
    }
}
