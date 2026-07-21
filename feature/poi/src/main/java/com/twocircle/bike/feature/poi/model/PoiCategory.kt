package com.twocircle.bike.feature.poi.model

import androidx.annotation.StringRes
import com.twocircle.bike.designsystem.R
import com.twocircle.bike.feature.search.engine.PlaceKind

/**
 * A POI category exposed in the bottom-sheet chips.
 *
 * Maps 1-to-1 to one or more [PlaceKind] values stored in `search.db` (which the backend
 * pipeline extracted from OSM tags). Multiple POI categories can share a kind alias —
 * e.g. [Lodging] pulls hotel/hostel/motel/guest_house rows.
 *
 * `displayNameRes` is the localised chip label; `kinds` is the OR filter applied at the
 * SQL layer when querying `search.db`.
 */
enum class PoiCategory(
    @field:StringRes val displayNameRes: Int,
    val kinds: Set<PlaceKind>,
) {
    Pharmacy(
        displayNameRes = R.string.place_kind_pharmacy,
        kinds = setOf(PlaceKind.Pharmacy),
    ),
    Hospital(
        displayNameRes = R.string.place_kind_hospital,
        kinds = setOf(PlaceKind.Hospital),
    ),
    Fuel(
        displayNameRes = R.string.place_kind_fuel,
        kinds = setOf(PlaceKind.Fuel),
    ),
    Water(
        displayNameRes = R.string.place_kind_water,
        kinds = setOf(PlaceKind.Water, PlaceKind.Spring),
    ),
    Cafe(
        displayNameRes = R.string.place_kind_cafe,
        kinds = setOf(PlaceKind.Cafe, PlaceKind.Restaurant),
    ),
    Shop(
        displayNameRes = R.string.place_kind_shop,
        kinds = setOf(PlaceKind.Shop),
    ),
    Lodging(
        displayNameRes = R.string.place_kind_hotel,
        kinds = setOf(PlaceKind.Hotel, PlaceKind.Campsite),
    ),
    Atm(
        displayNameRes = R.string.place_kind_atm,
        kinds = setOf(PlaceKind.Atm),
    ),
    BicycleService(
        displayNameRes = R.string.place_kind_bicycle_service,
        kinds = setOf(PlaceKind.BicycleService, PlaceKind.BicycleRental),
    );

    companion object {
        /** All kinds referenced across all categories — useful for count queries. */
        val allKinds: Set<PlaceKind> = entries.flatMap { it.kinds }.toSet()
    }
}
