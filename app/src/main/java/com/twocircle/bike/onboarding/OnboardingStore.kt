package com.twocircle.bike.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists the "onboarding completed" flag in a tiny DataStore.
 *
 * We use DataStore (not raw SharedPreferences) so the flag is exposed as a Flow and
 * the UI can reactively decide between showing onboarding or going straight to the
 * map. The store is process-singleton by virtue of the `Context.onboardingDataStore`
 * extension property — there's only ever one.
 */
private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

private val KEY_COMPLETED = booleanPreferencesKey("completed")

class OnboardingStore(private val context: Context) {
    val completed: Flow<Boolean> = context.onboardingDataStore.data.map { it[KEY_COMPLETED] ?: false }

    suspend fun setCompleted() {
        context.onboardingDataStore.edit { it[KEY_COMPLETED] = true }
    }
}
