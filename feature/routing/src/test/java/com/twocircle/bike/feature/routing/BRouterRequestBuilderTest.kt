package com.twocircle.bike.feature.routing

import com.google.common.truth.Truth.assertThat
import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.RoutingProfile
import com.twocircle.bike.domain.model.Waypoint
import com.twocircle.bike.domain.model.WaypointId
import com.twocircle.bike.feature.routing.api.BRouterRequestBuilder
import org.junit.Test

class BRouterRequestBuilderTest {

    private fun wp(lat: Double, lon: Double, idx: Int) = Waypoint(
        id = WaypointId("wp-$idx"),
        coord = Coord(lat, lon),
        role = if (idx == 0) Waypoint.Role.Start else Waypoint.Role.Via,
    )

    @Test
    fun `lonlats uses lon-first pipe-separated format`() {
        val wps = listOf(
            wp(47.211, 8.731, 0),
            wp(47.219, 8.744, 1),
        )
        // Note: lon,lat order per BRouter / GeoJSON convention.
        assertThat(BRouterRequestBuilder.buildLonLats(wps))
            .isEqualTo("8.731000,47.211000|8.744000,47.219000")
    }

    @Test
    fun `single waypoint produces single pair`() {
        val wps = listOf(wp(10.0, 20.0, 0))
        assertThat(BRouterRequestBuilder.buildLonLats(wps)).isEqualTo("20.000000,10.000000")
    }

    @Test
    fun `coordinates truncated to 6 decimals`() {
        // GPS jitter is ~3-5 m; 6 decimals (~10 cm) is overkill but URL-friendly.
        val wps = listOf(wp(47.211123456789, 8.731987654321, 0))
        assertThat(BRouterRequestBuilder.buildLonLats(wps)).isEqualTo("8.731988,47.211123")
    }

    @Test
    fun `profile mapping covers all routing profiles`() {
        assertThat(BRouterRequestBuilder.profileName(RoutingProfile.Touring)).isEqualTo("trekking")
        assertThat(BRouterRequestBuilder.profileName(RoutingProfile.Road)).isEqualTo("fastbike")
        assertThat(BRouterRequestBuilder.profileName(RoutingProfile.Mtb)).isEqualTo("mundo")
    }
}
