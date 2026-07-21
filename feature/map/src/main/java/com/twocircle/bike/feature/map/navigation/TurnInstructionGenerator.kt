package com.twocircle.bike.feature.map.navigation

import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.Route
import com.twocircle.bike.domain.model.Segment
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

/**
 * Инструкция поворота для TTS-навигации.
 *
 * Генерируется из геометрии маршрута (List<Coord>) путём анализа изменения
 * направления движения между последовательными точками.
 *
 * Алгоритм:
 * 1. Разбить маршрут на "манёвры" — точки где направление меняется > 25°
 * 2. Для каждого манёвра определить тип (прямо/право/лево/разворот)
 * 3. Вычислить дистанцию от предыдущего манёвра
 * 4. Сгенерировать текст инструкции
 */
data class TurnInstruction(
    /** Индекс точки в geometry маршрута, где происходит манёвр. */
    val pointIndex: Int,
    /** Координата манёвра. */
    val coord: Coord,
    /** Тип манёвра. */
    val type: ManeuverType,
    /** Дистанция от предыдущего манёвра (или старта) в метрах. */
    val distanceFromPrevious: Double,
    /** Название улицы (если есть в данных). */
    val streetName: String?,
    /** Текстовая инструкция для отображения и TTS. */
    val instructionText: String,
    /** Дистанция от старта маршрута. */
    val distanceFromStart: Double,
)

/** Тип манёвра — определяет иконку и текстовую инструкцию. */
enum class ManeuverType(val iconRes: String) {
    DEPART("depart"),
    STRAIGHT("straight"),
    SLIGHT_RIGHT("slight_right"),
    RIGHT("right"),
    SHARP_RIGHT("sharp_right"),
    SLIGHT_LEFT("slight_left"),
    LEFT("left"),
    SHARP_LEFT("sharp_left"),
    UTURN("uturn"),
    ARRIVE("arrive"),
}

/**
 * Генератор инструкций поворотов из геометрии маршрута.
 *
 * Анализирует маршрут и выделяет ключевые манёвры, на которых нужно
 * дать голосовую подсказку. Использует подоход из OSRM/GraphHopper:
 * - Вычисляет bearing (азимут) для каждого сегмента между точками
 * - Сравнивает bearing до и после каждой точки
 * - Если разница > 25° — это манёвр
 * - Классифицирует угол поворота в ManeuverType
 *
 * Это чистый Kotlin — unit-тестируемый без Android зависимостей.
 */
object TurnInstructionGenerator {

    /** Минимальный угол изменения направления для манёвра (градусы). */
    private const val MANEUVER_THRESHOLD = 25.0

    /** Минимальное расстояние между точками для расчёта bearing (метры). */
    private const val MIN_SEGMENT_DISTANCE = 5.0

    /**
     * Сгенерировать список инструкций для маршрута.
     *
     * @param geometry Упорядоченный список координат маршрута
     * @return Список инструкций поворотов, всегда начинается с DEPART и заканчивается ARRIVE
     */
    fun generate(geometry: List<Coord>): List<TurnInstruction> {
        if (geometry.size < 2) return emptyList()

        val instructions = mutableListOf<TurnInstruction>()
        var lastManeuverIndex = 0
        var cumulativeDistance = 0.0

        // Стартовая инструкция
        instructions.add(
            TurnInstruction(
                pointIndex = 0,
                coord = geometry[0],
                type = ManeuverType.DEPART,
                distanceFromPrevious = 0.0,
                streetName = null,
                instructionText = "Start route",
                distanceFromStart = 0.0,
            ),
        )

        // Вычисляем bearings для каждой точки (азимут от точки к следующей)
        val bearings = computeBearings(geometry)

        // Проходим по маршруту и ищем манёвры
        for (i in 1 until geometry.lastIndex) {
            val prevBearing = bearings[i - 1]
            val currBearing = bearings[i]

            // Пропускаем точки слишком близко друг к другу — bearing шумный
            val segDist = haversine(geometry[i - 1], geometry[i])
            if (segDist < MIN_SEGMENT_DISTANCE) continue

            val turnAngle = normalizeAngle(currBearing - prevBearing)

            if (kotlin.math.abs(turnAngle) >= MANEUVER_THRESHOLD) {
                val maneuverType = classifyTurn(turnAngle)
                val distanceFromPrev = haversine(geometry[lastManeuverIndex], geometry[i])

                instructions.add(
                    TurnInstruction(
                        pointIndex = i,
                        coord = geometry[i],
                        type = maneuverType,
                        distanceFromPrevious = distanceFromPrev,
                        streetName = null,
                        instructionText = generateInstructionText(maneuverType, distanceFromPrev),
                        distanceFromStart = cumulativeDistance + distanceFromPrev,
                    ),
                )
                cumulativeDistance += distanceFromPrev
                lastManeuverIndex = i
            }
        }

        // Финишная инструкция
        val totalDist = haversine(geometry[lastManeuverIndex], geometry.last())
        instructions.add(
            TurnInstruction(
                pointIndex = geometry.lastIndex,
                coord = geometry.last(),
                type = ManeuverType.ARRIVE,
                distanceFromPrevious = totalDist,
                streetName = null,
                instructionText = "Arrive at destination",
                distanceFromStart = cumulativeDistance + totalDist,
            ),
        )

        return instructions
    }

