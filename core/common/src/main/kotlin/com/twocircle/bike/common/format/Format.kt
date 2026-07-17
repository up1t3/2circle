package com.twocircle.bike.common.format

import java.time.Duration
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Locale-agnostic formatting of ride telemetry.
 *
 * Why custom and not `java.util.Formatter`? Bicycle UIs need compact forms
 * ("12.4 km", "1 ч 23 м", "−12 %") that differ from `DecimalFormat` defaults,
 * and we want the unit attached in Russian even on English-locale devices. This
 * keeps the strings deterministic and unit-testable.
 *
 * Notes:
 * - Decimal separator is `.` here intentionally — this is a stable, locale-independent
 *   contract for tests and persistence. UI presentation can localise later.
 */
object Format {

    /** "12.4 km" / "840 m" / "0 m". */
    fun distance(meters: Double): String = when {
        meters < 1.0 -> "0 m"
        meters < 1_000.0 -> "${meters.roundToInt()} m"
        else -> String.format(java.util.Locale.US, "%.1f km", meters / 1_000.0)
    }

    /** "23.1 km/h". */
    fun speed(metersPerSec: Double): String {
        val kmh = metersPerSec * 3.6
        return String.format(java.util.Locale.US, "%.1f km/h", kmh)
    }

    /**
     * Compact human-readable duration in Russian short forms.
     * - < 60 s → "45 с"
     * - < 60 m → "23 м" or "1 ч 5 м"
     * - else   → "3 ч 14 м"
     */
    fun duration(d: Duration): String {
        val totalSec = d.seconds
        if (totalSec < 60) return "${totalSec}s"
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        return when {
            hours == 0L -> "${mins}m"
            mins == 0L -> "${hours}h"
            else -> "${hours}h ${mins}m"
        }
    }

    /** "+348 m" / "−112 m" (Unicode minus for sign consistency). */
    fun elevation(meters: Double): String {
        val sign = when {
            meters > 0 -> "+"
            meters < 0 -> "−"
            else -> ""
        }
        return "$sign${abs(meters).roundToInt()} m"
    }

    /** "7.4 %" gradient. Always signed for slopes. */
    fun gradient(percent: Double): String {
        val sign = if (percent >= 0) "+" else "−"
        return "$sign${String.format(java.util.Locale.US, "%.1f", abs(percent))} %"
    }

    /** File size: "12 MB" / "1.4 GB" / "640 KB". */
    fun fileSize(bytes: Long): String {
        val abs = bytes.toDouble()
        val kb = abs / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%d KB", kb.roundToInt())
            else -> "$bytes B"
        }
    }
}
