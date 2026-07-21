package com.twocircle.bike.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddLocation
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.twocircle.bike.designsystem.R

/**
 * Раскрывающийся кластер кнопок (FAB Cluster).
 *
 * Состоит из главной кнопки управления (FAB), при нажатии на которую анимированно
 * раскрываются вспомогательные кнопки: "Начать поездку", "Навигация" и "Добавить точку".
 * Анимация реализована через смещение (offset), масштабирование (scale) и поворот (rotate).
 */
@Composable
fun FabCluster(
    onStartRide: () -> Unit,
    onNavigate: () -> Unit,
    onAddWaypoint: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val transition = updateTransition(targetState = expanded, label = "КластерFAB")

    // Анимация вращения главной кнопки (плюс превращается в крестик)
    val rotation by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) },
        label = "ВращениеГлавной"
    ) { state ->
        if (state) 135f else 0f
    }

    // Анимации для кнопки "Начать поездку" (выдвигается строго вверх, задержка 0ms)
    val startRideOffset by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 250, delayMillis = 0, easing = FastOutSlowInEasing) },
        label = "СмещениеСтарт"
    ) { state ->
        if (state) -180f else 0f
    }
    val startRideScale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 200, delayMillis = 0, easing = FastOutSlowInEasing) },
        label = "МасштабСтарт"
    ) { state ->
        if (state) 1f else 0f
    }

    // Анимации для кнопки "Навигация" (выдвигается по диагонали, задержка 50ms)
    val navigateOffsetY by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 250, delayMillis = 50, easing = FastOutSlowInEasing) },
        label = "СмещениеНавигацияY"
    ) { state ->
        if (state) -120f else 0f
    }
    val navigateOffsetX by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 250, delayMillis = 50, easing = FastOutSlowInEasing) },
        label = "СмещениеНавигацияX"
    ) { state ->
        if (state) -120f else 0f
    }
    val navigateScale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 200, delayMillis = 50, easing = FastOutSlowInEasing) },
        label = "МасштабНавигация"
    ) { state ->
        if (state) 1f else 0f
    }

    // Анимации для кнопки "Добавить точку" (выдвигается влево, задержка 100ms)
    val waypointOffset by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 250, delayMillis = 100, easing = FastOutSlowInEasing) },
        label = "СмещениеТочка"
    ) { state ->
        if (state) -180f else 0f
    }
    val waypointScale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 200, delayMillis = 100, easing = FastOutSlowInEasing) },
        label = "МасштабТочка"
    ) { state ->
        if (state) 1f else 0f
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        // Кнопка 1: Начать поездку (верхняя в раскрытом состоянии)
        if (startRideScale > 0.01f) {
            FloatingActionButton(
                onClick = {
                    expanded = false
                    onStartRide()
                },
                modifier = Modifier
                    .offset(y = startRideOffset.dp)
                    .scale(startRideScale)
                    .size(48.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(R.string.cd_map_start_ride_fab)
                )
            }
        }

        // Кнопка 2: Навигация (диагональная)
        if (navigateScale > 0.01f) {
            FloatingActionButton(
                onClick = {
                    expanded = false
                    onNavigate()
                },
                modifier = Modifier
                    .offset(x = navigateOffsetX.dp, y = navigateOffsetY.dp)
                    .scale(navigateScale)
                    .size(48.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Outlined.Explore,
                    contentDescription = stringResource(R.string.map_action_navigate)
                )
            }
        }

        // Кнопка 3: Добавить путевую точку (левая)
        if (waypointScale > 0.01f) {
            FloatingActionButton(
                onClick = {
                    expanded = false
                    onAddWaypoint()
                },
                modifier = Modifier
                    .offset(x = waypointOffset.dp)
                    .scale(waypointScale)
                    .size(48.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddLocation,
                    contentDescription = stringResource(R.string.map_action_add_waypoint)
                )
            }
        }

        // Главная кнопка управления кластером
        FloatingActionButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.rotate(rotation),
            containerColor = if (expanded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
            contentColor = if (expanded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = if (expanded) Icons.Outlined.Close else Icons.Outlined.Add,
                contentDescription = if (expanded) stringResource(R.string.map_action_close_menu) else stringResource(R.string.map_action_open_menu)
            )
        }
    }
}
