package com.twocircle.bike.domain

import com.twocircle.bike.domain.model.Coord
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only contract for the live-track overlay consumed by the map renderer.
 *
 * Lives in :core:domain so both :feature:tracking (which writes) and :feature:map
 * (which reads) depend on this contract without depending on each other. The concrete
 * implementation is [com.twocircle.bike.feature.tracking.TrackOverlayController],
 * injected via Hilt.
 *
 * The map subscribes to [polyline] and redraws the line on every emission.
 */
interface TrackOverlay {
    /** Current track points, newest last. Empty when no ride is active. */
    val polyline: StateFlow<List<Coord>>
}
