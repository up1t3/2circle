package com.twocircle.bike.domain.navigation

/**
 * Receives live GPS samples for turn-by-turn navigation.
 *
 * Lives in :core:domain so :feature:tracking (the GPS producer, via
 * [com.twocircle.bike.feature.tracking.TrackingController]) can feed navigation samples
 * without depending on :feature:map (where the navigation engine lives). The concrete
 * implementation is [com.twocircle.bike.feature.map.navigation.NavigationController],
 * bound via Hilt — mirroring the [com.twocircle.bike.domain.TrackOverlay] pattern.
 *
 * The tracking controller calls [onLocationUpdate] on every accepted GPS sample; the
 * sink ignores it unless [isActive] (i.e. a navigation session is running), so there is
 * zero overhead during plain ride recording.
 */
interface NavigationSink {
    /**
     * Feed one accepted GPS sample. Implementations must be cheap when [isActive] is
     * false (the tracking loop is hot — called on every fix).
     */
    fun onLocationUpdate(lat: Double, lon: Double, speedMps: Float)

    /** True while a turn-by-turn navigation session is running (Active or OffRoute). */
    fun isActive(): Boolean
}
