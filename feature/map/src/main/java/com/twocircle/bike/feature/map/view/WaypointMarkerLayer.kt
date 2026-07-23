package com.twocircle.bike.feature.map.view

import com.twocircle.bike.domain.model.Waypoint
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import timber.log.Timber

/**
 * Renders draft-route waypoints as numbered pins on the map.
 *
 * Non-Composable — created from [BikeMap]'s onMapReady callback, same lifecycle pattern
 * as [PoiMarkerLayer]. Each waypoint is a text marker ("①"/"🅐"/"●" by role) drawn above
 * the POI layer so it stays tappable. The pin currently being dragged is rendered larger
 * with a halo via a per-feature `editing` flag.
 *
 * Layer order matters: waypoints sit *above* POI (added via `addLayerAbove(..., "poi-layer")`)
 * so a waypoint placed on top of a POI is the tappable target. The planned route line
 * ([RouteOverlayLayer]) is below POI, so waypoints correctly crown the line.
 */
class WaypointMarkerLayer(
    private val map: MapLibreMap,
) {

    companion object {
        private const val SOURCE_ID = "waypoint-source"
        private const val LAYER_ID = "waypoint-layer"
        private const val POI_LAYER_REF = "poi-layer"
        // Start = blue, End = red, Via = amber — matches the route line's blue and stays
        // distinct from POI category colours.
        private const val COLOR_START = "#1976D2"
        private const val COLOR_END = "#D32F2F"
        private const val COLOR_VIA = "#FFC107"
        private const val HALO_COLOR = "#000000"
    }

    /** Wire the source/layer into the active style. Idempotent. */
    fun start() {
        val style = map.style ?: run {
            Timber.w("WaypointMarkerLayer: map has no style yet; layer inactive")
            return
        }
        if (style.getSource(SOURCE_ID) == null) {
            val source = GeoJsonSource(SOURCE_ID)
            style.addSource(source)
            val layer = SymbolLayer(LAYER_ID, SOURCE_ID).apply {
                setProperties(
                    // {symbol} and {size} are written per-feature in buildGeoJson so that
                    // role and editing state can vary without re-creating the layer.
                    PropertyFactory.textField("● {symbol}"),
                    PropertyFactory.textFont(arrayOf("Open Sans Bold")),
                    PropertyFactory.textSize(Expression.get("size")),
                    PropertyFactory.textAnchor("bottom"),
                    PropertyFactory.textOffset(arrayOf(0f, -0.4f)),
                    PropertyFactory.textColor(Expression.get("color")),
                    PropertyFactory.textHaloColor(HALO_COLOR),
                    PropertyFactory.textHaloWidth(Expression.get("halo")),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textIgnorePlacement(true),
                )
            }
            // Sit above POI markers so waypoints stay the tappable target when overlapping.
            // Depends on poi-layer being attached first; order matters in MapScreen.onMapReady.
            val poiLayer = style.getLayer(POI_LAYER_REF)
            checkNotNull(poiLayer) {
                "WaypointMarkerLayer: '$POI_LAYER_REF' not found in style. Order matters in MapScreen.onMapReady (POI -> Route -> Track -> Waypoint)."
            }
            style.addLayerAbove(layer, POI_LAYER_REF)
        }
        render(emptyList(), editingId = null)
    }

    /** Push the current waypoint list; the editing pin is emphasised. */
    fun setWaypoints(waypoints: List<Waypoint>, editingId: com.twocircle.bike.domain.model.WaypointId? = null) {
        render(waypoints, editingId)
    }

    /** Remove markers. Keeps the layer attached. */
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
            // Symbol carries role + ordinal so users see "Start / 2 / 3 / End".
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
            val props = JSONObject()
                .put("id", wp.id.value)
                .put("symbol", symbol)
                .put("color", color)
                // Editing pin is larger and gets a thicker halo for affordance.
                .put("size", if (editing) 16f else 13f)
                .put("halo", if (editing) 2.0f else 1.2f)
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
