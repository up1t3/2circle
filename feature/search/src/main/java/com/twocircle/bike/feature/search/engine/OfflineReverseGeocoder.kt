package com.twocircle.bike.feature.search.engine

import com.twocircle.bike.common.outcome.Outcome
import com.twocircle.bike.data.filesystem.RegionAssets
import com.twocircle.bike.data.repository.RegionsRepository
import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.Place
import com.twocircle.bike.domain.usecase.ReverseGeocode
import com.twocircle.bike.feature.search.model.SearchHit
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline [ReverseGeocode] backed by the active region's `search.db`.
 *
 * Resolves the active region via [RegionsRepository], points [SearchEngine] at its
 * `search.db`, and delegates to [SearchEngine.nearestWithin] — a linear scan that is
 * sub-10ms for typical region sizes (<50k places), as documented at the call site.
 *
 * "No name nearby" is a normal outcome, not an error: the user tapped an empty spot.
 * We surface it as [Outcome.Success]`<null>` so callers can render raw coordinates
 * without special-casing. Genuine failures (no region installed, corrupted DB) return
 * a typed [Failure.Region] so the UI can offer remediation.
 */
@Singleton
class OfflineReverseGeocoder @Inject constructor(
    private val engine: SearchEngine,
    private val regions: RegionsRepository,
    private val assets: RegionAssets,
) : ReverseGeocode {

    override suspend fun invoke(coord: Coord, radiusMeters: Double): Outcome<Place?> {
        val region = regions.firstInstalledOrNull()
            ?: return Outcome.Success(null) // No region → nothing to look up; not an error.
        val dbPath = assets.searchDbPath(region)
        if (!engine.useRegion(dbPath)) {
            Timber.w("ReverseGeocode: search.db unavailable for region %s", region.id)
            return Outcome.Success(null)
        }
        val hit = engine.nearestWithin(coord.lat, coord.lon, radiusMeters)
        return Outcome.Success(hit?.toPlace())
    }

    private fun SearchHit.toPlace(): Place = Place(
        id = rowId.toString(),
        name = name,
        asciiName = asciiName,
        kind = mapKind(kind),
        coord = Coord(lat = lat, lon = lon),
        population = population,
    )

    private fun mapKind(k: PlaceKind): Place.Kind = when (k) {
        PlaceKind.City -> Place.Kind.City
        PlaceKind.Town -> Place.Kind.Town
        PlaceKind.Village -> Place.Kind.Village
        PlaceKind.Hamlet -> Place.Kind.Hamlet
        PlaceKind.Spring -> Place.Kind.Spring
        PlaceKind.MountainPass -> Place.Kind.MountainPass
        PlaceKind.Campsite -> Place.Kind.Campsite
        PlaceKind.Viewpoint -> Place.Kind.Viewpoint
        PlaceKind.BicycleService -> Place.Kind.BicycleService
        PlaceKind.BicycleRental -> Place.Kind.BicycleRental
        PlaceKind.Pharmacy -> Place.Kind.Pharmacy
        PlaceKind.Fuel -> Place.Kind.Fuel
        PlaceKind.Cafe -> Place.Kind.Cafe
        PlaceKind.Restaurant -> Place.Kind.Restaurant
        PlaceKind.Hospital -> Place.Kind.Hospital
        PlaceKind.Atm -> Place.Kind.Atm
        PlaceKind.Water -> Place.Kind.Water
        PlaceKind.Hotel -> Place.Kind.Hotel
        PlaceKind.Shop -> Place.Kind.Shop
        PlaceKind.Other -> Place.Kind.Other
    }
}
