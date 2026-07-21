package com.twocircle.bike.feature.map.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twocircle.bike.common.format.Format
import com.twocircle.bike.designsystem.R
import com.twocircle.bike.designsystem.l10n.LocalUnitStrings
import java.time.Duration

/**
 * Overlay-панель навигации поверх карты.
 *
 * Показывается когда NavigationState == Active. Содержит:
 * - Верхняя панель: иконка манёвра + текст инструкции + дистанция до манёвра
 * - Нижняя панель: ETA + дистанция до финиша + текущая скорость
 *
 * Появляется с slide-in сверху, исчезает с fade-out.
 *
 * Кнопка закрытия (X) останавливает навигацию.
 */
@Composable
fun NavigationOverlay(
    controller: NavigationController,
    modifier: Modifier = Modifier,
) {
    val navState by controller.uiState.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = navState.state is NavigationState.Active || navState.state is NavigationState.OffRoute,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Верхняя панель — инструкция поворота
            InstructionBar(
                state = navState,
                onClose = { controller.stopNavigation() },
            )

            // Нижняя панель — статистика поездки
            if (navState.state is NavigationState.Active) {
                NavigationStatsBar(state = navState)
            }

            // Off-route предупреждение
            if (navState.state is NavigationState.OffRoute) {
                OffRouteBanner()
            }
        }
    }
}

/**
 * Верхняя панель с текущей инструкцией.
 *
 * Иконка манёвра слева (крупная, 48dp) + текст инструкции + дистанция до манёвра.
 * Кнопка закрытия справа.
 */
@Composable
private fun InstructionBar(
    state: NavigationUiState,
    onClose: () -> Unit,
) {
    val instruction = state.currentInstruction ?: return
    val units = LocalUnitStrings.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Иконка манёвра
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = maneuverIcon(instruction.type),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }

            // Текст инструкции + дистанция
            Column(modifier = Modifier.weight(1f)) {
                // Дистанция до манёвра (крупно)
                if (state.distanceToNextManeuver > 0) {
                    Text(
                        text = Format.distance(state.distanceToNextManeuver, meter = units.meter, kilometer = units.kilometer),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                // Текст инструкции
                Text(
                    text = instruction.instructionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    maxLines = 2,
                )
            }

            // Кнопка закрытия навигации
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.navigation_stop),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/**
 * Нижняя панель со статистикой навигации.
 *
 * ETA · дистанция до финиша · текущая скорость
 */
@Composable
private fun NavigationStatsBar(state: NavigationUiState) {
    val units = LocalUnitStrings.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ETA
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = Format.duration(
                        Duration.ofSeconds(state.etaSeconds.coerceAtLeast(0)),
                        second = units.second,
                        minute = units.minute,
                        hour = units.hour,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.navigation_eta),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            // Дистанция до финиша
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = Format.distance(state.distanceToDestination, meter = units.meter, kilometer = units.kilometer),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.navigation_remaining),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            // Текущая скорость
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = Format.speed(state.currentSpeed, kmh = units.kmh),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.navigation_speed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/**
 * Баннер отклонения от маршрута.
 */
@Composable
private fun OffRouteBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        Text(
            text = stringResource(R.string.navigation_off_route),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}

/**
 * Маппинг типа манёвра на Material иконку.
 */
private fun maneuverIcon(type: ManeuverType): ImageVector = when (type) {
    ManeuverType.DEPART -> Icons.Filled.Navigation
    ManeuverType.STRAIGHT -> Icons.Filled.Straight
    ManeuverType.SLIGHT_RIGHT -> Icons.Filled.TurnSlightRight
    ManeuverType.RIGHT -> Icons.Filled.TurnRight
    ManeuverType.SHARP_RIGHT -> Icons.Filled.TurnRight
    ManeuverType.SLIGHT_LEFT -> Icons.Filled.TurnSlightLeft
    ManeuverType.LEFT -> Icons.Filled.TurnLeft
    ManeuverType.SHARP_LEFT -> Icons.Filled.TurnLeft
    ManeuverType.UTURN -> Icons.Filled.Undo
    ManeuverType.ARRIVE -> Icons.Filled.Flag
}
