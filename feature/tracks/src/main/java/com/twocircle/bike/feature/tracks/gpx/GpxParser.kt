package com.twocircle.bike.feature.tracks.gpx

import com.twocircle.bike.feature.tracks.model.GpxDocument
import com.twocircle.bike.feature.tracks.model.GpxPoint
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parse a GPX 1.1 XML string into a [GpxDocument].
 *
 * Lenient by design: many GPX files in the wild omit `<ele>`, `<time>`, or `<name>`,
 * or use slightly-off namespaces. We read by local tag name so namespace mismatches
 * don't break parsing. We accept multiple `<trk>` / `<trkseg>` elements and concatenate
 * all their points — single-track output is what our writer produces, but imports come
 * from arbitrary sources (Strava, Komoot, OSM traces).
 *
 * Failure modes:
 *  - Malformed XML → throws [GpxParseException].
 *  - No `<trkpt>` elements → throws [GpxParseException] (an empty track has no use).
 *
 * Pure: takes a String, returns a [GpxDocument]. No I/O. Unit-testable on JVM.
 */
@Singleton
class GpxParser @Inject constructor() {

    fun parse(xml: String): GpxDocument {
        val root = try {
            val builder = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // Disable external entity resolution — GPX files can come from untrusted
                // sources (e.g. shared from another rider) and XXE attacks are real.
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                isExpandEntityReferences = false
            }.newDocumentBuilder()
            builder.parse(InputSource(StringReader(xml)))
        } catch (e: Exception) {
            throw GpxParseException("Malformed XML: ${e.message}", e)
        }

        val docEl = root.documentElement
        val points = mutableListOf<GpxPoint>()

        // Read every trkpt regardless of nesting depth — covers trk>trkseg>trkpt
        // and the rare case of wpt-only files (which we drop anyway).
        val trkpts = docEl.getElementsByTagName("trkpt")
        for (i in 0 until trkpts.length) {
            val el = trkpts.item(i) as Element
            val lat = el.getAttribute("lat").toDoubleOrNull()
            val lon = el.getAttribute("lon").toDoubleOrNull()
            if (lat == null || lon == null) continue // skip malformed point
            val ele = el.firstChildByTag("ele")?.toDoubleOrNull()
            val time = el.firstChildByTag("time")?.trim()
            points += GpxPoint(lat = lat, lon = lon, ele = ele, timeIso = time)
        }
        if (points.isEmpty()) throw GpxParseException("GPX contains no <trkpt> elements")

        val name = docEl.getElementsByTagName("name").item(0)?.textContent?.trim()
            ?.takeIf { it.isNotEmpty() }

        return GpxDocument(name = name, points = points)
    }

    private fun Element.firstChildByTag(tag: String): String? {
        val nodes = getElementsByTagName(tag)
        return if (nodes.length > 0) nodes.item(0).textContent else null
    }
}

class GpxParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
