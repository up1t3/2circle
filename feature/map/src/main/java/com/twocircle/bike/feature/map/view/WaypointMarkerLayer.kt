package com.twocircle.bike.feature.map.view

import com.twocircle.bike.domain.model.Waypoint
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import timber.log.Timber

/**
 * Renders draft-route waypoints as **visible filled-circle pins** with a numeric/role label.
 *
 * Why a [CircleLayer] (not a text "●" symbol like before): a 13px text bullet is nearly
 * invisible against a busy vector map and gets lost under POI labels. A filled circle with
 * a white stroke is a real, recognisable pin that reads at any zoom. The role label
 * (S / F / ordinal) rides in a sibling [SymbolLayer] stacked on top of the circle so the
 * number stays legible inside the pin.
 *
 * Layer order (bottom → top): road → route → track → poi → **waypoint-circle → waypoint-label**.
 * Both waypoint layers attach above `poi-layer` via `addLayerAbove`.
 *
 * The pin currently being edited (dragged) is rendered larger via a per-feature `editing`
 * flag so the user can see which point they're moving.
 */
class WaypointMarkerLayer(
    private val map: MapLibreMap,
) {

    companion object {
        private const val SOURCE_ID = "waypoint-source"
        private const val CIRCLE_LAYER_ID = "waypoint-layer"
        private const val LABEL_LAYER_ID = "waypoint-label-layer"
        private const val POI_LAYER_REF = "poi-layer"
        // Start = blue, End = red, Via = amber — distinct from POI category colours and
        // matching the planned route line.
        private const val COLOR_START = "#1976D2"
        private const val COLOR_END = "#D32F2F"
        private const val COLOR_VIA = "#FFC107"
        private const val STROKE_COLOR = "#FFFFFF"
    }

    /** Wire the source + circle/label layers into the active style. Idempotent. */
    fun start() {
        val style = map.style ?: run {
            Timber.w("WaypointMarkerLayer: map has no style yet; layer inactive")
            return
        }
        if (style.getSource(SOURCE_ID) == null) {
            val source = GeoJsonSource(SOURCE_ID)
            style.addSource(source)

            // Fail loud if POI isn't attached yet — z-order depends on it.
            val poiLayer = style.getLayer(POI_LAYER_REF)
            checkNotNull(poiLayer) {
                "WaypointMarkerLayer: '$POI_LAYER_REF' not found. Order matters in " +
                    "MapScreen.onMapReady (POI -> Route -> Track -> Waypoint)."
            }

            // Filled circle pin — colour and radius come from per-feature properties so
            // role (start/end/via) and editing state can vary without rebuilding the layer.
            val circleLayer = CircleLayer(CIRCLE_LAYER_ID, SOURCE_ID).apply {
                setProperties(
                    PropertyFactory.circleColor(Expression.get("color")),
                    PropertyFactory.circleRadius(Expression.get("radius")),
                    PropertyFactory.circleStrokeColor(STROKE_COLOR),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleOpacity(0.95f),
                    PropertyFactory.circleStrokeOpacity(1f),
                    // Keep pins on top of overlapping geometry but let labels breathe.
                    PropertyFactory.circlePitchAlignment("map"),
                )
            }
            style.addLayerAbove(circleLayer, POI_LAYER_REF)

            // Role label centred inside the pin (S / F / 1, 2, 3, …).
            val labelLayer = SymbolLayer(LABEL_LAYER_ID, SOURCE_ID).apply {
                setProperties(
                    PropertyFactory.textField(Expression.get("symbol")),
                    PropertyFactory.textFont(arrayOf("Open Sans Bold")),
                    PropertyFactory.textSize(11f),
                    PropertyFactory.textColor("#FFFFFF"),
                    PropertyFactory.textHaloColor("#000000"),
                    PropertyFactory.textHaloWidth(0.5f),
                    PropertyFactory.textAnchor("center"),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textIgnorePlacement(true),
                )
            }
            // Label sits directly above its circle pin.
            style.addLayerAbove(labelLayer, CIRCLE_LAYER_ID)
        }
        render(emptyList(), editingId = null)
    }

    /** Push the current waypoint list; the editing pin is emphasised. */
    fun setWaypoints(waypoints: List<Waypoint>, editingId: com.twocircle.bike.domain.model.WaypointId? = null) {
        render(waypoints, editingId)
    }

    /** Remove markers. Keeps the layers attached. */
    fun clear() = setWaypoints(emptyList())

    /** No-op for symmetry with [TrackOverlayLayer]. */
    fun stop() {
        clear()
    }

    private fun render(waypoints: List<Waypoint>, editingId: com.twocircle.bike.domain.model.WaypointId?) {
        val style = map.style ?: return
        val source = style.getSource(SOURCE_ID) as? GeoJsonSource ?: return
        source.setGeoJson(buildGeoJson(waypoints, editingId))
    }

    private fun buildGeoJson(
        waypoints: List<Waypoint>,
        editingId: com.twocircle.bike.domain.model.WaypointId?,
    ): String {
        if (waypoints.isEmpty()) {
            return "{\"type\":\"FeatureCollection\",\"features\":[]}"
        }
        val features = JSONArray()
        for ((index, wp) in waypoints.withIndex()) {
            val editing = wp.id == editingId
            val geom = JSONObject()
                .put("type", "Point")
                .put("coordinates", JSONArray().put(wp.coord.lon).put(wp.coord.lat))
            // Role label: S = start, F = finish, otherwise the ordinal (1-based for vias).
            val symbol = when (wp.role) {
                Waypoint.Role.Start -> "S"
                Waypoint.Role.End -> "F"
                Waypoint.Role.Via -> (index).toString()
            }
            val color = when (wp.role) {
                Waypoint.Role.Start -> COLOR_START
                Waypoint.Role.End -> COLOR_END
                Waypoint.Role.Via -> COLOR_VIA
            }
            // Pin radius: 9px normally, 12px while being dragged (clearer affordance).
            val radius = if (editing) 12f else 9f
            val props = JSONObject()
                .put("id", wp.id.value)
                .put("symbol", symbol)
                .put("color", color)
                .put("radius", radius)
            val feature = JSONObject()
                .put("type", "Feature")
                .put("geometry", geom)
                .put("properties", props)
            features.put(feature)
        }
        return JSONObject()
            .put("type", "FeatureCollection")
            .put("features", features)
            .toString()
    }
}
