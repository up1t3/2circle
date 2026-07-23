package com.twocircle.bike.feature.map.view

import com.twocircle.bike.feature.map.style.PoiColors
import com.twocircle.bike.feature.poi.model.Poi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import timber.log.Timber

/**
 * Renders POI markers on the map from a [StateFlow] of [Poi] lists.
 *
 * Non-Composable — created from [BikeMap]'s onMapReady callback (a plain Kotlin hook).
 * Sets up a GeoJSON source + a [SymbolLayer] once, then the host pushes new POI lists
 * via [setPois]; the source's GeoJSON is rebuilt and the map re-renders the markers.
 *
 * Markers are coloured circles for now (no per-category icon yet). The GeoJSON feature
 * properties carry `id`, `name`, `kind` so a later tap-to-select pass can show a callout
 * without a separate lookup — this is the same pattern MapLibre's symbol clustering uses.
 *
 * Lifecycle: created per [BikeMap] ready callback, stopped (cleared) via [stop]. The
 * GeoJSON source/layer stay on the map until the style is torn down — that's fine, an
 * empty FeatureCollection costs nothing.
 */
class PoiMarkerLayer(
    private val map: MapLibreMap,
) {

    private val _pois = MutableStateFlow<List<Poi>>(emptyList())
    val pois: StateFlow<List<Poi>> = _pois.asStateFlow()

    companion object {
        private const val SOURCE_ID = "poi-source"
        private const val LAYER_ID = "poi-layer"
    }

    /** Wire the source/layer into the active style. Idempotent. */
    fun start() {
        val style = map.style ?: run {
            Timber.w("PoiMarkerLayer: map has no style yet; layer inactive")
            return
        }
        if (style.getSource(SOURCE_ID) == null) {
            val source = GeoJsonSource(SOURCE_ID)
            style.addSource(source)
            val layer = SymbolLayer(LAYER_ID, SOURCE_ID).apply {
                setProperties(
                    // Circle marker drawn via text + a Unicode bullet. We don't ship a
                    // raster sprite (yet) — Phase 2.6 will swap this for a per-category
                    // vector icon. Until then, "● Name" reads clearly on the dark map,
                    // with colour differentiated by `kind` (see [PoiColors]).
                    PropertyFactory.textField("● {name}"),
                    PropertyFactory.textFont(arrayOf("Open Sans Semibold")),
                    PropertyFactory.textSize(11f),
                    PropertyFactory.textAnchor("bottom"),
                    PropertyFactory.textOffset(arrayOf(0f, -0.5f)),
                    PropertyFactory.textColor(kindColorExpression()),
                    PropertyFactory.textHaloColor("#000000"),
                    PropertyFactory.textHaloWidth(1.2f),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textIgnorePlacement(true),
                )
            }
            style.addLayer(layer)
        }
        // Initial paint of an empty collection so the source is never null.
        render(emptyList())
    }

    /** Push a fresh POI list; the source is rebuilt and markers re-rendered. */
    fun setPois(pois: List<Poi>) {
        _pois.value = pois
        render(pois)
    }

    /** Remove markers (empty source). Keeps the layer attached so re-show is instant. */
    fun clear() = setPois(emptyList())

    /** No-op for now — kept for API symmetry with [TrackOverlayLayer]. */
    fun stop() {
        clear()
    }

    private fun render(pois: List<Poi>) {
        val style = map.style ?: return
        val source = style.getSource(SOURCE_ID) as? GeoJsonSource ?: return
        source.setGeoJson(buildGeoJson(pois))
    }

    private fun buildGeoJson(pois: List<Poi>): String {
        if (pois.isEmpty()) {
            return "{\"type\":\"FeatureCollection\",\"features\":[]}"
        }
        val features = JSONArray()
        for (poi in pois) {
            val geom = JSONObject()
                .put("type", "Point")
                .put("coordinates", JSONArray().put(poi.lon).put(poi.lat))
            val props = JSONObject()
                .put("id", poi.id)
                .put("name", poi.name)
                .put("kind", poi.kind.osmValue)
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

    /**
     * Builds a MapLibre `match` expression that maps the per-feature `kind` property
     * (an OSM value such as `"pharmacy"`, `"atm"`) to a category colour from [PoiColors].
     * Unknown kinds fall back to [PoiColors.DEFAULT]. The expression is re-evaluated by
     * MapLibre on every source update, so colour changes cost nothing at the data layer.
     */
    private fun kindColorExpression(): Expression {
        val args = ArrayList<Expression>()
        args.add(Expression.get("kind"))
        for ((osmValue, hex) in PoiColors.byKind) {
            args.add(Expression.literal(osmValue))
            args.add(Expression.literal(hex))
        }
        args.add(Expression.literal(PoiColors.DEFAULT))
        return Expression.match(*args.toTypedArray())
    }
}
