package com.twocircle.bike.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = BikeDarkColors.Primary,
    onPrimary = BikeDarkColors.OnPrimary,
    secondary = BikeDarkColors.Secondary,
    background = BikeDarkColors.Background,
    onBackground = BikeDarkColors.OnBackground,
    surface = BikeDarkColors.Surface,
    onSurface = BikeDarkColors.OnSurface,
    error = BikeDarkColors.Error,
)

private val LightColors = lightColorScheme(
    primary = BikeLightColors.Primary,
    onPrimary = BikeLightColors.OnPrimary,
    secondary = BikeLightColors.Secondary,
    background = BikeLightColors.Background,
    onBackground = BikeLightColors.OnBackground,
    surface = BikeLightColors.Surface,
    onSurface = BikeLightColors.OnSurface,
    error = BikeLightColors.Error,
)

/**
 * App theme. Dark is the default for outdoor readability and night-vision preservation
 * during multi-day tours; light mode flips in bright sunlight.
 *
 * [darkTheme] defaults to system, but can be overridden by a user preference later.
 */
@Composable
fun BikeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = BikeTypography,
        content = content,
    )
}
