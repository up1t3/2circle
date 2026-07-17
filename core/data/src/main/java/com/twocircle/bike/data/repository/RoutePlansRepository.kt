package com.twocircle.bike.data.repository

import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.common.outcome.Outcome
import com.twocircle.bike.data.db.dao.SegmentDao
import com.twocircle.bike.data.db.dao.WaypointDao
import com.twocircle.bike.data.db.dao.RoutePlanDao
import com.twocircle.bike.data.db.entity.RoutePlanEntity
import com.twocircle.bike.data.db.entity.SegmentEntity
import com.twocircle.bike.data.db.entity.WaypointEntity
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutePlansRepository @Inject constructor(
    private val routePlanDao: RoutePlanDao,
    private val segmentDao: SegmentDao,
    private val waypointDao: WaypointDao,
) {

    fun allPlansFlow(): Flow<List<RoutePlanEntity>> = routePlanDao.all()

    fun waypointsFlow(planId: String): Flow<List<WaypointEntity>> = waypointDao.forPlanFlow(planId)

    suspend fun planById(id: String): Outcome<RoutePlanEntity?> = try {
        Outcome.Success(routePlanDao.byId(id))
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }

    suspend fun savePlan(plan: RoutePlanEntity): Outcome<Unit> = try {
        routePlanDao.upsert(plan)
        Outcome.Success(Unit)
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }

    suspend fun replaceWaypoints(planId: String, waypoints: List<WaypointEntity>): Outcome<Unit> = try {
        waypointDao.replaceForPlan(planId, waypoints)
        Outcome.Success(Unit)
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }

    suspend fun replaceSegments(planId: String, segments: List<SegmentEntity>): Outcome<Unit> = try {
        segmentDao.replaceForPlan(planId, segments)
        Outcome.Success(Unit)
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }

    suspend fun segmentsFor(planId: String): Outcome<List<SegmentEntity>> = try {
        Outcome.Success(segmentDao.forPlan(planId))
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }

    suspend fun deletePlan(id: String): Outcome<Unit> = try {
        routePlanDao.delete(id)
        Outcome.Success(Unit)
    } catch (e: IOException) {
        Outcome.Failure(Failure.Storage.Inaccessible(e))
    }
}
