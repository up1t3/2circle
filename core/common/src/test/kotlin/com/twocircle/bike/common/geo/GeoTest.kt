package com.twocircle.bike.common.geo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeoTest {

    @Test
    fun `distance between identical points is zero`() {
        val d = Geo.distanceMeters(50.0, 30.0, 50.0, 30.0)
        assertThat(d).isWithin(0.001).of(0.0)
    }

    @Test
    fun `distance Kyiv to Lviv is roughly 470 km`() {
        // Kyiv 50.4501, 30.5234 ; Lviv 49.8397, 24.0297 ; road ≈ 470-480 km, great-circle ≈ 468 km
        val d = Geo.distanceMeters(50.4501, 30.5234, 49.8397, 24.0297)
        assertThat(d / 1000.0).isWithin(5.0).of(468.0)
    }

    @Test
    fun `distance along 1 degree of latitude is ~111 km`() {
        val d = Geo.distanceMeters(0.0, 0.0, 1.0, 0.0)
        assertThat(d / 1000.0).isWithin(0.5).of(111.32)
    }

    @Test
    fun `bearing due east is 90 degrees`() {
        val b = Geo.bearingDeg(0.0, 0.0, 0.0, 1.0)
        assertThat(b).isWithin(0.01).of(90.0)
    }

    @Test
    fun `bearing due north is 0 degrees`() {
        val b = Geo.bearingDeg(0.0, 0.0, 1.0, 0.0)
        assertThat(b).isWithin(0.01).of(0.0)
    }

    @Test
    fun `gradient is positive for uphill`() {
        assertThat(gradientPct(1000.0, 80.0)).isWithin(0.01).of(8.0)
    }

    @Test
    fun `gradient is zero for zero distance`() {
        assertThat(gradientPct(0.0, 80.0)).isEqualTo(0.0)
    }

    @Test
    fun `total ascent sums only positive deltas`() {
        assertThat(totalAscentMeters(listOf(100.0, 150.0, 120.0, 200.0))).isEqualTo(130.0)
    }

    @Test
    fun `total descent sums only negative deltas`() {
        assertThat(totalDescentMeters(listOf(200.0, 150.0, 180.0, 100.0))).isEqualTo(130.0)
    }

    @Test
    fun `bounding box has expected span at equator`() {
        val bbox = boundingBox(0.0, 0.0, 1_000.0)
        // ~0.009 degrees per km
        assertThat(bbox[0]).isWithin(0.001).of(-0.00898)
        assertThat(bbox[2]).isWithin(0.001).of(0.00898)
    }

    @Test
    fun `LatLon rejects invalid coordinates`() {
        try {
            LatLon(91.0, 0.0)
            assert(false) { "expected exception" }
        } catch (_: IllegalArgumentException) {
        }
        try {
            LatLon(0.0, -181.0)
            assert(false) { "expected exception" }
        } catch (_: IllegalArgumentException) {
        }
    }
}
