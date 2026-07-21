package com.twocircle.bike.feature.poi.model

import com.twocircle.bike.feature.search.engine.PlaceKind

/**
 * A single point of interest loaded from the region's `search.db`.
 *
 * A thin view over a search hit restricted to POI kinds (no settlements) — kept
 * separate from `SearchHit` because the POI UI doesn't need bm25 rank or ascii name,
 * and grouping by category is a POI-specific concern.
 */
data class Poi(
    val id: Long,
    val name: String,
    val kind: PlaceKind,
    val lat: Double,
    val lon: Double,
) {
    /** Categories this POI belongs to (a hospital also shows under Lodging? no — just its own). */
    fun categories(): Set<PoiCategory> = PoiCategory.entries
        .filterTo(mutableSetOf()) { this.kind in it.kinds }
}
