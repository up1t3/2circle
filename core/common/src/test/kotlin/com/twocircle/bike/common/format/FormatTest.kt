package com.twocircle.bike.common.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration

class FormatTest {

    @Test
    fun `distance under 1km renders in metres`() {
        assertThat(Format.distance(0.5)).isEqualTo("0 m")
        assertThat(Format.distance(840.0)).isEqualTo("840 m")
    }

    @Test
    fun `distance over 1km renders in km with one decimal`() {
        assertThat(Format.distance(12_345.0)).isEqualTo("12.3 km")
    }

    @Test
    fun `speed converts mps to kmh`() {
        assertThat(Format.speed(5.0)).isEqualTo("18.0 km/h")
        assertThat(Format.speed(0.0)).isEqualTo("0.0 km/h")
    }

    @Test
    fun `duration seconds only`() {
        assertThat(Format.duration(Duration.ofSeconds(45))).isEqualTo("45s")
    }

    @Test
    fun `duration minutes only`() {
        assertThat(Format.duration(Duration.ofMinutes(23))).isEqualTo("23m")
    }

    @Test
    fun `duration hours and minutes`() {
        assertThat(Format.duration(Duration.ofMinutes(83))).isEqualTo("1h 23m")
    }

    @Test
    fun `duration hours only`() {
        assertThat(Format.duration(Duration.ofHours(3))).isEqualTo("3h")
    }

    @Test
    fun `elevation positive uses plus sign`() {
        assertThat(Format.elevation(348.6)).isEqualTo("+349 m")
    }

    @Test
    fun `elevation negative uses unicode minus`() {
        assertThat(Format.elevation(-112.0)).isEqualTo("−112 m")
    }

    @Test
    fun `gradient signed`() {
        assertThat(Format.gradient(7.4)).isEqualTo("+7.4 %")
        assertThat(Format.gradient(-7.4)).isEqualTo("−7.4 %")
    }

    @Test
    fun `file size units`() {
        assertThat(Format.fileSize(512L)).isEqualTo("512 B")
        assertThat(Format.fileSize(2048L)).isEqualTo("2 KB")
        assertThat(Format.fileSize(12L * 1024 * 1024)).isEqualTo("12.0 MB")
        assertThat(Format.fileSize((1.4 * 1024 * 1024 * 1024).toLong())).isEqualTo("1.4 GB")
    }
}
