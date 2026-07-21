package com.twocircle.bike.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twocircle.bike.designsystem.R

/**
 * График высот (Elevation Profile).
 *
 * Отрисовывает плавно сглаженную линию рельефа с вертикальным градиентом под ней,
 * а также подписями минимальной и максимальной высоты.
 *
 * @param elevations Список значений высоты в метрах.
 * @param height Высота Canvas элемента (по умолчанию 120.dp).
 */
@Composable
fun ElevationProfile(
    elevations: List<Double>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (elevations.isEmpty()) {
        Text(
            text = stringResource(R.string.route_no_elevation_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = modifier.padding(vertical = 12.dp)
        )
        return
    }

    val minEle = elevations.minOrNull() ?: 0.0
    val maxEle = elevations.maxOrNull() ?: 100.0
    val range = (maxEle - minEle).coerceAtLeast(10.0)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val width = size.width
            val canvasHeight = size.height
            val pointsCount = elevations.size
            val path = Path()
            val fillPath = Path()

            // Сетка высот (горизонтальные пунктирные/тонкие линии)
            val strokeWidthPx = 1.dp.toPx()
            val gridColor = lineColor.copy(alpha = 0.15f)
            
            // 3 горизонтальные направляющие линии (top, middle, bottom)
            drawLine(
                color = gridColor,
                start = Offset(0f, 0f),
                end = Offset(width, 0f),
                strokeWidth = strokeWidthPx
            )
            drawLine(
                color = gridColor,
                start = Offset(0f, canvasHeight / 2),
                end = Offset(width, canvasHeight / 2),
                strokeWidth = strokeWidthPx
            )
            drawLine(
                color = gridColor,
                start = Offset(0f, canvasHeight),
                end = Offset(width, canvasHeight),
                strokeWidth = strokeWidthPx
            )

            if (pointsCount >= 2) {
                elevations.forEachIndexed { index, ele ->
                    val x = (index.toFloat() / (pointsCount - 1).toFloat()) * width
                    val y = canvasHeight - (((ele - minEle) / range) * canvasHeight).toFloat()
                    
                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, canvasHeight)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    if (index == pointsCount - 1) {
                        fillPath.lineTo(x, canvasHeight)
                        fillPath.close()
                    }
                }

                // Отрисовка закрашенного градиента
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
                // Отрисовка основной линии высоты
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${minEle.toInt()} ${stringResource(R.string.format_unit_meter)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "${maxEle.toInt()} ${stringResource(R.string.format_unit_meter)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
