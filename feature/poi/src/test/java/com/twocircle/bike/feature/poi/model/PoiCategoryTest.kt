package com.twocircle.bike.feature.poi.model

import com.google.common.truth.Truth.assertThat
import com.twocircle.bike.feature.search.engine.PlaceKind
import org.junit.Test

/**
 * Guards the [PoiCategory] → [PlaceKind] mapping — the bottom-sheet chips must query the
 * same kinds the backend pipeline writes into `search.db`. A mismatch here shows up as
 * an empty result for a chip that the user expects to be populated.
 */
class PoiCategoryTest {

    @Test
    fun `every category references at least one PlaceKind`() {
        // A chip with no kinds would never return results — pointless.
        PoiCategory.entries.forEach { cat ->
            assertThat(cat.kinds).isNotEmpty()
        }
    }

    @Test
    fun `pharmacy maps to exactly pharmacy`() {
        assertThat(PoiCategory.Pharmacy.kinds).containsExactly(PlaceKind.Pharmacy)
    }

    @Test
    fun `cafe aggregates cafe and restaurant`() {
        // The chip label says "Cafe" but the query must include restaurants too —
        // on tour both are interchangeable for finding food.
        assertThat(PoiCategory.Cafe.kinds).containsExactly(PlaceKind.Cafe, PlaceKind.Restaurant)
    }

    @Test
    fun `water aggregates drinking_water and natural spring`() {
        // Springs are also drinking-water sources in the backcountry.
        assertThat(PoiCategory.Water.kinds).containsExactly(PlaceKind.Water, PlaceKind.Spring)
    }

    @Test
    fun `lodging aggregates hotel variants plus campsite`() {
        assertThat(PoiCategory.Lodging.kinds).containsExactly(PlaceKind.Hotel, PlaceKind.Campsite)
    }

    @Test
    fun `bicycle service aggregates repair and rental`() {
        assertThat(PoiCategory.BicycleService.kinds)
            .containsExactly(PlaceKind.BicycleService, PlaceKind.BicycleRental)
    }

    @Test
    fun `all kinds is the union of every category`() {
        val expected = PoiCategory.entries.flatMap { it.kinds }.toSet()
        assertThat(PoiCategory.allKinds).isEqualTo(expected)
    }

    @Test
    fun `every kind referenced by a category is one the search schema recognises`() {
        // The wire format (osmValue) must round-trip through PlaceKind.fromOsm — if it
        // doesn't, the row read from search.db would be misclassified as Other.
        PoiCategory.allKinds.forEach { kind ->
            val roundTripped = PlaceKind.fromOsm(kind.osmValue)
            assertThat(roundTripped).isEqualTo(kind)
        }
    }
}
