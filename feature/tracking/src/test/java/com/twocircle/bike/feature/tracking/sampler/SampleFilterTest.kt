package com.twocircle.bike.feature.tracking.sampler

import com.google.common.truth.Truth.assertThat
import com.twocircle.bike.feature.tracking.model.PointSample
import org.junit.Test

class SampleFilterTest {

    private fun sample(
        lat: Double = 50.0,
        lon: Double = 30.0,
        accuracy: Float? = 5f,
        speed: Float? = 4f,
        ts: Long = 0L,
    ) = PointSample(lat, lon, ele = null, accuracy, speed, bearingDeg = null, timestampMs = ts)

    @Test
    fun `first sample with good accuracy is accepted`() {
        val s = sample(accuracy = 5f)
        assertThat(SampleFilter.accept(s, lastAccepted = null)).isEqualTo(s)
    }

    @Test
    fun `first sample with poor accuracy is still dropped`() {
        // The accuracy gate runs even for the first sample — we never want to seed the
        // track with a fix so bad it would corrupt aggregates from the start.
        val s = sample(accuracy = 100f)
        assertThat(SampleFilter.accept(s, lastAccepted = null)).isNull()
    }

    @Test
    fun `low accuracy sample dropped`() {
        val s = sample(accuracy = 80f)
        assertThat(SampleFilter.accept(s, lastAccepted = sample())).isNull()
    }

    @Test
    fun `sample at accuracy boundary accepted`() {
        val last = sample(ts = 0L)
        // Need to move ~10 m to clear spacing; place the candidate far enough.
        val s = sample(lat = 50.001, lon = 30.0, accuracy = 50f, ts = 5_000)
        assertThat(SampleFilter.accept(s, last)).isNotNull()
    }

    @Test
    fun `sample within spacing radius dropped`() {
        val last = sample(lat = 50.0, lon = 30.0, ts = 0L)
        // ~1 m away — below MIN_SPACING_METERS.
        val s = sample(lat = 50.00001, lon = 30.0, ts = 5_000)
        assertThat(SampleFilter.accept(s, last)).isNull()
    }

    @Test
    fun `sample beyond spacing radius accepted`() {
        val last = sample(ts = 0L)
        // ~100 m east.
        val s = sample(lat = 50.0, lon = 30.0015, ts = 30_000)
        assertThat(SampleFilter.accept(s, last)).isNotNull()
    }

    @Test
    fun `teleport sample dropped`() {
        val last = sample(lat = 50.0, lon = 30.0, ts = 0L)
        // 10 km away in 1 second — clearly a fly-away glitch.
        val s = sample(lat = 50.05, lon = 30.10, ts = 1_000)
        assertThat(SampleFilter.accept(s, last)).isNull()
    }

    @Test
    fun `plausible speed accepted`() {
        val last = sample(lat = 50.0, lon = 30.0, ts = 0L)
        // 10 m/s for 3 seconds → 30 m. Within plausibility.
        val s = sample(lat = 50.0003, lon = 30.0, ts = 3_000)
        assertThat(SampleFilter.accept(s, last)).isNotNull()
    }

    @Test
    fun `null accuracy is accepted`() {
        // Some fixes lack accuracy; we trust them rather than discarding.
        val s = sample(accuracy = null)
        assertThat(SampleFilter.accept(s, lastAccepted = null)).isEqualTo(s)
    }

    @Test
    fun `backward clock does not crash and accepts on plausibility`() {
        val last = sample(ts = 10_000)
        // Same place, older timestamp — dtSec clamped to 0; spacing rule applies.
        val s = sample(lat = 50.0, lon = 30.0, ts = 5_000)
        assertThat(SampleFilter.accept(s, last)).isNull() // spacing
    }
}
