package com.twocircle.bike.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.twocircle.bike.data.db.BikeDatabase
import com.twocircle.bike.data.db.dao.RegionDao
import com.twocircle.bike.data.db.dao.RoutePlanDao
import com.twocircle.bike.data.db.dao.SegmentDao
import com.twocircle.bike.data.db.dao.TrackDao
import com.twocircle.bike.data.db.dao.TrackPointDao
import com.twocircle.bike.data.db.dao.WaypointDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Database DI bindings. Single [BikeDatabase] for the app process.
 *
 * WAL mode is enabled here via the openHelper callback: it lets the tracking service's
 * write transaction proceed concurrently with the UI's read transactions — critical for
 * not stalling the map renderer on GPS inserts.
 *
 * `enableWriteAheadLogging` is also called on the open callback as a belt-and-braces
 * measure: Room's WAL configuration is honoured only after the DB is opened, and the
 * callback fires exactly once on first access.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BikeDatabase =
        Room.databaseBuilder(context, BikeDatabase::class.java, BikeDatabase.NAME)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    // Hardening: enforce foreign keys (CASCADE deletes on TrackEntity etc.)
                    // and WAL even if the builder's journalMode was overridden by a migration.
                    db.execSQL("PRAGMA foreign_keys = ON")
                    db.execSQL("PRAGMA journal_mode = WAL")
                    // SYNCHRONOUS=NORMAL is the safe companion to WAL: appends are durable
                    // across crashes; a power loss can lose the last transaction, which is
                    // acceptable for telemetry (the rider resumes from the last flush).
                    db.execSQL("PRAGMA synchronous = NORMAL")
                }
            })
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun trackDao(db: BikeDatabase): TrackDao = db.trackDao()
    @Provides fun trackPointDao(db: BikeDatabase): TrackPointDao = db.trackPointDao()
    @Provides fun regionDao(db: BikeDatabase): RegionDao = db.regionDao()
    @Provides fun routePlanDao(db: BikeDatabase): RoutePlanDao = db.routePlanDao()
    @Provides fun segmentDao(db: BikeDatabase): SegmentDao = db.segmentDao()
    @Provides fun waypointDao(db: BikeDatabase): WaypointDao = db.waypointDao()
}
