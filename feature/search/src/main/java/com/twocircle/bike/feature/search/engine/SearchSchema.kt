package com.twocircle.bike.feature.search.engine

/**
 * Schema contract for the per-region `search.db` produced by the backend pipeline.
 *
 * The pipeline (tilemaker + FTS5 build, see design doc Step 9) writes this SQLite file
 * with a single FTS5 virtual table. The client opens it read-only and issues MATCH
 * queries — no schema management happens on-device.
 *
 * Expected SQL (built by the pipeline):
 * ```
 * CREATE VIRTUAL TABLE places USING fts5(
 *   name, name_ascii, kind, lat UNINDEXED, lon UNINDEXED,
 *   population UNINDEXED,
 *   tokenize = "unicode61 remove_diacritics 2"
 * );
 * ```
 *
 * `name_ascii` is a transliterated form (Cyrillic→Latin etc.) so the rider can type
 * "Yalta" or "Ялта" interchangeably regardless of keyboard layout. `unicode61` plus
 * `remove_diacritics 2` handles accents on Latin scripts (España → Espana).
 *
 * `kind` is one of the [PlaceKind] values; indexed so we can filter by category later.
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

/** Category of a place — drives icon and ranking weight in the search UI. */
enum class PlaceKind {
    City, Town, Village, Hamlet, Spring, MountainPass, Campsite, Viewpoint,
    BicycleService, Other;

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
        Other -> "other"
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
            else -> Other
        }
    }
}
