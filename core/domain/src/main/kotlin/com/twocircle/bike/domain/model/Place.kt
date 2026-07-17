package com.twocircle.bike.domain.model

import kotlinx.serialization.Serializable

/**
 * A named, searchable geographic feature. Source of truth for offline search results
 * and reverse-geocoded long-press points.
 *
 * [kind] drives icon and category filter in the search UI; [population] participates
 * in ranking (larger cities outrank same-named hamlets).
 */
@Serializable
data class Place(
    val id: String,
    val name: String,
    val asciiName: String,
    val kind: Kind,
    val coord: Coord,
    val population: Long = 0,
    val extra: Map<String, String> = emptyMap(),
) {
    enum class Kind {
        City, Town, Village, Hamlet, Spring, MountainPass, Campsite, Viewpoint,
        BicycleService, Other,
    }
}
