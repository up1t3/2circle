package com.twocircle.bike.feature.map.style

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MapColorsTest {

    @Test
    fun `asphalt family is green`() {
        assertThat(colorForSurface("asphalt")).isEqualTo(MapColors.ASPHALT)
        assertThat(colorForSurface("concrete")).isEqualTo(MapColors.ASPHALT)
        assertThat(colorForSurface("paving_stones")).isEqualTo(MapColors.ASPHALT)
        assertThat(colorForSurface("sett")).isEqualTo(MapColors.ASPHALT)
    }

    @Test
    fun `compacted family is orange`() {
        assertThat(colorForSurface("gravel")).isEqualTo(MapColors.COMPACTED)
        assertThat(colorForSurface("compacted")).isEqualTo(MapColors.COMPACTED)
    }

    @Test
    fun `sand is red`() {
        assertThat(colorForSurface("sand")).isEqualTo(MapColors.SAND)
    }

    @Test
    fun `null surface is grey`() {
        assertThat(colorForSurface(null)).isEqualTo(MapColors.UNKNOWN)
        assertThat(colorForSurface("unobtanium")).isEqualTo(MapColors.UNKNOWN)
    }

    @Test
    fun `case insensitive`() {
        assertThat(colorForSurface("ASPHALT")).isEqualTo(MapColors.ASPHALT)
        assertThat(colorForSurface("Sand")).isEqualTo(MapColors.SAND)
    }

    @Test
    fun `poi medical kinds are red`() {
        val byKind = PoiColors.byKind.toMap()
        assertThat(byKind["pharmacy"]).isEqualTo(PoiColors.MEDICAL)
        assertThat(byKind["hospital"]).isEqualTo(PoiColors.MEDICAL)
    }

    @Test
    fun `poi water kinds are blue`() {
        val byKind = PoiColors.byKind.toMap()
        assertThat(byKind["water"]).isEqualTo(PoiColors.WATER)
        assertThat(byKind["spring"]).isEqualTo(PoiColors.WATER)
    }

    @Test
    fun `poi bicycle kinds are teal`() {
        val byKind = PoiColors.byKind.toMap()
        assertThat(byKind["bicycle_service"]).isEqualTo(PoiColors.BICYCLE)
        assertThat(byKind["bicycle_rental"]).isEqualTo(PoiColors.BICYCLE)
    }

    @Test
    fun `poi lodging kinds are green`() {
        val byKind = PoiColors.byKind.toMap()
        assertThat(byKind["hotel"]).isEqualTo(PoiColors.LODGING)
        assertThat(byKind["campsite"]).isEqualTo(PoiColors.LODGING)
    }

    @Test
    fun `poi all colours are valid 7-char hex`() {
        // match-expression feeds raw strings to MapLibre; malformed hex renders black.
        val allHex = listOf(
            PoiColors.MEDICAL, PoiColors.FUEL, PoiColors.WATER, PoiColors.FOOD,
            PoiColors.SHOP, PoiColors.LODGING, PoiColors.MONEY, PoiColors.BICYCLE,
            PoiColors.SIGHT, PoiColors.DEFAULT,
        ) + PoiColors.byKind.map { it.second }
        for (hex in allHex) {
            assertThat(hex).matches("#[0-9A-Fa-f]{6}")
        }
    }

    @Test
    fun `poi default is legacy amber`() {
        // Cities/villages/"other" have no dedicated colour and must fall back to the
        // previous single-colour scheme so existing maps look unchanged.
        assertThat(PoiColors.DEFAULT).isEqualTo("#FFC107")
    }
}
