package com.twocircle.bike.feature.tracking.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackAggregatesTest {

    private fun sample(
        lat: Double, lon: Double, ele: Double? = null,
        speed: Float? = 4f, ts: Long,
    ) = PointSample(lat, lon, ele, accuracyMeters = 5f, speed, bearingDeg = null, timestampMs = ts)

    @Test
    fun `first sample initialises timestamps and count`() {
        val s = sample(lat = 50.0, lon = 30.0, speed = 5f, ts = 1_000L)
        val agg = TrackAggregates().withAcceptedSample(previous = null, new = s, nowMs = 2_000L)
        assertThat(agg.pointCount).isEqualTo(1)
        assertThat(agg.startedAtMs).isEqualTo(1_000L)
        assertThat(agg.distanceMeters).isEqualTo(0.0)
        assertThat(agg.maxSpeedMps).isEqualTo(5.0)
        assertThat(agg.elapsedSeconds).isEqualTo(1L)
    }

    @Test
    fun `distance accumulates between consecutive samples`() {
        val a = sample(lat = 50.0, lon = 30.0, ts = 0L)
        // ~1 degree east at lat 50 ≈ ~72 km.
        val b = sample(lat = 50.0, lon = 31.0, speed = 5f, ts = 14_400_000L) // 4 hours later
        val agg = TrackAggregates().withAcceptedSample(null, a, 0L).withAcceptedSample(a, b, 14_400_000L)
        assertThat(agg.distanceMeters).isAtLeast(70_000.0)
        assertThat(agg.distanceMeters).isAtMost(75_000.0)
        assertThat(agg.pointCount).isEqualTo(2)
    }

    @Test
    fun `ascent and descent accumulate from elevation deltas`() {
        val a = sample(lat = 50.0, lon = 30.0, ele = 100.0, ts = 0L)
        val b = sample(lat = 50.0, lon = 30.001, ele = 150.0, ts = 10_000) // +50
        val c = sample(lat = 50.0, lon = 30.002, ele = 130.0, ts = 20_000) // -20
        val agg = TrackAggregates()
            .withAcceptedSample(null, a, 0L)
            .withAcceptedSample(a, b, 10_000)
            .withAcceptedSample(b, c, 20_000)
        assertThat(agg.ascentMeters).isWithin(0.01).of(50.0)
        assertThat(agg.descentMeters).isWithin(0.01).of(20.0)
    }

    @Test
    fun `missing elevation leaves ascent and descent unchanged`() {
        val a = sample(lat = 50.0, lon = 30.0, ele = null, ts = 0L)
        val b = sample(lat = 50.0, lon = 30.001, ele = null, ts = 10_000)
        val agg = TrackAggregates().withAcceptedSample(null, a, 0L).withAcceptedSample(a, b, 10_000)
        assertThat(agg.ascentMeters).isEqualTo(0.0)
        assertThat(agg.descentMeters).isEqualTo(0.0)
    }

    @Test
    fun `moving time excludes stationary samples`() {
        val a = sample(lat = 50.0, lon = 30.0, speed = 5f, ts = 0L)
        // 10 seconds "stopped" (speed below 1.0).
        val b = sample(lat = 50.0, lon = 30.0, speed = 0.0f, ts = 10_000)
        val agg = TrackAggregates().withAcceptedSample(null, a, 0L).withAcceptedSample(a, b, 10_000)
        // Speed 0 → not moving → 0 seconds added.
        assertThat(agg.movingSeconds).isEqualTo(0L)
    }

    @Test
    fun `moving time accumulates when speed is above threshold`() {
        val a = sample(lat = 50.0, lon = 30.0, speed = 5f, ts = 0L)
        val b = sample(lat = 50.0, lon = 30.001, speed = 5f, ts = 10_000)
        val agg = TrackAggregates().withAcceptedSample(null, a, 0L).withAcceptedSample(a, b, 10_000)
        assertThat(agg.movingSeconds).isEqualTo(10L)
    }

    @Test
    fun `max speed tracks the highest observed`() {
        val a = sample(lat = 50.0, lon = 30.0, speed = 5f, ts = 0L)
        val b = sample(lat = 50.0, lon = 30.0001, speed = 12f, ts = 1_000)
        val c = sample(lat = 50.0, lon = 30.0002, speed = 8f, ts = 2_000)
        val agg = TrackAggregates()
            .withAcceptedSample(null, a, 0L)
            .withAcceptedSample(a, b, 1_000)
            .withAcceptedSample(b, c, 2_000)
        assertThat(agg.maxSpeedMps).isEqualTo(12.0)
    }

    @Test
    fun `average speed is total distance over moving time`() {
        // 1000 m, 100 s moving → 10 m/s.
        val a = sample(lat = 0.0, lon = 0.0, speed = 10f, ts = 0L)
        val b = sample(lat = 0.0, lon = 0.00899, speed = 10f, ts = 100_000) // ~1 km at equator
        val agg = TrackAggregates().withAcceptedSample(null, a, 0L).withAcceptedSample(a, b, 100_000)
        assertThat(agg.avgSpeedMps).isWithin(0.5).of(10.0)
    }
}
