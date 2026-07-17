package com.twocircle.bike

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom instrumentation runner that swaps the real [BikeApp] for [HiltTestApplication].
 *
 * Required for any @HiltAndroidTest: Hilt needs to build its own test component graph
 * (which can substitute fakes via @UninstallModules or @TestInstallIn) and that requires
 * the Application under test to be HiltTestApplication, not the production BikeApp.
 *
 * Wired in :app/build.gradle.kts via testInstrumentationRunner.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
