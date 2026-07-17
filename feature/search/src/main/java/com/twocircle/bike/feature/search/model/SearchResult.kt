package com.twocircle.bike.feature.search.model

import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.Place
import com.twocircle.bike.feature.search.engine.PlaceKind

/**
 * A raw search hit, before ranking. Mirrors one row of the FTS5 result.
 *
 * [bm25Rank] is the FTS5 `bm25()` value — negative, closer to zero = better match.
 * Kept on this intermediate type because ranking needs the whole result set to normalise
 * ranks (see SearchRanking.relevance).
 */
data class SearchHit(
    val rowId: Long,
    val name: String,
    val asciiName: String,
    val kind: PlaceKind,
    val lat: Double,
    val lon: Double,
    val population: Long,
    val bm25Rank: Double,
)

/** Distance from the search anchor, computed after the hit is fetched. */
data class ScoredResult(
    val hit: SearchHit,
    val distanceKm: Double?,
    val score: Double,
)

/** Map a scored result to the domain [Place] type. */
fun ScoredResult.toPlace(): Place = Place(
    id = hit.rowId.toString(),
    name = hit.name,
    asciiName = hit.asciiName,
    kind = hit.kind.toDomainKind(),
    coord = Coord(lat = hit.lat, lon = hit.lon),
    population = hit.population,
)

private fun PlaceKind.toDomainKind(): Place.Kind = when (this) {
    PlaceKind.City -> Place.Kind.City
    PlaceKind.Town -> Place.Kind.Town
    PlaceKind.Village -> Place.Kind.Village
    PlaceKind.Hamlet -> Place.Kind.Hamlet
    PlaceKind.Spring -> Place.Kind.Spring
    PlaceKind.MountainPass -> Place.Kind.MountainPass
    PlaceKind.Campsite -> Place.Kind.Campsite
    PlaceKind.Viewpoint -> Place.Kind.Viewpoint
    PlaceKind.BicycleService -> Place.Kind.BicycleService
    PlaceKind.Other -> Place.Kind.Other
}
