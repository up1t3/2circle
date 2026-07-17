package com.twocircle.bike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.twocircle.bike.designsystem.theme.BikeTheme
import com.twocircle.bike.nav.TwoCircleNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity shell. All screens are Compose destinations handled by [TwoCircleNavHost].
 *
 * Edge-to-edge with a translucent status bar; the dark theme is the default for
 * outdoor readability. configChanges in the manifest prevent recreation on rotation —
 * important for a map-heavy app where recreation drops the MapLibre context.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BikeTheme {
                TwoCircleNavHost()
            }
        }
    }
}
