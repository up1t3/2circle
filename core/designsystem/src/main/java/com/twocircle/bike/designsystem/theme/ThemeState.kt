package com.twocircle.bike.designsystem.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Глобальное состояние темы приложения.
 *
 * Читается из SharedPreferences ("app_settings" → "theme") при старте.
 * При смене темы в SettingsScreen обновляется этот StateFlow → BikeTheme
 * и MapScreen реактивно пересобираются без пересоздания Activity
 * (критично для MapLibre, который теряет GL-контекст при recreate).
 *
 * Находится в :core:designsystem, чтобы быть доступным из всех модулей.
 */
object ThemeState {
    private val _isDark = MutableStateFlow(true)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val theme = prefs.getString("theme", "system") ?: "system"
        applyTheme(theme, context)
    }

    fun applyTheme(theme: String, context: Context) {
        val isDark = when (theme) {
            "dark" -> true
            "light" -> false
            else -> {
                val nightMode = context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
        _isDark.value = isDark
    }
}
