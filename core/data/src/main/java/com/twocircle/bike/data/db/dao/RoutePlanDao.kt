package com.twocircle.bike.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.twocircle.bike.data.db.entity.RoutePlanEntity
import com.twocircle.bike.data.db.entity.SegmentEntity
import com.twocircle.bike.data.db.entity.WaypointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutePlanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: RoutePlanEntity)

    @Update suspend fun update(plan: RoutePlanEntity)

    @Query("SELECT * FROM route_plans ORDER BY updatedAtMs DESC")
    fun all(): Flow<List<RoutePlanEntity>>

    @Query("SELECT * FROM route_plans WHERE id = :id")
    suspend fun byId(id: String): RoutePlanEntity?

    @Query("DELETE FROM route_plans WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface SegmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(segments: List<SegmentEntity>)

    /** Replace all segments for a plan in one transaction — atomic re-plan. */
    @Transaction
    suspend fun replaceForPlan(planId: String, segments: List<SegmentEntity>) {
        deleteForPlan(planId)
        upsertAll(segments)
    }

    @Query("DELETE FROM route_segments WHERE routePlanId = :planId")
    suspend fun deleteForPlan(planId: String)

    @Query("SELECT * FROM route_segments WHERE routePlanId = :planId ORDER BY orderIdx ASC")
    suspend fun forPlan(planId: String): List<SegmentEntity>
}

@Dao
interface WaypointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(waypoints: List<WaypointEntity>)

    /** Replace all waypoints for a plan in one transaction. */
    @Transaction
    suspend fun replaceForPlan(planId: String, waypoints: List<WaypointEntity>) {
        deleteForPlan(planId)
        upsertAll(waypoints)
    }

    @Query("DELETE FROM waypoints WHERE routePlanId = :planId")
    suspend fun deleteForPlan(planId: String)

    @Query("SELECT * FROM waypoints WHERE routePlanId = :planId ORDER BY orderIdx ASC")
    fun forPlanFlow(planId: String): Flow<List<WaypointEntity>>

    @Query("SELECT * FROM waypoints WHERE routePlanId = :planId ORDER BY orderIdx ASC")
    suspend fun forPlan(planId: String): List<WaypointEntity>

    @Query("DELETE FROM waypoints WHERE rowId = :rowId")
    suspend fun delete(rowId: Long)
}
