package com.twocircle.bike.designsystem.l10n

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.twocircle.bike.designsystem.R

/**
 * Localised unit suffixes for [com.twocircle.bike.common.format.Format].
 *
 * `Format` lives in `:core:common` (pure Kotlin, no Android resource access). To localise
 * its output without leaking Android into core, we resolve the unit suffixes here via
 * `stringResource` and pass them back as a plain data class. Callers wrap each Format
 * call: `Format.distance(meters, units = LocalUnitStrings.current)`.
 *
 * Example:
 * - ru → "12.4 км" / "1 ч 23 мин"
 * - en → "12.4 km" / "1h 23min"
 *
 * The Compose-friendly entry point is [LocalUnitStrings]; Format keeps a no-arg default
 * that emits Latin/English suffixes for backward compatibility (unit tests, notifications
 * built outside of composition).
 */
data class UnitStrings(
    val meter: String,
    val kilometer: String,
    val kmh: String,
    val second: String,
    val minute: String,
    val hour: String,
    val percent: String,
    val gb: String,
    val mb: String,
    val kb: String,
    val byte: String,
) {
    companion object {
        /** Latin/English defaults — used when no locale is available (unit tests). */
        val Default = UnitStrings(
            meter = "m",
            kilometer = "km",
            kmh = "km/h",
            second = "s",
            minute = "min",
            hour = "h",
            percent = "%",
            gb = "GB",
            mb = "MB",
            kb = "KB",
            byte = "B",
        )
    }
}

/**
 * Resolve the current locale's unit suffixes inside a Composable.
 *
 * Usage:
 * ```
 * val units = LocalUnitStrings.current
 * Text(Format.distance(meters, units))
 * ```
 */
object LocalUnitStrings {
    val current: UnitStrings
        @Composable get() = UnitStrings(
            meter = stringResource(R.string.format_unit_meter),
            kilometer = stringResource(R.string.format_unit_kilometer),
            kmh = stringResource(R.string.format_unit_kmh),
            second = stringResource(R.string.format_unit_second),
            minute = stringResource(R.string.format_unit_minute),
            hour = stringResource(R.string.format_unit_hour),
            percent = stringResource(R.string.format_unit_percent),
            gb = stringResource(R.string.format_unit_gb),
            mb = stringResource(R.string.format_unit_mb),
            kb = stringResource(R.string.format_unit_kb),
            byte = stringResource(R.string.format_unit_b),
        )
}
