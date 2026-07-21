package com.twocircle.bike.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twocircle.bike.designsystem.R
import com.twocircle.bike.designsystem.theme.BikeColors
import com.twocircle.bike.domain.model.Surface

/**
 * Расширения для маппинга enum [Surface] на ресурсы локализации и цвета.
 */
fun Surface.displayNameRes(): Int = when (this) {
    Surface.Asphalt -> R.string.surface_asphalt
    Surface.Compacted -> R.string.surface_compacted
    Surface.Dirt -> R.string.surface_dirt
    Surface.Sand -> R.string.surface_sand
    Surface.Grass -> R.string.surface_grass
    Surface.Rock -> R.string.surface_rock
    Surface.Unknown -> R.string.surface_unknown
}

fun Surface.color(): Color = when (this) {
    Surface.Asphalt -> BikeColors.SurfaceAsphalt
    Surface.Compacted -> BikeColors.SurfaceCompacted
    Surface.Dirt -> BikeColors.SurfaceDirt
    Surface.Sand -> BikeColors.SurfaceSand
    Surface.Grass -> BikeColors.SurfaceGrass
    Surface.Rock -> BikeColors.SurfaceRock
    Surface.Unknown -> BikeColors.SurfaceUnknown
}

/**
 * Горизонтальная цветная полоса пропорционального покрытия (Surface Breakdown Bar).
 *
 * Рисует сегменты дорожных покрытий на Canvas скругленным блоком, пропорционально их длине в маршруте.
 * Под полосой отрисовывается легенда с процентным соотношением.
 *
 * @param surfaceBreakdown Словарь соотношения поверхностей и их длины в метрах.
 * @param barHeight Высота полосы (8dp для компактных карточек, 12dp для детального просмотра).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SurfaceBreakdownBar(
    surfaceBreakdown: Map<Surface, Double>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 8.dp
) {
    val totalDistance = surfaceBreakdown.values.sum()
    if (totalDistance <= 0.0) return

    // Фильтруем и сортируем сегменты по порядку их объявления в enum, чтобы порядок цветов был фиксирован
    val segments = Surface.entries
        .mapNotNull { surface ->
            val dist = surfaceBreakdown[surface] ?: 0.0
            if (dist > 0.0) Pair(surface, dist) else null
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Отрисовка полосы покрытия
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(4.dp))
        ) {
            var currentX = 0f
            val widthPx = size.width
            val heightPx = size.height

            segments.forEach { (surface, distance) ->
                val fraction = distance / totalDistance
                val segmentWidth = (fraction * widthPx).toFloat()

                drawRect(
                    color = surface.color(),
                    topLeft = Offset(currentX, 0f),
                    size = Size(segmentWidth, heightPx)
                )
                currentX += segmentWidth
            }
        }

        // Вывод легенды под полосой
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = androidx.compose.ui.Alignment.Start),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            segments.forEach { (surface, distance) ->
                val percentage = ((distance / totalDistance) * 100).toInt()
                SurfaceSwatch(
                    label = "${percentage}% ${stringResource(surface.displayNameRes())}",
                    color = surface.color()
                )
            }
        }
    }
}
