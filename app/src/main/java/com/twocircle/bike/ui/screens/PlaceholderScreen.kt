package com.twocircle.bike.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Feature-screen placeholder.
 *
 * Replaced by real feature-module Composables as Steps 3–8 land. The intent is to
 * keep the navigation graph + bottom bar runnable end-to-end from day one, so the
 * shell contract is validated before any feature UI is built.
 */
@Composable
fun PlaceholderScreen(route: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "2circle / $route",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Экран подключается в следующих шагах сборки.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
