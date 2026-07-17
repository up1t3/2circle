package com.twocircle.bike.data.filesystem

import android.content.Context
import com.twocircle.bike.data.db.entity.RegionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves on-disk paths for a downloaded region's assets.
 *
 * The region package extracts to `<filesDir>/regions/<regionId>/` with a fixed layout:
 *   tiles.mbtiles / routing.rd5 / search.db / manifest.json
 *
 * Centralising path resolution here means feature modules never hardcode filenames —
 * if the layout changes, this is the only file to update.
 */
@Singleton
class RegionAssets @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val baseDir: File get() = File(context.filesDir, "regions")

    /** Directory holding all files for [region]. Created on first asset write. */
    fun regionDir(region: RegionEntity): File = File(baseDir, region.id)

    /** Absolute path to the vector tiles .mbtiles file for this region. */
    fun mbtilesPath(region: RegionEntity): File = File(regionDir(region), "tiles.mbtiles")

    /** Absolute path to the BRouter routing graph. */
    fun rd5Path(region: RegionEntity): File = File(regionDir(region), "routing.rd5")

    /** Absolute path to the offline FTS5 search database. */
    fun searchDbPath(region: RegionEntity): File = File(regionDir(region), "search.db")

    /** True iff the tiles file exists and is non-empty — minimum viable for rendering. */
    fun hasTiles(region: RegionEntity): Boolean = mbtilesPath(region).let { it.exists() && it.length() > 0 }
}
