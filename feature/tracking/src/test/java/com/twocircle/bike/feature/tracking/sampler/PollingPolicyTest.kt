package com.twocircle.bike.feature.tracking.sampler

import com.google.common.truth.Truth.assertThat
import com.twocircle.bike.feature.tracking.model.PointSample
import com.twocircle.bike.feature.tracking.model.SamplerState
import org.junit.Test

class PollingPolicyTest {

    private val now = 1_000_000L

    private fun sample(speed: Float?, ts: Long = now): PointSample = PointSample(
        lat = 50.0, lon = 30.0, ele = null,
        accuracyMeters = 5f, speedMps = speed, bearingDeg = null, timestampMs = ts,
    )

    @Test
    fun `no samples yields Acquiring`() {
        assertThat(PollingPolicy.next(SamplerState.Acquiring, emptyList(), now))
            .isEqualTo(SamplerState.Acquiring)
    }

    @Test
    fun `fast sample immediately transitions Acquiring to Moving`() {
        val s = sample(speed = 6.0f) // 6 m/s clearly moving
        val state = PollingPolicy.next(SamplerState.Acquiring, listOf(s), now)
        assertThat(state).isInstanceOf(SamplerState.Moving::class.java)
        assertThat((state as SamplerState.Moving).intervalMs).isEqualTo(PollingPolicy.ACTIVE_INTERVAL_MS)
    }

    @Test
    fun `slow sample under grace keeps Acquiring`() {
        // One slow sample, well within grace period.
        val s = sample(speed = 0.0f, ts = now - 1_000)
        val state = PollingPolicy.next(SamplerState.Acquiring, listOf(s), now)
        assertThat(state).isEqualTo(SamplerState.Acquiring)
    }

    @Test
    fun `slow samples past grace enter Stationary`() {
        // Slow streak spanning 10s, past the 8s grace.
        val a = sample(speed = 0.0f, ts = now - 10_000)
        val b = sample(speed = 0.0f, ts = now - 5_000)
        val state = PollingPolicy.next(SamplerState.Acquiring, listOf(b, a), now)
        assertThat(state).isInstanceOf(SamplerState.Stationary::class.java)
        assertThat((state as SamplerState.Stationary).intervalMs).isEqualTo(PollingPolicy.STATIONARY_INTERVAL_MS)
    }

    @Test
    fun `Moving state stays Moving while recent sample is fast`() {
        val moving = SamplerState.Moving(speedMps = 5.0, intervalMs = PollingPolicy.ACTIVE_INTERVAL_MS)
        val s = sample(speed = 5.0f)
        val state = PollingPolicy.next(moving, listOf(s), now)
        assertThat(state).isInstanceOf(SamplerState.Moving::class.java)
    }

    @Test
    fun `Moving state respects grace before dropping to Stationary`() {
        val moving = SamplerState.Moving(speedMps = 5.0, intervalMs = PollingPolicy.ACTIVE_INTERVAL_MS)
        // A single short slow sample: still within grace.
        val s = sample(speed = 0.0f, ts = now - 2_000)
        val state = PollingPolicy.next(moving, listOf(s), now)
        assertThat(state).isInstanceOf(SamplerState.Moving::class.java)
    }

    @Test
    fun `Moving drops to Stationary after extended slow streak`() {
        val moving = SamplerState.Moving(speedMps = 5.0, intervalMs = PollingPolicy.ACTIVE_INTERVAL_MS)
        val a = sample(speed = 0.0f, ts = now - 12_000)
        val b = sample(speed = 0.0f, ts = now - 6_000)
        val c = sample(speed = 0.0f, ts = now)
        val state = PollingPolicy.next(moving, listOf(c, b, a), now)
        assertThat(state).isInstanceOf(SamplerState.Stationary::class.java)
    }

    @Test
    fun `Stationary immediately resumes Moving when speed jumps`() {
        val stationary = SamplerState.Stationary(sinceMs = now - 60_000, intervalMs = PollingPolicy.STATIONARY_INTERVAL_MS)
        val s = sample(speed = 4.0f)
        val state = PollingPolicy.next(stationary, listOf(s), now)
        assertThat(state).isInstanceOf(SamplerState.Moving::class.java)
        assertThat((state as SamplerState.Moving).intervalMs).isEqualTo(PollingPolicy.ACTIVE_INTERVAL_MS)
    }

    @Test
    fun `intervalFor maps each state to its interval`() {
        assertThat(PollingPolicy.intervalFor(SamplerState.Acquiring))
            .isEqualTo(PollingPolicy.ACQUIRING_INTERVAL_MS)
        assertThat(PollingPolicy.intervalFor(
            SamplerState.Moving(speedMps = 5.0, intervalMs = 3_000)
        )).isEqualTo(3_000L)
        assertThat(PollingPolicy.intervalFor(
            SamplerState.Stationary(sinceMs = 0L, intervalMs = 30_000)
        )).isEqualTo(30_000L)
    }
}
