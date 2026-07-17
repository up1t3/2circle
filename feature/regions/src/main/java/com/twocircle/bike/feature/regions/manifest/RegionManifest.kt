package com.twocircle.bike.feature.regions.manifest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Region manifest — the catalog the backend pipeline publishes.
 *
 * The pipeline (Step 9) builds `.mbtiles` + `routing.rd5` + `search.db` per region,
 * zips them with a `manifest.json` sibling, and serves both via a static host.
 * The app fetches this index to populate the "Regions" tab and to drive downloads.
 *
 * Schema is intentionally minimal: every field here corresponds to a column we need in
 * the local [com.twocircle.bike.data.db.entity.RegionEntity] row, so the mapping is 1:1.
 *
 * Backward-compat: unknown keys are ignored ([Json.ignoreUnknownKeys] in the downloader)
 * so adding fields to the manifest never breaks older clients.
 */
@Serializable
data class RegionManifest(
    /** Schema version of the manifest itself (independent of [RegionEntry.version]). */
    val schema: Int = 1,
    val regions: List<RegionEntry> = emptyList(),
)

@Serializable
data class RegionEntry(
    /** Stable identifier; matches the local DB row and the on-disk directory name. */
    val id: String,
    val name: String,
    /** Monotonically increasing; bumped when the pipeline rebuilds the region. */
    val version: Int,
    /** Total size of the zipped package, in bytes (for the "12 MB" UI hint). */
    val sizeBytes: Long,
    /** WGS84 bounds; used to answer "is this coord covered offline?" without the file. */
    val bounds: RegionBounds,
    /** Absolute URL of the .zip package to download. */
    @SerialName("download_url") val downloadUrl: String,
    /** SHA-256 of the package; verified after download to catch truncated transfers. */
    val sha256: String? = null,
)

@Serializable
data class RegionBounds(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
)
