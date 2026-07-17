package com.twocircle.bike.domain.usecase

import com.twocircle.bike.common.outcome.Outcome
import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.Place
import com.twocircle.bike.domain.model.RegionId
import com.twocircle.bike.domain.model.Route
import com.twocircle.bike.domain.model.RoutingProfile
import com.twocircle.bike.domain.model.Waypoint

/**
 * Domain UseCase contracts. Pure interfaces — no Android, no IO classes.
 * Implementations live in feature/data layers and are injected via Hilt.
 *
 * Each UseCase is an `operator fun invoke`, callable as `useCase(args)` — the canonical
 * Clean Architecture form that composes cleanly in ViewModels.
 *
 * Note: NOT `fun interface` — Kotlin forbids default arguments on functional interfaces,
 * and several UseCases need defaults (`near`, `limit`, `radiusMeters`). Plain interfaces
 * give us that flexibility at zero cost.
 */

/** Build an offline route through [waypoints] in order, using [profile] weights. */
interface BuildRoute {
    suspend operator fun invoke(
        waypoints: List<Waypoint>,
        profile: RoutingProfile,
    ): Outcome<Route>
}

/** Search named places in the active region's offline FTS5 index. */
interface SearchPlaces {
    suspend operator fun invoke(
        query: String,
        near: Coord? = null,
        limit: Int = 30,
    ): Outcome<List<Place>>
}

/** Reverse-geocode a coord against the offline index (best-effort; null if nothing nearby). */
interface ReverseGeocode {
    suspend operator fun invoke(coord: Coord, radiusMeters: Double = 500.0): Outcome<Place?>
}

/** True when a region covering [coord] is downloaded and ready for offline use. */
interface IsRegionAvailable {
    suspend operator fun invoke(coord: Coord): Outcome<RegionId?>
}
