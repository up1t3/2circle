package com.twocircle.bike.feature.tracks.model

import com.twocircle.bike.data.db.entity.TrackPointEntity

/**
 * In-memory representation of a GPX file's content.
 *
 * Decoupled from the on-disk XML so the writer and parser can be unit-tested without
 * touching the filesystem — they fold over [GpxDocument], not files.
 *
 * GPX 1.1 schema:
 *  - Single `<trk>` containing one `<trkseg>` of `<trkpt>` (lat, lon, ele?, time?).
 *  - Optional `<name>` on the track.
 *  - `<wpt>` waypoints are NOT included in the export; only the ridden track is. The
 *    reverse is also true: an imported GPX's `<wpt>` elements are dropped here — the
 *    route-builder handles waypoints separately (Step 5).
 */
data class GpxDocument(
    val name: String?,
    val points: List<GpxPoint>,
) {
    init { require(points.isNotEmpty()) { "GPX document must contain at least one point" } }
}

data class GpxPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double?,
    /** ISO-8601 UTC instant; null when the source GPX has no timestamp. */
    val timeIso: String?,
)

/** Map persisted track points to a GPX-ready document. */
fun List<TrackPointEntity>.toGpxDocument(name: String?): GpxDocument = GpxDocument(
    name = name,
    points = map { GpxPoint(lat = it.lat, lon = it.lon, ele = it.ele, timeIso = formatIso(it.timestampMs)) },
)

private fun formatIso(epochMs: Long): String {
    // ISO-8601 UTC, no millis. javax.xml (used by the writer) accepts this form.
    val instant = java.time.Instant.ofEpochMilli(epochMs)
    return instant.toString()
}
