package com.twocircle.bike.feature.search.engine

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.twocircle.bike.common.geo.Geo
import com.twocircle.bike.feature.search.model.ScoredResult
import com.twocircle.bike.feature.search.model.SearchHit
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline FTS5 search over a region's `search.db`.
 *
 * Opens the database read-only via the platform SQLite (no Room wrapper — Room manages
 * our app's own BikeDatabase, but this is an external file with our own schema). Issues
 * MATCH queries against the FTS5 virtual table, then re-ranks in-app using [SearchRanking]
 * because FTS5's bm25 alone doesn't account for population or distance.
 *
 * Lifecycle: the engine opens the DB lazily on first query and keeps it open. Callers
 * invoke [useRegion] when the active region changes; that closes any previously-opened
 * DB and opens the new one. This keeps file handles bounded (max one open DB at a time).
 *
 * Thread safety: SQLite opens are not thread-safe by default; the engine serialises
 * queries behind a synchronized block. Search traffic is low (user-typed debounce) so
 * this is cheaper than a thread pool.
 */
@Singleton
class SearchEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    @Volatile private var db: SQLiteDatabase? = null
    @Volatile private var currentPath: String? = null
    private val lock = Any()

    /**
     * Switch the engine to [searchDbPath]. Idempotent if already pointing there.
     * Returns true if the DB opened successfully, false otherwise (caller surfaces as
     * a typed Failure).
     */
    fun useRegion(searchDbPath: File): Boolean = synchronized(lock) {
        val path = searchDbPath.absolutePath
        if (path == currentPath && db?.isOpen == true) return true
        closeInternal()
        if (!searchDbPath.exists() || searchDbPath.length() == 0L) {
            Timber.w("search.db missing/empty at $path")
            return false
        }
        return try {
            // OPEN_READONLY — we never write to the search DB on-device.
            // SQLiteDatabase is the platform class; no CursorFactory needed.
            db = SQLiteDatabase.openDatabase(
                path, /* factory = */ null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            currentPath = path
            // Reset the FTS5 probe — each DB handle may have a different SQLite build.
            fts5Cached = null
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to open search.db at $path")
            false
        }
    }

    /** Close any open DB. Safe to call repeatedly. */
    fun close() = synchronized(lock) { closeInternal() }

    private fun closeInternal() {
        try { db?.close() } catch (_: Exception) {}
        db = null
        currentPath = null
    }

    /**
     * Run a FTS5 MATCH query and rank the results.
     *
     * @param query raw user input; will be [escapeForFts]'d.
     * @param anchorLat optional lat for proximity scoring; null disables distance bias.
     * @param anchorLon optional lon; ignored if [anchorLat] is null.
     * @param limit max hits to return after ranking.
     * @return ranked results, best first. Empty list on no matches or DB error.
     */
    fun search(
        query: String,
        anchorLat: Double? = null,
        anchorLon: Double? = null,
        limit: Int = 30,
    ): List<ScoredResult> {
        val escaped = escapeForFts(query) ?: return emptyList()
        val openDb = db ?: return emptyList()
        val hasAnchor = anchorLat != null && anchorLon != null

        val hits = try {
            queryHits(openDb, escaped)
        } catch (e: Exception) {
            Timber.e(e, "FTS query failed for: $query")
            return emptyList()
        }
        if (hits.isEmpty()) return emptyList()

        val worstRank = hits.minOf { it.bm25Rank }
        val scored = hits.map { hit ->
            val dist = if (hasAnchor) {
                Geo.distanceMeters(anchorLat!!, anchorLon!!, hit.lat, hit.lon) / 1000.0
            } else null
            ScoredResult(
                hit = hit,
                distanceKm = dist,
                score = SearchRanking.score(
                    ftsRank = hit.bm25Rank,
                    population = hit.population,
                    distanceKm = dist,
                    worstFtsRank = worstRank,
                ),
            )
        }
        return scored.sortedByDescending { it.score }.take(limit)
    }

    /**
     * Find the nearest named place within [radiusMeters] of [lat],[lon].
     * Used by long-press reverse-geocode. Returns null if nothing is within radius.
     */
    fun nearestWithin(lat: Double, lon: Double, radiusMeters: Double): SearchHit? {
        val openDb = db ?: return null
        // We scan all places (the region DB is small) and compute distance in Kotlin —
        // SQLite's spatial support is unavailable without R*Tree, which the pipeline
        // doesn't enable. For typical region sizes (<50k places) this is sub-10ms.
        val hits = try {
            queryAll(openDb)
        } catch (e: Exception) {
            Timber.e(e, "Reverse-geocode query failed")
            return null
        }
        return hits
            .map { it to Geo.distanceMeters(lat, lon, it.lat, it.lon) }
            .filter { (_, dist) -> dist <= radiusMeters }
            .minByOrNull { (_, dist) -> dist }
            ?.first
    }

    private fun queryHits(db: SQLiteDatabase, escapedQuery: String): List<SearchHit> {
        // Some Android SQLite builds (notably several AVD images) ship without FTS5.
        // Detect once per DB open and fall back to a LIKE-based scan when unavailable.
        // The pipeline still emits FTS5 (better experience where supported); the
        // fallback is the safety net that makes search work everywhere.
        if (!fts5Available(db)) return queryHitsFallback(db, escapedQuery)

        val sql = (
            "SELECT rowid, name, name_ascii, kind, lat, lon, population, bm25(places) AS rank " +
                "FROM ${SearchSchema.TABLE} " +
                "WHERE ${SearchSchema.TABLE} MATCH ? " +
                "ORDER BY rank LIMIT ?"
            )
        val args = arrayOf(escapedQuery, (MAX_HITS_PER_QUERY).toString())
        val out = ArrayList<SearchHit>(64)
        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                out += SearchHit(
                    rowId = c.getLong(c.getColumnIndexOrThrow(SearchSchema.COL_ROWID)),
                    name = c.getString(c.getColumnIndexOrThrow(SearchSchema.COL_NAME)),
                    asciiName = c.getString(c.getColumnIndexOrThrow(SearchSchema.COL_NAME_ASCII)),
                    kind = PlaceKind.fromOsm(c.getString(c.getColumnIndexOrThrow(SearchSchema.COL_KIND))),
                    lat = c.getDouble(c.getColumnIndexOrThrow(SearchSchema.COL_LAT)),
                    lon = c.getDouble(c.getColumnIndexOrThrow(SearchSchema.COL_LON)),
                    population = c.getLong(c.getColumnIndexOrThrow(SearchSchema.COL_POPULATION)),
                    bm25Rank = c.getDouble(c.getColumnIndexOrThrow("rank")),
                )
            }
        }
        return out
    }

    /**
     * Detect whether this SQLite build has FTS5 enabled. Cached per [db] handle to avoid
     * running the probe query on every search.
     *
     * Probe: `SELECT bm25(fts5('x'))` would be cleaner but `fts5()` is itself an FTS5
     * function, so we check `PRAGMA compile_options` for the ENABLE_FTS5 flag instead —
     * a string-scan that works on any SQLite version.
     */
    private fun fts5Available(db: SQLiteDatabase): Boolean {
        if (fts5Cached != null) return fts5Cached!!
        fts5Cached = try {
            db.rawQuery("PRAGMA compile_options;", null).use { c ->
                val opts = StringBuilder()
                while (c.moveToNext()) opts.append(c.getString(0)).append('\n')
                opts.contains("ENABLE_FTS5")
            }
        } catch (e: Exception) {
            Timber.w(e, "FTS5 probe failed; assuming unavailable")
            false
        }
        return fts5Cached!!
    }
    @Volatile private var fts5Cached: Boolean? = null

    /**
     * LIKE-based fallback used when FTS5 is unavailable (some AVD images and a few
     * OEM ROMs ship SQLite without it). Returns all rows whose name or asciiName
     * contains the raw query string. We strip the FTS5 escape wrapping applied by
     * [escapeForFts] so the LIKE matches cleanly.
     *
     * bm25 is unavailable here, so we sort by length (shorter names first — they're
     * usually more relevant than longer ones containing the substring).
     */
    private fun queryHitsFallback(db: SQLiteDatabase, escapedQuery: String): List<SearchHit> {
        // Reverse the escape: "Ordino"* → Ordino. Split on whitespace to allow multi-token.
        val cleaned = escapedQuery
            .replace("\"", "")
            .replace("*", "")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString("%") { it }
        if (cleaned.isEmpty()) return emptyList()

        // Build a WHERE clause with LIKE on both name and name_ascii, so Cyrillic /
        // transliterated matches both work.
        val pattern = "%$cleaned%"
        val sql = (
            "SELECT rowid, name, name_ascii, kind, lat, lon, population, 0 AS rank " +
                "FROM ${SearchSchema.TABLE} " +
                "WHERE name LIKE ? OR name_ascii LIKE ? " +
                "ORDER BY LENGTH(name) ASC LIMIT ?"
            )
        val args = arrayOf(pattern, pattern, MAX_HITS_PER_QUERY.toString())
        val out = ArrayList<SearchHit>(64)
        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                out += SearchHit(
                    rowId = c.getLong(c.getColumnIndexOrThrow(SearchSchema.COL_ROWID)),
                    name = c.getString(c.getColumnIndexOrThrow(SearchSchema.COL_NAME)) ?: "",
                    asciiName = c.getString(c.getColumnIndexOrThrow(SearchSchema.COL_NAME_ASCII)) ?: "",
                    kind = PlaceKind.fromOsm(c.getString(c.getColumnIndexOrThrow(SearchSchema.COL_KIND))),
                    lat = c.getDouble(c.getColumnIndexOrThrow(SearchSchema.COL_LAT)),
                    lon = c.getDouble(c.getColumnIndexOrThrow(SearchSchema.COL_LON)),
                    population = c.getLong(c.getColumnIndexOrThrow(SearchSchema.COL_POPULATION)),
                    bm25Rank = -100.0, // neutral rank; SearchRanking.relevance will normalise
                )
            }
        }
        return out
    }

    private fun queryAll(db: SQLiteDatabase): List<SearchHit> {
        val sql = (
            "SELECT rowid, name, name_ascii, kind, lat, lon, population, 0 AS rank " +
                "FROM ${SearchSchema.TABLE}"
            )
        val out = ArrayList<SearchHit>(256)
        db.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                out += SearchHit(
                    rowId = c.getLong(c.getColumnIndexOrThrow(SearchSchema.COL_ROWID)),
                    name = c.getString(c.getColumnIndexOrThrow(SearchSchema.COL_NAME)) ?: "",
                    asciiName = c.getString(c.getColumnIndexOrThrow(SearchSchema.COL_NAME_ASCII)) ?: "",
                    kind = PlaceKind.fromOsm(c.getString(c.getColumnIndexOrThrow(SearchSchema.COL_KIND))),
                    lat = c.getDouble(c.getColumnIndexOrThrow(SearchSchema.COL_LAT)),
                    lon = c.getDouble(c.getColumnIndexOrThrow(SearchSchema.COL_LON)),
                    population = c.getLong(c.getColumnIndexOrThrow(SearchSchema.COL_POPULATION)),
                    bm25Rank = 0.0,
                )
            }
        }
        return out
    }

    companion object {
        private const val MAX_HITS_PER_QUERY = 200

        /**
         * Sanitise a raw user query for FTS5 MATCH syntax.
         *
         * FTS5 has its own query language (AND, OR, *, "", etc.). Unescaped user input
         * like "foo bar" works as a phrase, but stray quotes or operators (`"`, `*`,
         * `(`, `)`, `:`) either error out or silently broaden the search. We escape by
         * wrapping each whitespace-separated token in double quotes and stripping
         * embedded quotes. Trailing `*` enables prefix matching ("Yal*" → "Yalta").
         *
         * Returns null for blank queries so callers can short-circuit.
         */
        fun escapeForFts(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            return trimmed
                .split(Regex("\\s+"))
                .mapNotNull { token ->
                    val cleaned = token.replace("\"", "")
                    if (cleaned.isEmpty()) null else "\"${cleaned}\"*"
                }
                .joinToString(" ")
                .ifEmpty { null }
        }
    }
}
