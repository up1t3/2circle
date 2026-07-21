package com.twocircle.bike.feature.poi.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.twocircle.bike.data.db.entity.RegionInstallState
import com.twocircle.bike.data.filesystem.RegionAssets
import com.twocircle.bike.data.repository.RegionsRepository
import com.twocircle.bike.feature.poi.model.Poi
import com.twocircle.bike.feature.poi.model.PoiCategory
import com.twocircle.bike.feature.search.engine.PlaceKind
import com.twocircle.bike.feature.search.engine.SearchSchema
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads POIs from the active region's `search.db`.
 *
 * Same file the offline search uses — but queried by category (filtered `WHERE kind IN (...)`)
 * and constrained to the current map bounding box. This is the offline-only source; the
 * planned 2GIS online enrichment sits in front of it as a decorator in a later phase.
 *
 * DB handle is opened per query (cheap with WAL + the platform pool) and closed in a `use`
 * block — POI queries happen on category-toggle / map-idle, never at high frequency, so we
 * don't need a long-lived handle here. (The search screen keeps its own long-lived handle
 * via [com.twocircle.bike.feature.search.engine.SearchEngine] for snappier keystroke search.)
 *
 * Returns an empty list on any DB error — never throws. The UI treats empty as "no POIs in
 * view", which is the honest representation of either "DB missing" or "category genuinely
 * empty in this area".
 */
@Singleton
class PoiRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val regionsRepository: RegionsRepository,
    private val regionAssets: RegionAssets,
) {

    /**
     * Load POIs of the given [categories] inside the bounding box, capped at [limit]
     * per category to keep the symbol layer snappy.
     *
     * [minLat]/[minLon]/[maxLat]/[maxLon] are the map's visible region in WGS84 degrees.
     */
    suspend fun getInBounds(
        categories: Set<PoiCategory>,
        minLat: Double, minLon: Double,
        maxLat: Double, maxLon: Double,
        limit: Int = DEFAULT_LIMIT,
    ): List<Poi> = withContext(Dispatchers.IO) {
        if (categories.isEmpty()) return@withContext emptyList()
        val region = regionsRepository.firstInstalledOrNull() ?: return@withContext emptyList()
        if (region.installState != RegionInstallState.Installed) return@withContext emptyList()
        val dbFile = regionAssets.searchDbPath(region)
        if (!dbFile.exists() || dbFile.length() == 0L) return@withContext emptyList()

        val kinds = categories.flatMap { it.kinds }.map { it.osmValue }.distinct()
        val placeholders = kinds.joinToString(",") { "?" }
        val sql = (
            "SELECT rowid, name, kind, lat, lon " +
                "FROM ${SearchSchema.TABLE} " +
                "WHERE kind IN ($placeholders) " +
                "AND lat BETWEEN ? AND ? " +
                "AND lon BETWEEN ? AND ? " +
                "LIMIT ?"
            )
        // arg order: kind…, minLat, maxLat, minLon, maxLon, limit
        val args = kinds.toTypedArray<String>() +
            arrayOf(
                minLat.toString(), maxLat.toString(),
                minLon.toString(), maxLon.toString(),
                limit.toString(),
            )

        try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath, /* factory = */ null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            ).use { db ->
                val out = ArrayList<Poi>(limit)
                db.rawQuery(sql, args).use { c ->
                    while (c.moveToNext()) {
                        out += Poi(
                            id = c.getLong(c.getColumnIndexOrThrow(SearchSchema.COL_ROWID)),
                            name = c.getString(c.getColumnIndexOrThrow(SearchSchema.COL_NAME)) ?: "",
                            kind = PlaceKind.fromOsm(c.getString(c.getColumnIndexOrThrow(SearchSchema.COL_KIND))),
                            lat = c.getDouble(c.getColumnIndexOrThrow(SearchSchema.COL_LAT)),
                            lon = c.getDouble(c.getColumnIndexOrThrow(SearchSchema.COL_LON)),
                        )
                    }
                }
                out
            }
        } catch (e: Exception) {
            Timber.e(e, "POI query failed")
            emptyList()
        }
    }

    /**
     * Count POIs of [categories] in the bounding box, without fetching the rows.
     * Used by the bottom sheet to show "23 points" next to the chip.
     */
    suspend fun countInBounds(
        categories: Set<PoiCategory>,
        minLat: Double, minLon: Double,
        maxLat: Double, maxLon: Double,
    ): Int = withContext(Dispatchers.IO) {
        if (categories.isEmpty()) return@withContext 0
        val region = regionsRepository.firstInstalledOrNull() ?: return@withContext 0
        if (region.installState != RegionInstallState.Installed) return@withContext 0
        val dbFile = regionAssets.searchDbPath(region)
        if (!dbFile.exists() || dbFile.length() == 0L) return@withContext 0

        val kinds = categories.flatMap { it.kinds }.map { it.osmValue }.distinct()
        val placeholders = kinds.joinToString(",") { "?" }
        val sql = (
            "SELECT COUNT(*) FROM ${SearchSchema.TABLE} " +
                "WHERE kind IN ($placeholders) " +
                "AND lat BETWEEN ? AND ? " +
                "AND lon BETWEEN ? AND ?"
            )
        val args = kinds.toTypedArray<String>() +
            arrayOf(minLat.toString(), maxLat.toString(), minLon.toString(), maxLon.toString())

        try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath, /* factory = */ null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            ).use { db ->
                db.rawQuery(sql, args).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "POI count query failed")
            0
        }
    }

    companion object {
        /** Cap per query — keeps the symbol layer and the list usable. */
        const val DEFAULT_LIMIT = 200
    }
}
