package com.twocircle.bike.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.twocircle.bike.data.db.entity.RegionEntity
import com.twocircle.bike.data.db.entity.RegionInstallState
import kotlinx.coroutines.flow.Flow

@Dao
interface RegionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(region: RegionEntity)

    @Query("SELECT * FROM regions ORDER BY name ASC")
    fun all(): Flow<List<RegionEntity>>

    @Query("SELECT * FROM regions WHERE id = :id")
    suspend fun byId(id: String): RegionEntity?

    @Query("SELECT * FROM regions WHERE installState = :state")
    suspend fun withState(state: RegionInstallState): List<RegionEntity>

    @Query("UPDATE regions SET installState = :state, installedAtMs = :installedAtMs WHERE id = :id")
    suspend fun setState(id: String, state: RegionInstallState, installedAtMs: Long?)

    @Query("DELETE FROM regions WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Find an installed region whose bounding box contains the point. Used to answer
     * "does this coord have offline coverage?" for routing/search. Cheap: only installed
     * regions are scanned and bbox checks are O(1) per row.
     */
    @Query(
        """
        SELECT * FROM regions
        WHERE installState = 'Installed'
          AND :lat BETWEEN boundsMinLat AND boundsMaxLat
          AND :lon BETWEEN boundsMinLon AND boundsMaxLon
        LIMIT 1
        """,
    )
    suspend fun installedRegionContaining(lat: Double, lon: Double): RegionEntity?
}
