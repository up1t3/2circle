package com.twocircle.bike.feature.map.navigation

import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.Route
import com.twocircle.bike.domain.navigation.NavigationSink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Состояние активной навигации.
 */
sealed interface NavigationState {
    /** Навигация не активна. */
    data object Idle : NavigationState
    /** Навигация активна — есть маршрут и текущая позиция. */
    data object Active : NavigationState
    /** Райдер отклонился от маршрута — предлагается rerouting. */
    data object OffRoute : NavigationState
    /** Навигация завершена — достигли точки назначения. */
    data object Arrived : NavigationState
}

/**
 * UI-данные для navigation overlay.
 */
data class NavigationUiState(
    val state: NavigationState = NavigationState.Idle,
    val currentInstruction: TurnInstruction? = null,
    val nextInstruction: TurnInstruction? = null,
    val distanceToNextManeuver: Double = 0.0,
    val distanceToDestination: Double = 0.0,
    val etaSeconds: Long = 0L,
    val currentSpeed: Double = 0.0,
)

/**
 * Оркестратор turn-by-turn навигации.
 *
 * Принимает:
 * - Запланированный маршрут (Route) → генерирует инструкции через [TurnInstructionGenerator]
 * - GPS-позицию райдера (через [updatePosition])
 *
 * Вычисляет:
 * - Ближайшую точку на маршруте (snap-to-route)
 * - Дистанцию до следующего манёвра
 * - Дистанцию до финиша
 * - ETA (на основе плановой скорости BRouter)
 *
 * Озвучивает:
 * - Инструкции через [TtsController] при приближении к манёвру (< 100м)
 * - Предупреждение при отклонении от маршрута (> 50м от маршрута)
 *
 * Это @Singleton — переживает переключение экранов (как TrackingController).
 */
