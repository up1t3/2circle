package com.twocircle.bike.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.twocircle.bike.data.db.entity.RegionEntity
import com.twocircle.bike.data.db.entity.RegionInstallState
import com.twocircle.bike.data.db.entity.RoutePlanEntity
import com.twocircle.bike.data.db.entity.SegmentEntity
import com.twocircle.bike.data.db.entity.TrackEntity
import com.twocircle.bike.data.db.entity.TrackPointEntity
import com.twocircle.bike.data.db.entity.TrackSource
import com.twocircle.bike.data.db.entity.TrackStatus
import com.twocircle.bike.data.db.entity.WaypointEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Database-level integration test on an in-memory Room via Robolectric.
 *
 * Covers the red-line guarantees: crash-safe point persistence, FK cascade, and the
 * seq-monotonicity contract the tracking service depends on for resume-after-crash.
 */
@RunWith(RobolectricTestRunner::class)
class BikeDatabaseTest {

    private lateinit var db: BikeDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BikeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `track upsert and read by id`() = runTest {
        val track = TrackEntity(
            id = "t1",
            name = "Morning ride",
            startedAtMs = 1_000L,
            status = TrackStatus.Recording,
        )
        db.trackDao().upsert(track)

        val loaded = db.trackDao().byId("t1")
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.name).isEqualTo("Morning ride")
        assertThat(loaded.status).isEqualTo(TrackStatus.Recording)
    }

    @Test
    fun `track status transition persists`() = runTest {
        db.trackDao().upsert(
            TrackEntity(
                id = "t2", name = "x", startedAtMs = 1L, status = TrackStatus.Recording,
            ),
        )
        db.trackDao().setStatus("t2", TrackStatus.Interrupted)

        assertThat(db.trackDao().byId("t2")!!.status).isEqualTo(TrackStatus.Interrupted)
    }

    @Test
    fun `find recoverable tracks returns recording paused interrupted`() = runTest {
        db.trackDao().upsert(
            TrackEntity(id = "a", name = "x", startedAtMs = 1L, status = TrackStatus.Recording),
        )
        db.trackDao().upsert(
            TrackEntity(id = "b", name = "x", startedAtMs = 2L, status = TrackStatus.Paused),
        )
        db.trackDao().upsert(
            TrackEntity(id = "c", name = "x", startedAtMs = 3L, status = TrackStatus.Interrupted),
        )
        db.trackDao().upsert(
            TrackEntity(id = "d", name = "x", startedAtMs = 4L, status = TrackStatus.Finished),
        )

        val recoverable = db.trackDao().withStatus(
            listOf(TrackStatus.Recording, TrackStatus.Paused, TrackStatus.Interrupted),
        ).map { it.id }
        assertThat(recoverable).containsExactly("a", "b", "c")
    }

    @Test
    fun `point batch insert is crash-safe monotonic seq`() = runTest {
        db.trackDao().upsert(
            TrackEntity(id = "p1", name = "x", startedAtMs = 0L, status = TrackStatus.Recording),
        )
        // Simulate the first flush: 5 points.
        val first = (0L..4L).map { seq ->
            TrackPointEntity(
                trackId = "p1", seq = seq,
                lat = 50.0 + seq, lon = 30.0, timestampMs = seq * 1000L,
            )
        }
        db.trackPointDao().insertAll(first)

        // Crash. On restart, the service queries maxSeq to know where to continue.
        val nextSeq = db.trackPointDao().maxSeqForTrack("p1") + 1L
        assertThat(nextSeq).isEqualTo(5L)

        // Second flush continues from nextSeq — no overlap.
        val second = (nextSeq..nextSeq + 2L).map { seq ->
            TrackPointEntity(
                trackId = "p1", seq = seq,
                lat = 60.0 + seq, lon = 30.0, timestampMs = seq * 1000L,
            )
        }
        db.trackPointDao().insertAll(second)

        assertThat(db.trackPointDao().countForTrack("p1")).isEqualTo(8)
        val all = db.trackPointDao().pointsForTrack("p1")
        assertThat(all.map { it.seq })
            .containsExactly(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L).inOrder()
    }

    @Test
    fun `duplicate seq insert is ignored`() = runTest {
        db.trackDao().upsert(
            TrackEntity(id = "d1", name = "x", startedAtMs = 0L, status = TrackStatus.Recording),
        )
        val p1 = TrackPointEntity(
            trackId = "d1", seq = 0L, lat = 1.0, lon = 1.0, timestampMs = 0L,
        )
        db.trackPointDao().insertAll(listOf(p1))

        // Retry the same flush after a transient error: same seq must not duplicate.
        db.trackPointDao().insertAll(listOf(p1.copy(rowId = 999L)))

        assertThat(db.trackPointDao().countForTrack("d1")).isEqualTo(1)
    }

    @Test
    fun `deleting track cascades to its points`() = runTest {
        db.trackDao().upsert(
            TrackEntity(id = "cas1", name = "x", startedAtMs = 0L, status = TrackStatus.Recording),
        )
        db.trackPointDao().insertAll(
            listOf(
                TrackPointEntity(trackId = "cas1", seq = 0L, lat = 1.0, lon = 1.0, timestampMs = 0L),
                TrackPointEntity(trackId = "cas1", seq = 1L, lat = 1.1, lon = 1.1, timestampMs = 1000L),
            ),
        )
        assertThat(db.trackPointDao().countForTrack("cas1")).isEqualTo(2)

        db.trackDao().delete("cas1")

        assertThat(db.trackPointDao().countForTrack("cas1")).isEqualTo(0)
    }

    @Test
    fun `aggregates update in place`() = runTest {
        db.trackDao().upsert(
            TrackEntity(id = "ag1", name = "x", startedAtMs = 0L, status = TrackStatus.Recording),
        )
        db.trackDao().updateAggregates(
            id = "ag1",
            distanceMeters = 12_345.0,
            movingSeconds = 1800L,
            elapsedSeconds = 2000L,
            ascentMeters = 120.0,
            descentMeters = 80.0,
            maxSpeedMps = 9.5,
            avgSpeedMps = 6.85,
            endedAtMs = null,
            updatedAtMs = 12345L,
        )
        val t = db.trackDao().byId("ag1")!!
        assertThat(t.distanceMeters).isEqualTo(12_345.0)
        assertThat(t.ascentMeters).isEqualTo(120.0)
        assertThat(t.maxSpeedMps).isEqualTo(9.5)
    }

    @Test
    fun `region bbox coverage query`() = runTest {
        db.regionDao().upsert(
            RegionEntity(
                id = "carpathians", name = "Carpathians", version = 1, sizeBytes = 50_000_000,
                boundsMinLat = 47.0, boundsMinLon = 22.0, boundsMaxLat = 50.0, boundsMaxLon = 26.0,
                installState = RegionInstallState.Installed,
            ),
        )
        // Inside bbox.
        assertThat(db.regionDao().installedRegionContaining(48.5, 24.0)?.id).isEqualTo("carpathians")
        // Outside bbox.
        assertThat(db.regionDao().installedRegionContaining(40.0, 10.0)).isNull()
    }

    @Test
    fun `non-installed region does not provide coverage`() = runTest {
        db.regionDao().upsert(
            RegionEntity(
                id = "dl", name = "Downloading", version = 1, sizeBytes = 1,
                boundsMinLat = 0.0, boundsMinLon = 0.0, boundsMaxLat = 89.0, boundsMaxLon = 179.0,
                installState = RegionInstallState.Downloading,
            ),
        )
        // Even though the point is inside the bbox, the region is not Installed yet.
        assertThat(db.regionDao().installedRegionContaining(50.0, 30.0)).isNull()
    }

    @Test
    fun `segment replace is atomic`() = runTest {
        db.routePlanDao().upsert(
            RoutePlanEntity(
                id = "rp1", name = "p", profile = "Touring",
                createdAtMs = 0L, updatedAtMs = 0L,
            ),
        )
        val s1 = listOf(
            SegmentEntity(
                routePlanId = "rp1", orderIdx = 0,
                fromLat = 0.0, fromLon = 0.0, fromName = null,
                toLat = 1.0, toLon = 1.0, toName = null,
                geometryJson = "[]", distanceMeters = 1000.0, plannedSeconds = 200L,
                ascentMeters = 0.0, descentMeters = 0.0, surface = "Asphalt", smoothness = "Good",
            ),
        )
        db.segmentDao().replaceForPlan("rp1", s1)
        assertThat(db.segmentDao().forPlan("rp1")).hasSize(1)

        // Re-planning with new geometry replaces, not appends.
        db.segmentDao().replaceForPlan("rp1", s1 + s1.map { it.copy(orderIdx = 1) })
        assertThat(db.segmentDao().forPlan("rp1")).hasSize(2)
    }

    @Test
    fun `waypoint replace preserves ordering`() = runTest {
        db.routePlanDao().upsert(
            RoutePlanEntity(
                id = "rp2", name = "p", profile = "Touring",
                createdAtMs = 0L, updatedAtMs = 0L,
            ),
        )
        val wps = listOf(
            WaypointEntity(routePlanId = "rp2", orderIdx = 0, lat = 0.0, lon = 0.0,
                name = "start", role = "Start", source = "Manual"),
            WaypointEntity(routePlanId = "rp2", orderIdx = 1, lat = 1.0, lon = 1.0,
                name = "end", role = "End", source = "Search"),
        )
        db.waypointDao().replaceForPlan("rp2", wps)

        val loaded = db.waypointDao().forPlan("rp2")
        assertThat(loaded.map { it.orderIdx }).containsExactly(0, 1).inOrder()
        assertThat(loaded.map { it.role }).containsExactly("Start", "End").inOrder()
    }

    @Test
    fun `track source default is Live`() = runTest {
        db.trackDao().upsert(
            TrackEntity(id = "ts1", name = "x", startedAtMs = 0L, status = TrackStatus.Finished),
        )
        // source defaults to Live when omitted on construction.
        assertThat(db.trackDao().byId("ts1")!!.source).isEqualTo(TrackSource.Live)
    }
}
