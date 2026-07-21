package com.twocircle.bike.feature.search.engine

import androidx.annotation.StringRes
import com.twocircle.bike.designsystem.R

/**
 * Schema contract for the per-region `search.db` produced by the backend pipeline.
 *
 * The pipeline writes this SQLite file with a single `places` table (plain SQLite,
 * not FTS5 — Android's bundled SQLite often lacks the FTS5 module). The client opens
 * it read-only and issues `LIKE` queries with indexes on `name` and `name_ascii`.
 *
 * Expected SQL (built by the pipeline):
 * ```
 * CREATE TABLE places (
 *   rowid INTEGER PRIMARY KEY AUTOINCREMENT,
 *   name TEXT NOT NULL,
 *   name_ascii TEXT NOT NULL,
 *   kind TEXT NOT NULL,
 *   lat REAL NOT NULL,
 *   lon REAL NOT NULL,
 *   population INTEGER NOT NULL DEFAULT 0
 * );
 * CREATE INDEX idx_places_name ON places(name);
 * CREATE INDEX idx_places_name_ascii ON places(name_ascii);
 * ```
 *
 * `name_ascii` is a transliterated form (Cyrillic→Latin etc.) so the rider can type
 * "Yalta" or "Ялта" interchangeably regardless of keyboard layout.
 *
 * `kind` is one of the [PlaceKind] values; used by the search UI and the POI layer
 * for icon + label selection.
 */
object SearchSchema {
    const val TABLE = "places"
    const val COL_ROWID = "rowid"
    const val COL_NAME = "name"
    const val COL_NAME_ASCII = "name_ascii"
    const val COL_KIND = "kind"
    const val COL_LAT = "lat"
    const val COL_LON = "lon"
    const val COL_POPULATION = "population"
}

/**
 * Category of a place — drives icon, label and ranking weight in the search and POI UIs.
 *
 * `displayNameRes` is the localised user-facing label; `osmValue` is the wire format
 * written into `search.db.kind` by the backend pipeline (must match `POI_KINDS` and
 * `PLACE_KINDS` in `build_search_db.py`).
 */
enum class PlaceKind {
    City, Town, Village, Hamlet,
    Spring, MountainPass, Campsite, Viewpoint,
    BicycleService, BicycleRental,
    Pharmacy, Fuel, Cafe, Restaurant, Hospital, Atm,
    Water, Hotel, Shop,
    Other;

    /** Wire format stored in `search.db.kind`. */
    val osmValue: String get() = when (this) {
        City -> "city"
        Town -> "town"
        Village -> "village"
        Hamlet -> "hamlet"
        Spring -> "spring"
        MountainPass -> "mountain_pass"
        Campsite -> "campsite"
        Viewpoint -> "viewpoint"
        BicycleService -> "bicycle_service"
        BicycleRental -> "bicycle_rental"
        Pharmacy -> "pharmacy"
        Fuel -> "fuel"
        Cafe -> "cafe"
        Restaurant -> "restaurant"
        Hospital -> "hospital"
        Atm -> "atm"
        Water -> "water"
        Hotel -> "hotel"
        Shop -> "shop"
        Other -> "other"
    }

    /** Localised label for the UI. */
    @get:StringRes
    val displayNameRes: Int get() = when (this) {
        City -> R.string.place_kind_city
        Town -> R.string.place_kind_town
        Village -> R.string.place_kind_village
        Hamlet -> R.string.place_kind_hamlet
        Spring -> R.string.place_kind_spring
        MountainPass -> R.string.place_kind_mountain_pass
        Campsite -> R.string.place_kind_campsite
        Viewpoint -> R.string.place_kind_viewpoint
        BicycleService -> R.string.place_kind_bicycle_service
        BicycleRental -> R.string.place_kind_bicycle_rental
        Pharmacy -> R.string.place_kind_pharmacy
        Fuel -> R.string.place_kind_fuel
        Cafe -> R.string.place_kind_cafe
        Restaurant -> R.string.place_kind_restaurant
        Hospital -> R.string.place_kind_hospital
        Atm -> R.string.place_kind_atm
        Water -> R.string.place_kind_water
        Hotel -> R.string.place_kind_hotel
        Shop -> R.string.place_kind_shop
        Other -> R.string.place_kind_other
    }

    companion object {
        fun fromOsm(raw: String?): PlaceKind = when (raw?.lowercase()) {
            "city" -> City
            "town" -> Town
            "village" -> Village
            "hamlet" -> Hamlet
            "spring" -> Spring
            "mountain_pass" -> MountainPass
            "campsite" -> Campsite
            "viewpoint" -> Viewpoint
            "bicycle_service" -> BicycleService
            "bicycle_rental" -> BicycleRental
            "pharmacy" -> Pharmacy
            "fuel" -> Fuel
            "cafe" -> Cafe
            "restaurant" -> Restaurant
            "hospital" -> Hospital
            "atm" -> Atm
            "water" -> Water
            "hotel" -> Hotel
            "shop" -> Shop
            else -> Other
        }
    }
}
