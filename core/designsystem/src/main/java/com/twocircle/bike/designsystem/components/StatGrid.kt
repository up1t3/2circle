package com.twocircle.bike.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Элемент сетки статистики.
 */
data class StatItem(
    val value: String,
    val label: String,
    val valueColor: Color? = null,
)

/**
 * Сетка статистических показателей 2×3 (StatGrid).
 *
 * Используется на экране деталей трека [TrackDetailScreen] и в предпросмотре маршрута [RoutePreviewCard].
 *
 * @param items6 Список из 6 статистических параметров (2 строки по 3 элемента).
 */
@Composable
fun StatGrid(
    items6: List<StatItem>,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Первая строка (3 элемента)
            if (items6.size >= 3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    items6.take(3).forEach { item ->
                        TelemetryStat(
                            value = item.value,
                            caption = item.label,
                            valueColor = item.valueColor ?: MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Разделитель строк
            if (items6.size > 3) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                // Вторая строка (следующие 3 элемента)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    items6.drop(3).take(3).forEach { item ->
                        TelemetryStat(
                            value = item.value,
                            caption = item.label,
                            valueColor = item.valueColor ?: MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
