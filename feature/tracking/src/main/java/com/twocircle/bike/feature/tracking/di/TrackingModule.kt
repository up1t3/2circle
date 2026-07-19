package com.twocircle.bike.feature.tracking.di

import com.twocircle.bike.domain.TrackOverlay
import com.twocircle.bike.feature.tracking.TrackOverlayController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds [TrackOverlayController] as the [TrackOverlay] implementation.
 *
 * The interface lives in :core:domain so :feature:map can inject it without depending
 * on :feature:tracking. This binding is the only place that knows the concrete class.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingModule {
    @Binds
    @Singleton
    abstract fun bindTrackOverlay(impl: TrackOverlayController): TrackOverlay
}
