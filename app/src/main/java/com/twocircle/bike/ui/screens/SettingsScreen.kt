package com.twocircle.bike.ui.screens

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.twocircle.bike.BuildConfig
import com.twocircle.bike.designsystem.R

/**
 * Settings screen — Account, Language, Theme, Units, Maps, About.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    val currentLocaleList = AppCompatDelegate.getApplicationLocales()
    val selectedLang = currentLocaleList.takeIf { !it.isEmpty }?.get(0)?.language

    var themeChoice by remember {
        mutableStateOf(prefs.getString("theme", "system") ?: "system")
    }

    var unitsChoice by remember {
        mutableStateOf(prefs.getString("units", "metric") ?: "metric")
    }

    fun updateTheme(theme: String) {
        themeChoice = theme
        prefs.edit().putString("theme", theme).apply()
        // ThemeState.applyTheme мгновенно обновляет BikeTheme через StateFlow —
        // без recreate Activity (критично для MapLibre).
        com.twocircle.bike.designsystem.theme.ThemeState.applyTheme(theme, context)
    }

    fun updateUnits(units: String) {
        unitsChoice = units
        prefs.edit().putString("units", units).apply()
        // TODO Phase 2: реализовать конверсию единиц в Format.kt (metric→imperial)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
        )

        HorizontalDivider()

        // ─── 1. Account Section ──────────────────────────────────────────────────
        SettingsSectionHeader(stringResource(R.string.settings_section_account))
        SettingsRow(
            icon = Icons.Outlined.AccountCircle,
            title = stringResource(R.string.settings_account_default_name),
            subtitle = stringResource(R.string.settings_account_offline),
        )

        HorizontalDivider()

        // ─── 2. Language Section ────────────────────────────────────────────────
        SettingsSectionHeader(stringResource(R.string.settings_section_language))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SelectionRow(
                icon = Icons.Outlined.Language,
                title = stringResource(R.string.settings_language_system),
                selected = selectedLang == null,
                onSelect = {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                }
            )
            SelectionRow(
                icon = Icons.Outlined.Language,
                title = "English",
                selected = selectedLang == "en",
                onSelect = {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                }
            )
            SelectionRow(
                icon = Icons.Outlined.Language,
                title = "Русский",
                selected = selectedLang == "ru",
                onSelect = {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru"))
                }
            )
            SelectionRow(
                icon = Icons.Outlined.Language,
                title = "Español",
                selected = selectedLang == "es",
                onSelect = {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("es"))
                }
            )
        }

        HorizontalDivider()

        // ─── 3. Theme Section ────────────────────────────────────────────────────
        SettingsSectionHeader(stringResource(R.string.settings_section_theme))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SelectionRow(
                icon = Icons.Outlined.DarkMode,
                title = stringResource(R.string.settings_theme_system),
                selected = themeChoice == "system",
                onSelect = { updateTheme("system") }
            )
            SelectionRow(
                icon = Icons.Outlined.DarkMode,
                title = stringResource(R.string.settings_theme_dark),
                selected = themeChoice == "dark",
                onSelect = { updateTheme("dark") }
            )
            SelectionRow(
                icon = Icons.Outlined.DarkMode,
                title = stringResource(R.string.settings_theme_light),
                selected = themeChoice == "light",
                onSelect = { updateTheme("light") }
            )
        }

        HorizontalDivider()

        // ─── 4. Units Section ────────────────────────────────────────────────────
        SettingsSectionHeader(stringResource(R.string.settings_section_units))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SelectionRow(
                icon = Icons.Outlined.Speed,
                title = stringResource(R.string.settings_units_metric),
                selected = unitsChoice == "metric",
                onSelect = { updateUnits("metric") }
            )
            SelectionRow(
                icon = Icons.Outlined.Speed,
                title = stringResource(R.string.settings_units_imperial),
                selected = unitsChoice == "imperial",
                onSelect = { updateUnits("imperial") }
            )
        }

        HorizontalDivider()

        // ─── 5. Maps Section ─────────────────────────────────────────────────────
        SettingsSectionHeader(stringResource(R.string.settings_section_maps))
        SettingsRow(
            icon = Icons.Outlined.Map,
            title = stringResource(R.string.settings_maps_active_region),
            subtitle = stringResource(R.string.settings_maps_region_loaded),
        )

        HorizontalDivider()

        // ─── 6. About Section ────────────────────────────────────────────────────
        SettingsSectionHeader(stringResource(R.string.settings_section_about))
        SettingsRow(
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
            subtitle = stringResource(R.string.settings_about_attribution),
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionRow(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
