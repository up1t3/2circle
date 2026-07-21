package com.twocircle.bike.di

import com.twocircle.bike.BuildConfig
import com.twocircle.bike.common.di.ManifestUrl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the region-manifest URL as an injected String so feature modules can fetch
 * the catalog without depending on :app's BuildConfig (which would be a circular
 * dependency — :app depends on the features, not the other way around).
 *
 * The URL differs between debug (emulator alias 10.0.2.2 for host loopback) and release
 * (LAN IP of the dev machine hosting the backend pipeline). Both are baked into
 * BuildConfig.MANIFEST_URL per build type — see app/build.gradle.kts.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {

    /** URL of the regions/manifest.json served by the backend pipeline. */
    @Provides
    @ManifestUrl
    @Singleton
    fun provideManifestUrl(): String = BuildConfig.MANIFEST_URL
}
