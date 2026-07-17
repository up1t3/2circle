package com.twocircle.bike.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.twocircle.bike.data.db.converter.Converters
import com.twocircle.bike.data.db.dao.RegionDao
import com.twocircle.bike.data.db.dao.RoutePlanDao
import com.twocircle.bike.data.db.dao.SegmentDao
import com.twocircle.bike.data.db.dao.TrackDao
import com.twocircle.bike.data.db.dao.TrackPointDao
import com.twocircle.bike.data.db.dao.WaypointDao
import com.twocircle.bike.data.db.entity.RegionEntity
import com.twocircle.bike.data.db.entity.RoutePlanEntity
import com.twocircle.bike.data.db.entity.SegmentEntity
import com.twocircle.bike.data.db.entity.TrackEntity
import com.twocircle.bike.data.db.entity.TrackPointEntity
import com.twocircle.bike.data.db.entity.WaypointEntity

/**
 * Single Room database for the app's primary on-device store.
 *
 * WAL mode is enabled by the Hilt provider (not here) because it is a build-time
 * configuration of the openHelper factory. WAL lets the tracking service write while
 * the UI reads — a hard requirement for not blocking the map renderer on GPS writes.
 *
 * Schema version is exported (see build.gradle.ksp `room.schemaLocation`) so migrations
 * can be diffed and reviewed. Never bump [version] without a Migration + a test.
 */
@Database(
    entities = [
        TrackEntity::class,
        TrackPointEntity::class,
        RegionEntity::class,
        RoutePlanEntity::class,
        SegmentEntity::class,
        WaypointEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BikeDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun regionDao(): RegionDao
    abstract fun routePlanDao(): RoutePlanDao
    abstract fun segmentDao(): SegmentDao
    abstract fun waypointDao(): WaypointDao

    companion object {
        const val NAME = "twocircle.db"
    }
}
