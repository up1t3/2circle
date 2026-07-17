package com.twocircle.bike.feature.tracks.gpx

import com.twocircle.bike.feature.tracks.model.GpxDocument
import com.twocircle.bike.feature.tracks.model.GpxPoint
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Serialise a [GpxDocument] to a GPX 1.1 XML string.
 *
 * GPX 1.1 is a verbose format; we emit only the elements we care about (trk/trkseg/trkpt
 * with ele and time when present). The schema requires the `version` and `creator`
 * attributes on the root, and `xmlns` to be the GPX 1.1 namespace — strict parsers
 * (Strava, Komoot) reject GPX that omits any of these.
 *
 * Uses javax.xml DOM rather than a hand-rolled string builder because GPX has escaping
 * rules (ampersand in `<name>`, etc.) that are easy to get wrong. DOM handles it for free.
 *
 * Pure function: takes a [GpxDocument], returns a String. No I/O. Unit-testable.
 */
object GpxWriter {

    private const val NS = "http://www.topografix.com/GPX/1/1"
    private const val CREATOR = "2circle"

    fun write(doc: GpxDocument): String {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val xml: Document = builder.newDocument()
        xml.xmlStandalone = true

        val root = xml.createElementNS(NS, "gpx").apply {
            setAttribute("version", "1.1")
            setAttribute("creator", CREATOR)
            setAttribute("xmlns", NS)
        }
        xml.appendChild(root)

        val trk = xml.createElement("trk")
        root.appendChild(trk)
        doc.name?.let { name ->
            val nameEl = xml.createElement("name")
            nameEl.textContent = name
            trk.appendChild(nameEl)
        }

        val seg = xml.createElement("trkseg")
        trk.appendChild(seg)

        doc.points.forEach { p -> seg.appendChild(pointElement(xml, p)) }

        return serialize(xml)
    }

    private fun pointElement(xml: Document, p: GpxPoint): Element {
        val el = xml.createElement("trkpt")
        // lat/lon formatting: 7 decimals ≈ 1 cm. GPX schema uses decimal degrees.
        el.setAttribute("lat", formatCoord(p.lat))
        el.setAttribute("lon", formatCoord(p.lon))
        p.ele?.let { e ->
            val eleEl = xml.createElement("ele")
            eleEl.textContent = formatEle(e)
            el.appendChild(eleEl)
        }
        p.timeIso?.let { t ->
            val timeEl = xml.createElement("time")
            timeEl.textContent = t
            el.appendChild(timeEl)
        }
        return el
    }

    private fun formatCoord(v: Double): String =
        String.format(java.util.Locale.US, "%.7f", v)

    private fun formatEle(v: Double): String =
        String.format(java.util.Locale.US, "%.1f", v)

    private fun serialize(xml: Document): String {
        val tf = TransformerFactory.newInstance()
        val transformer = tf.newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        }
        val writer = StringWriter()
        transformer.transform(DOMSource(xml), StreamResult(writer))
        return writer.toString()
    }
}
