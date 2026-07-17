package com.twocircle.bike.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A downloaded offline region — a bundle of tiles.mbtiles + routing.rd5 + search.db + DEM.
 *
 * Stored in the DB for cataloguing (list/delete UI); the actual files live on disk under
 * `regions/<regionId>/`. [installState] tracks the download/verify/extract lifecycle so
 * the regions UI can resume or clean up an interrupted install.
 *
 * [boundsMinLat/minLon/maxLat/maxLon] form the bounding box. Used to answer "does this
 * coord have offline coverage?" without touching the filesystem.
 */
@Entity(
    tableName = "regions",
    indices = [Index("installState")],
)
data class RegionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: Int,
    val sizeBytes: Long,
    val boundsMinLat: Double,
    val boundsMinLon: Double,
    val boundsMaxLat: Double,
    val boundsMaxLon: Double,
    val installState: RegionInstallState,
    /** When the install completed (epoch ms); null while downloading/extracting. */
    val installedAtMs: Long? = null,
    /** Backend URL the package was fetched from; allows update-checks. */
    val sourceUrl: String? = null,
)

enum class RegionInstallState {
    NotInstalled,
    Downloading,
    Verifying,
    Extracting,
    Installed,
    /** Download or verification failed; needs cleanup before retry. */
    Failed,
}
