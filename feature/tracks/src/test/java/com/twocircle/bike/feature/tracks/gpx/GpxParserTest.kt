package com.twocircle.bike.feature.tracks.gpx

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GpxParserTest {

    private val parser = GpxParser()

    @Test
    fun `parses minimal GPX with one trkpt`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg>
                <trkpt lat="50.0" lon="30.0"><ele>412.5</ele><time>2024-01-01T00:00:00Z</time></trkpt>
              </trkseg></trk>
            </gpx>
        """.trimIndent()
        val doc = parser.parse(xml)
        assertThat(doc.points).hasSize(1)
        assertThat(doc.points[0].lat).isEqualTo(50.0)
        assertThat(doc.points[0].lon).isEqualTo(30.0)
        assertThat(doc.points[0].ele).isEqualTo(412.5)
        assertThat(doc.points[0].timeIso).isEqualTo("2024-01-01T00:00:00Z")
    }

    @Test
    fun `parses points without ele or time`() {
        val xml = """
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg>
                <trkpt lat="50.0" lon="30.0"/>
              </trkseg></trk>
            </gpx>
        """.trimIndent()
        val doc = parser.parse(xml)
        assertThat(doc.points[0].ele).isNull()
        assertThat(doc.points[0].timeIso).isNull()
    }

    @Test
    fun `concatenates points from multiple trkseg`() {
        val xml = """
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <trk>
                <trkseg><trkpt lat="1.0" lon="1.0"/></trkseg>
                <trkseg><trkpt lat="2.0" lon="2.0"/></trkseg>
              </trk>
            </gpx>
        """.trimIndent()
        val doc = parser.parse(xml)
        assertThat(doc.points).hasSize(2)
    }

    @Test
    fun `reads track name when present`() {
        val xml = """
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><name>Big loop</name><trkseg><trkpt lat="1.0" lon="1.0"/></trkseg></trk>
            </gpx>
        """.trimIndent()
        assertThat(parser.parse(xml).name).isEqualTo("Big loop")
    }

    @Test
    fun `name is null when not present`() {
        val xml = """
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg><trkpt lat="1.0" lon="1.0"/></trkseg></trk>
            </gpx>
        """.trimIndent()
        assertThat(parser.parse(xml).name).isNull()
    }

    @Test(expected = GpxParseException::class)
    fun `malformed XML throws`() {
        parser.parse("<gpx><trk><not closed>")
    }

    @Test(expected = GpxParseException::class)
    fun `no trkpt throws`() {
        parser.parse(
            """
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg></trkseg></trk>
            </gpx>
            """.trimIndent(),
        )
    }

    @Test
    fun `wpt elements are ignored`() {
        // We only consume trkpt; waypoints from imports belong to the route builder.
        val xml = """
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <wpt lat="10.0" lon="10.0"/>
              <trk><trkseg><trkpt lat="50.0" lon="30.0"/></trkseg></trk>
            </gpx>
        """.trimIndent()
        val doc = parser.parse(xml)
        assertThat(doc.points).hasSize(1)
        assertThat(doc.points[0].lat).isEqualTo(50.0)
    }

    @Test
    fun `malformed trkpt skipped not fatal`() {
        // Missing lat/lon — should skip, not crash.
        val xml = """
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg>
                <trkpt lat="1.0" lon="1.0"/>
                <trkpt lat="bad" lon="data"/>
                <trkpt lat="2.0" lon="2.0"/>
              </trkseg></trk>
            </gpx>
        """.trimIndent()
        val doc = parser.parse(xml)
        assertThat(doc.points).hasSize(2)
    }

    @Test(expected = GpxParseException::class)
    fun `doctype declaration rejected for XXE safety`() {
        // A malicious GPX could try to load an external entity via DOCTYPE.
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE gpx [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <gpx version="1.1"><trk><trkseg><trkpt lat="1.0" lon="1.0"/></trkseg></trk></gpx>
        """.trimIndent()
        parser.parse(xml)
    }
}
