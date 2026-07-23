package com.twocircle.bike.feature.map.di

import com.twocircle.bike.domain.navigation.NavigationSink
import com.twocircle.bike.feature.map.navigation.NavigationController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the [NavigationController] as the [NavigationSink] implementation.
 *
 * This is the only place that couples :feature:map's navigation engine to the
 * [NavigationSink] contract in :core:domain. :feature:tracking injects `NavigationSink`
 * (an interface) and forwards accepted GPS samples to it without learning about
 * :feature:map — the same module-boundary pattern as
 * [com.twocircle.bike.domain.TrackOverlay] / [com.twocircle.bike.domain.PlannedRouteHolder].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MapModule {
    @Binds
    @Singleton
    abstract fun bindNavigationSink(impl: NavigationController): NavigationSink
}
