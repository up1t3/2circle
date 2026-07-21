package com.twocircle.bike.feature.tracks.gpx

import com.google.common.truth.Truth.assertThat
import com.twocircle.bike.feature.tracks.model.GpxDocument
import com.twocircle.bike.feature.tracks.model.GpxPoint
import org.junit.Test

class GpxWriterTest {

    private fun doc(points: List<GpxPoint>, name: String? = "Test ride") =
        GpxDocument(name = name, points = points)

    @Test
    fun `output is valid XML with GPX 1_1 root`() {
        val xml = GpxWriter.write(doc(listOf(GpxPoint(50.0, 30.0, 100.0, "2024-01-01T00:00:00Z"))))
        assertThat(xml).contains("<?xml")
        assertThat(xml).contains("<gpx")
        assertThat(xml).contains("version=\"1.1\"")
        assertThat(xml).contains("http://www.topografix.com/GPX/1/1")
    }

    @Test
    fun `track name emitted when present`() {
        val xml = GpxWriter.write(doc(listOf(point()), name = "Morning loop"))
        assertThat(xml).contains("<name>Morning loop</name>")
    }

    @Test
    fun `track name omitted when null`() {
        val xml = GpxWriter.write(doc(listOf(point()), name = null))
        assertThat(xml).doesNotContain("<name>")
    }

    @Test
    fun `each trkpt has lat lon ele time`() {
        val xml = GpxWriter.write(doc(listOf(
            GpxPoint(lat = 50.1234567, lon = 30.9876543, ele = 412.5, timeIso = "2024-01-01T00:00:00Z"),
        )))
        assertThat(xml).contains("lat=\"50.1234567\"")
        assertThat(xml).contains("lon=\"30.9876543\"")
        assertThat(xml).contains("<ele>412.5</ele>")
        assertThat(xml).contains("<time>2024-01-01T00:00:00Z</time>")
    }

    @Test
    fun `ele and time omitted when null`() {
        val xml = GpxWriter.write(doc(listOf(GpxPoint(50.0, 30.0, ele = null, timeIso = null))))
        // Point element still present, but no ele/time children.
        assertThat(xml).contains("<trkpt")
        assertThat(xml).doesNotContain("<ele>")
        assertThat(xml).doesNotContain("<time>")
    }

    @Test
    fun `ampersand in name is escaped`() {
        val xml = GpxWriter.write(doc(listOf(point()), name = "Tom & Jerry ride"))
        // DOM must escape the ampersand to &amp; so the XML stays well-formed.
        assertThat(xml).contains("Tom &amp; Jerry ride")
        assertThat(xml).doesNotContain("Tom & Jerry ride")
    }

    @Test
    fun `multiple points each get their own trkpt`() {
        val pts = (0..3).map { GpxPoint(50.0 + it * 0.001, 30.0, null, null) }
        val xml = GpxWriter.write(doc(pts))
        val trkptCount = Regex("<trkpt ").findAll(xml).count()
        assertThat(trkptCount).isEqualTo(4)
    }

    @Test
    fun `round-trip through parser preserves coordinates`() {
        val original = doc(
            listOf(
                GpxPoint(50.1234567, 30.9876543, 412.5, "2024-01-01T00:00:00Z"),
                GpxPoint(50.124, 30.988, 415.0, "2024-01-01T00:00:05Z"),
            ),
            name = "Loop",
        )
        val xml = GpxWriter.write(original)
        val parsed = GpxParser().parse(xml)
        assertThat(parsed.name).isEqualTo("Loop")
        assertThat(parsed.points).hasSize(2)
        assertThat(parsed.points[0].lat).isWithin(1e-6).of(50.1234567)
        assertThat(parsed.points[0].ele).isEqualTo(412.5)
    }

    private fun point() = GpxPoint(50.0, 30.0, 100.0, "2024-01-01T00:00:00Z")
}
