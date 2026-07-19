package com.twocircle.bike.feature.tracking

import com.twocircle.bike.domain.model.Coord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges live tracking data to the map renderer.
 *
 * [TrackingController] holds the authoritative ride state, but it lives in
 * :feature:tracking and shouldn't be imported by :feature:map (that would create a
 * module dependency the other way). This singleton breaks the cycle: tracking writes
 * points here, map reads them — neither module imports the other.
 *
 * The map subscribes to [polyline] and redraws whenever a new point lands. For a long
 * ride this flow can emit thousands of values; MapLibre's LineManager handles updates
 * efficiently (it diffs internally), and we cap the list at [MAX_POINTS] so memory
 * doesn't balloon on a multi-hour tour.
 */
@Singleton
class TrackOverlayController @Inject constructor() : com.twocircle.bike.domain.TrackOverlay {

    private val _polyline = MutableStateFlow<List<Coord>>(emptyList())
    override val polyline: StateFlow<List<Coord>> get() = _polyline.asStateFlow()

    /** Replace the entire polyline (used when loading a historical track for review). */
    fun setPolyline(points: List<Coord>) {
        _polyline.value = points.takeLast(MAX_POINTS)
    }

    /** Append a single point (used during live recording). */
    fun appendPoint(point: Coord) {
        val current = _polyline.value
        val updated = if (current.size >= MAX_POINTS) {
            current.drop(current.size - MAX_POINTS + 1) + point
        } else {
            current + point
        }
        _polyline.value = updated
    }

    /** Clear the overlay (ride stopped / new ride starting). */
    fun clear() {
        _polyline.value = emptyList()
    }

    companion object {
        /** Cap to prevent unbounded memory on very long rides. ~10h at 3s cadence. */
        private const val MAX_POINTS = 12_000
    }
}
