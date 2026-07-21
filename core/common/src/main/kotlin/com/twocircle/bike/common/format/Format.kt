package com.twocircle.bike.common.format

import java.time.Duration
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Locale-aware formatting of ride telemetry.
 *
 * Why custom and not `java.util.Formatter`? Bicycle UIs need compact forms
 * ("12.4 km", "1 ч 23 мин", "−12 %") that differ from `DecimalFormat` defaults,
 * and we want the unit attached in the active locale (Russian short forms on
 * Russian devices, Latin on English). This keeps the strings deterministic and
 * unit-testable.
 *
 * Localisation contract: the unit suffixes are passed in via a `UnitStrings`-like
 * payload (a plain data holder, defined in :core:designsystem as [UnitStrings]).
 * The no-arg overloads default to Latin suffixes so pure-Kotlin callers (tests,
 * notifications outside of Compose) keep working. Composable callers pass the
 * locale-resolved suffixes via `LocalUnitStrings.current`.
 *
 * Notes:
 * - Decimal separator is `.` here intentionally — locale-independent for tests/persistence.
 */
object Format {

    /** "12.4 km" / "840 m" / "8.2 mi" / "400 ft". */
    fun distance(
        meters: Double,
        meter: String = "m",
        kilometer: String = "km",
        isImperial: Boolean = false,
    ): String {
        if (isImperial) {
            val miles = meters * 0.000621371
            val feet = meters * 3.28084
            return when {
                feet < 1.0 -> "0 ft"
                miles < 0.1 -> "${feet.roundToInt()} ft"
                else -> String.format(java.util.Locale.US, "%.1f mi", miles)
            }
        }
        return when {
            meters < 1.0 -> "0 $meter"
            meters < 1_000.0 -> "${meters.roundToInt()} $meter"
            else -> String.format(java.util.Locale.US, "%.1f $kilometer", meters / 1_000.0)
        }
    }

    /** "23.1 km/h" / "14.4 mph". */
    fun speed(
        metersPerSec: Double,
        kmh: String = "km/h",
        isImperial: Boolean = false,
    ): String {
        val v = if (isImperial) metersPerSec * 2.23694 else metersPerSec * 3.6
        val unit = if (isImperial) "mph" else kmh
        return String.format(java.util.Locale.US, "%.1f $unit", v)
    }

    /**
     * Compact human-readable duration.
     * - < 60 s → "45 s" / "45 с"
     * - < 60 m → "23 min" / "23 мин"  (or "1 h 5 min" / "1 ч 5 мин")
     * - else   → "3 h 14 min" / "3 ч 14 мин"
     */
    fun duration(
        d: Duration,
        second: String = "s",
        minute: String = "min",
        hour: String = "h",
    ): String {
        val totalSec = d.seconds
        if (totalSec < 60) return "$totalSec$second"
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        return when {
            hours == 0L -> "$mins$minute"
            mins == 0L -> "$hours$hour"
            else -> "$hours$hour $mins$minute"
        }
    }

    /** "+348 m" / "−112 m" or "+1141 ft". */
    fun elevation(
        meters: Double,
        meter: String = "m",
        isImperial: Boolean = false,
    ): String {
        val converted = if (isImperial) meters * 3.28084 else meters
        val unit = if (isImperial) "ft" else meter
        val sign = when {
            converted > 0 -> "+"
            converted < 0 -> "−"
            else -> ""
        }
        return "$sign${abs(converted).roundToInt()} $unit"
    }

    /** "7.4 %" gradient. Always signed for slopes. */
    fun gradient(percent: Double, percentSign: String = "%"): String {
        val sign = if (percent >= 0) "+" else "−"
        return "$sign${String.format(java.util.Locale.US, "%.1f", abs(percent))} $percentSign"
    }

    /** File size: "12 MB" / "1.4 GB" / "640 KB". */
    fun fileSize(
        bytes: Long,
        gb: String = "GB",
        mb: String = "MB",
        kb: String = "KB",
        byte: String = "B",
    ): String {
        val abs = bytes.toDouble()
        val kbVal = abs / 1024.0
        val mbVal = kbVal / 1024.0
        val gbVal = mbVal / 1024.0
        return when {
            gbVal >= 1.0 -> String.format(java.util.Locale.US, "%.1f $gb", gbVal)
            mbVal >= 1.0 -> String.format(java.util.Locale.US, "%.1f $mb", mbVal)
            kbVal >= 1.0 -> String.format(java.util.Locale.US, "%d $kb", kbVal.roundToInt())
            else -> "$bytes $byte"
        }
    }
}