    /**
     * Для маршрута из [Route] (с сегментами) генерирует инструкции.
     */
    fun generateFromRoute(route: Route): List<TurnInstruction> {
        return generate(route.geometry)
    }

    /**
     * Вычислить bearing (азимут) для каждой точки маршрута.
     * bearing[i] = азимут от geometry[i] к geometry[i+1]
     */
    private fun computeBearings(geometry: List<Coord>): DoubleArray {
        val bearings = DoubleArray(geometry.size)
        for (i in 0 until geometry.lastIndex) {
            bearings[i] = bearing(geometry[i], geometry[i + 1])
        }
        bearings[geometry.lastIndex] = bearings[geometry.lastIndex - 1]
        return bearings
    }

    /**
     * Азимут (bearing) от точки A к точке B в градусах (0-360).
     * 0 = север, 90 = восток, 180 = юг, 270 = запад.
     */
    private fun bearing(a: Coord, b: Coord): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }

    /**
     * Нормализовать угол в диапазон [-180, 180].
     * Положительный = поворот вправо, отрицательный = поворот влево.
     */
    private fun normalizeAngle(angle: Double): Double {
        var a = angle % 360
        if (a > 180) a -= 360
        if (a < -180) a += 360
        return a
    }

    /**
     * Классифицировать угол поворота в ManeuverType.
     *
     * - 0-15° — прямо
     * - 15-45° — лёгкий поворот
     * - 45-120° — обычный поворот
     * - 120-180° — резкий поворот / разворот
     */
    private fun classifyTurn(angle: Double): ManeuverType {
        val abs = kotlin.math.abs(angle)
        return when {
            abs < 15 -> ManeuverType.STRAIGHT
            angle > 0 -> when {
                abs < 45 -> ManeuverType.SLIGHT_RIGHT
                abs < 120 -> ManeuverType.RIGHT
                abs < 165 -> ManeuverType.SHARP_RIGHT
                else -> ManeuverType.UTURN
            }
            else -> when {
                abs < 45 -> ManeuverType.SLIGHT_LEFT
                abs < 120 -> ManeuverType.LEFT
                abs < 165 -> ManeuverType.SHARP_LEFT
                else -> ManeuverType.UTURN
            }
        }
    }

    /**
     * Сгенерировать текст инструкции (на английском — TTS движок сам переведёт
     * через Android TextToSpeech, который использует активный язык устройства).
     */
    private fun generateInstructionText(type: ManeuverType, distanceMeters: Double): String {
        val distText = formatDistance(distanceMeters)
        return when (type) {
            ManeuverType.STRAIGHT -> "Continue straight"
            ManeuverType.SLIGHT_RIGHT -> "In $distText, bear right"
            ManeuverType.RIGHT -> "In $distText, turn right"
            ManeuverType.SHARP_RIGHT -> "In $distText, sharp right"
            ManeuverType.SLIGHT_LEFT -> "In $distText, bear left"
            ManeuverType.LEFT -> "In $distText, turn left"
            ManeuverType.SHARP_LEFT -> "In $distText, sharp left"
            ManeuverType.UTURN -> "In $distText, U-turn"
            ManeuverType.DEPART -> "Start route"
            ManeuverType.ARRIVE -> "Arrive at destination"
        }
    }

    private fun formatDistance(meters: Double): String = when {
        meters < 1000 -> "${meters.toInt()} meters"
        else -> "${"%.1f".format(meters / 1000)} kilometers"
    }

    /** Haversine distance between two coords (метры). */
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
