package com.twocircle.bike.feature.search.di

import com.twocircle.bike.domain.usecase.ReverseGeocode
import com.twocircle.bike.feature.search.engine.OfflineReverseGeocoder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the offline [ReverseGeocode] implementation.
 *
 * Lives in :feature:search because that's where the [OfflineReverseGeocoder] (and its
 * [com.twocircle.bike.feature.search.engine.SearchEngine] dependency) resides. Consumers
 * (:feature:map, :feature:routing) inject the `ReverseGeocode` interface from :core:domain
 * without depending on :feature:search — the same module-boundary pattern as
 * [com.twocircle.bike.domain.TrackOverlay] / [com.twocircle.bike.domain.PlannedRouteHolder].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {
    @Binds
    @Singleton
    abstract fun bindReverseGeocoder(impl: OfflineReverseGeocoder): ReverseGeocode
}
