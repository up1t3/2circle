package com.twocircle.bike

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application entry. Hilt root; Timber planted in DEBUG.
 *
 * Hilt-generated codegen happens here; feature modules contribute via their own
 * @InstallIn modules. No global state beyond logging — domain/data layers own theirs.
 */
@HiltAndroidApp
class BikeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
