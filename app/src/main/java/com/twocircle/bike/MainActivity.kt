package com.twocircle.bike

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.twocircle.bike.designsystem.theme.BikeTheme
import com.twocircle.bike.designsystem.theme.ThemeState
import com.twocircle.bike.nav.TwoCircleNavHost
import com.twocircle.bike.onboarding.OnboardingScreen
import com.twocircle.bike.onboarding.OnboardingStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Single-activity shell.
 *
 * configChanges в манифесте подавляют recreate на rotation/uiMode — критично для
 * MapLibre. Поэтому тему переключаем НЕ через AppCompatDelegate (который требует
 * recreate), а через [ThemeState] StateFlow → BikeTheme реактивно пересобирается.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        ThemeState.init(this)

        val onboarding = OnboardingStore(this)
        var onboardingReady = false
        splash.setKeepOnScreenCondition { !onboardingReady }

        setContent {
            // Реактивное отслеживание темы — обновляется мгновенно при смене в Settings.
            val isDark by ThemeState.isDark.collectAsState()

            BikeTheme(darkTheme = isDark) {
                val completed by onboarding.completed.collectAsState(initial = null)
                val scope = rememberCoroutineScope()
                onboardingReady = completed != null
                when (completed) {
                    null -> Unit
                    false -> OnboardingScreen(
                        onCompleted = {
                            scope.launch { onboarding.setCompleted() }
                        },
                    )
                    true -> TwoCircleNavHost()
                }
            }
        }
    }
}
