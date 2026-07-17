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
}