@Singleton
class NavigationController @Inject constructor(
    private val ttsController: TtsController,
) : NavigationSink {

    override fun isActive(): Boolean = _uiState.value.state.let {
        it is NavigationState.Active || it is NavigationState.OffRoute
    }

    override fun onLocationUpdate(lat: Double, lon: Double, speedMps: Float) {
        updatePosition(lat, lon, speedMps.toDouble())
    }

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    private var instructions: List<TurnInstruction> = emptyList()
    private var routeGeometry: List<Coord> = emptyList()
    private var currentInstructionIndex = 0
    private var totalDistance = 0.0
    private var plannedSeconds = 0L

    /** Радиус срабатывания инструкции (метры). */
    private val ANNOUNCE_RADIUS = 100.0

    /** Радиус раннего предупреждения (метры). */
    private val EARLY_ANNOUNCE_RADIUS = 300.0

    /** Максимальное отклонение от маршрута (метры) до OffRoute. */
    private val OFF_ROUTE_THRESHOLD = 50.0

    private var lastAnnouncedIndex = -1
    private var lastEarlyAnnouncedIndex = -1

    /**
     * Начать навигацию по маршруту.
     * Генерирует инструкции, инициализирует TTS, переводит в Active.
     */
    fun startNavigation(route: Route) {
        routeGeometry = route.geometry
        instructions = TurnInstructionGenerator.generateFromRoute(route)
        totalDistance = route.distanceMeters
        plannedSeconds = route.plannedSeconds
        currentInstructionIndex = 0
        lastAnnouncedIndex = -1
        lastEarlyAnnouncedIndex = -1

        ttsController.init()

        // Озвучить стартовую инструкцию
        if (instructions.isNotEmpty()) {
            ttsController.speakInstruction(instructions[0])
            lastAnnouncedIndex = 0
        }

        _uiState.value = NavigationUiState(
            state = NavigationState.Active,
            currentInstruction = instructions.firstOrNull(),
            nextInstruction = instructions.getOrNull(1),
            distanceToNextManeuver = instructions.firstOrNull()?.distanceFromPrevious ?: 0.0,
            distanceToDestination = totalDistance,
            etaSeconds = plannedSeconds,
        )

        Timber.i("Navigation started: %d instructions, %.1f km, %d s ETA",
            instructions.size, totalDistance / 1000, plannedSeconds)
    }

    /**
     * Обновить позицию райдера. Вызывается из TrackingController при каждом принятом GPS-сэмпле.
     *
     * Алгоритм:
     * 1. Найти ближайшую точку на маршруте
     * 2. Проверить отклонение → OffRoute если > 50м
     * 3. Вычислить distanceToNextManeuver
     * 4. Если < 300м и не предупреждали → раннее предупреждение
     * 5. Если < 100м и не озвучивали → озвучить
     * 6. Если достигли финиша → Arrived
     */
    fun updatePosition(lat: Double, lon: Double, speedMps: Double) {
        if (instructions.isEmpty() || routeGeometry.isEmpty()) return

        val pos = Coord(lat, lon)

        // Найти ближайшую точку на маршруте
        var minDist = Double.MAX_VALUE
        var nearestIdx = 0
        for (i in routeGeometry.indices) {
            val d = haversine(pos, routeGeometry[i])
            if (d < minDist) {
                minDist = d
                nearestIdx = i
            }
        }

        // Проверка отклонения
        if (minDist > OFF_ROUTE_THRESHOLD) {
            if (_uiState.value.state != NavigationState.OffRoute) {
                Timber.w("Off-route: %.0fm from route", minDist)
                _uiState.value = _uiState.value.copy(state = NavigationState.OffRoute)
                ttsController.speak("You have left the route. Recalculating.")
            }
            return
        } else if (_uiState.value.state == NavigationState.OffRoute) {
            _uiState.value = _uiState.value.copy(state = NavigationState.Active)
        }

        // Найти текущий и следующий манёвр
        var currentManeuverIdx = 0
        for (i in instructions.indices) {
            if (instructions[i].pointIndex <= nearestIdx) {
                currentManeuverIdx = i
            } else {
                break
            }
        }

        // Вычислить дистанцию до следующего манёвра
        val nextManeuver = instructions.getOrNull(currentManeuverIdx + 1)
        val distToNext = if (nextManeuver != null) {
            // Сумма расстояний от текущей позиции до следующего манёвра
            var d = haversine(pos, routeGeometry[nearestIdx])
            for (i in nearestIdx until nextManeuver.pointIndex) {
                d += haversine(routeGeometry[i], routeGeometry[i + 1])
            }
            d
        } else {
            // Нет следующего манёвра — дистанция до финиша
            var d = haversine(pos, routeGeometry[nearestIdx])
            for (i in nearestIdx until routeGeometry.lastIndex) {
                d += haversine(routeGeometry[i], routeGeometry[i + 1])
            }
            d
        }

        // Дистанция до финиша
        var distToDest = haversine(pos, routeGeometry[nearestIdx])
        for (i in nearestIdx until routeGeometry.lastIndex) {
            distToDest += haversine(routeGeometry[i], routeGeometry[i + 1])
        }

        // Раннее предупреждение (300м)
        if (nextManeuver != null &&
            distToNext <= EARLY_ANNOUNCE_RADIUS &&
            lastEarlyAnnouncedIndex != currentManeuverIdx + 1
        ) {
            lastEarlyAnnouncedIndex = currentManeuverIdx + 1
            // Тихое раннее уведомление
            Timber.d("Early announce maneuver %d at %.0fm", currentManeuverIdx + 1, distToNext)
        }

        // Основное озвучивание (100м)
        if (nextManeuver != null &&
            distToNext <= ANNOUNCE_RADIUS &&
            lastAnnouncedIndex != currentManeuverIdx + 1
        ) {
            lastAnnouncedIndex = currentManeuverIdx + 1
            ttsController.speakInstruction(nextManeuver)
            currentInstructionIndex = currentManeuverIdx + 1
            Timber.i("Announced maneuver %d: %s", currentManeuverIdx + 1, nextManeuver.instructionText)
        }

        // Проверка прибытия
        if (distToDest < 20.0) {
            _uiState.value = _uiState.value.copy(
                state = NavigationState.Arrived,
                distanceToDestination = 0.0,
                etaSeconds = 0L,
            )
            ttsController.speak("You have arrived at your destination.")
            Timber.i("Navigation complete: arrived")
            return
        }

        // Обновить UI state
        val remainingTime = if (speedMps > 1.0) {
            (distToDest / speedMps).toLong()
        } else {
            plannedSeconds
        }

        _uiState.value = NavigationUiState(
            state = NavigationState.Active,
            currentInstruction = nextManeuver ?: instructions[currentManeuverIdx],
            nextInstruction = instructions.getOrNull(currentManeuverIdx + 2),
            distanceToNextManeuver = distToNext,
            distanceToDestination = distToDest,
            etaSeconds = remainingTime,
            currentSpeed = speedMps,
        )
    }

    /**
     * Остановить навигацию.
     */
    fun stopNavigation() {
        ttsController.stop()
        _uiState.value = NavigationUiState(state = NavigationState.Idle)
        instructions = emptyList()
        routeGeometry = emptyList()
        Timber.i("Navigation stopped")
    }

    fun shutdown() {
        ttsController.shutdown()
    }

    /** Haversine distance (метры). */
    private fun haversine(a: Coord, b: Coord): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val x = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) *
            sin(dLon / 2).let { it * it }
        return r * 2 * atan2(sqrt(x), sqrt(1 - x))
    }
}
