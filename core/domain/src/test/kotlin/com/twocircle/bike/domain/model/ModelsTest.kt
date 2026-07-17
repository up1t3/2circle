package com.twocircle.bike.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SurfaceMappingTest {

    @Test
    fun `asphalt family maps to Asphalt`() {
        assertThat(surfaceFromOsm("asphalt")).isEqualTo(Surface.Asphalt)
        assertThat(surfaceFromOsm("concrete")).isEqualTo(Surface.Asphalt)
        assertThat(surfaceFromOsm("paving_stones")).isEqualTo(Surface.Asphalt)
        assertThat(surfaceFromOsm("sett")).isEqualTo(Surface.Asphalt)
    }

    @Test
    fun `gravel family maps to Compacted`() {
        assertThat(surfaceFromOsm("gravel")).isEqualTo(Surface.Compacted)
        assertThat(surfaceFromOsm("compacted")).isEqualTo(Surface.Compacted)
        assertThat(surfaceFromOsm("fine_gravel")).isEqualTo(Surface.Compacted)
    }

    @Test
    fun `dirt family maps to Dirt`() {
        assertThat(surfaceFromOsm("dirt")).isEqualTo(Surface.Dirt)
        assertThat(surfaceFromOsm("ground")).isEqualTo(Surface.Dirt)
        assertThat(surfaceFromOsm("earth")).isEqualTo(Surface.Dirt)
    }

    @Test
    fun `sand maps to Sand`() {
        assertThat(surfaceFromOsm("sand")).isEqualTo(Surface.Sand)
    }

    @Test
    fun `null and unknown map to Unknown`() {
        assertThat(surfaceFromOsm(null)).isEqualTo(Surface.Unknown)
        assertThat(surfaceFromOsm("unobtanium")).isEqualTo(Surface.Unknown)
    }

    @Test
    fun `surface mapping is case insensitive`() {
        assertThat(surfaceFromOsm("ASPHALT")).isEqualTo(Surface.Asphalt)
        assertThat(surfaceFromOsm("Gravel")).isEqualTo(Surface.Compacted)
    }

    @Test
    fun `smoothness mapping covers all known values`() {
        assertThat(smoothnessFromOsm("excellent")).isEqualTo(Smoothness.Excellent)
        assertThat(smoothnessFromOsm("good")).isEqualTo(Smoothness.Good)
        assertThat(smoothnessFromOsm("bad")).isEqualTo(Smoothness.Bad)
        assertThat(smoothnessFromOsm("very_bad")).isEqualTo(Smoothness.VeryBad)
        assertThat(smoothnessFromOsm("horrible")).isEqualTo(Smoothness.Horrible)
        assertThat(smoothnessFromOsm("impassable")).isEqualTo(Smoothness.Impassable)
        assertThat(smoothnessFromOsm(null)).isEqualTo(Smoothness.Unknown)
    }
}

class CoordValidationTest {

    @Test
    fun `coord accepts valid range`() {
        Coord(0.0, 0.0)
        Coord(90.0, 180.0)
        Coord(-90.0, -180.0)
    }

    @Test
    fun `coord rejects out of range`() {
        assertThrows<IllegalArgumentException> { Coord(91.0, 0.0) }
        assertThrows<IllegalArgumentException> { Coord(0.0, 181.0) }
    }
}

inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
    try {
        block()
        error("Expected ${T::class.simpleName} to be thrown")
    } catch (e: Throwable) {
        if (e !is T) error("Wrong exception: ${e::class}")
    }
}
