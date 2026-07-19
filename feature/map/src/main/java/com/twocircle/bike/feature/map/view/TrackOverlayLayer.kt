package com.twocircle.bike.feature.map.view

import com.twocircle.bike.domain.TrackOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import timber.log.Timber

/**
 * Attaches a live-track overlay to the given MapLibre map.
 *
 * Non-Composable — called from [BikeMap]'s onMapReady lambda (which is a plain Kotlin
 * callback, not a Composable scope). Sets up a GeoJSON source + line layer once, then
 * subscribes to [overlay]'s polyline flow on a background scope and updates the source
 * geometry on every emission.
 *
 * The scope is cancelled via [stop] — callers should call this from the host's
 * DisposableEffect to avoid leaking the subscription.
 */
class TrackOverlayLayer(
    private val map: MapLibreMap,
    private val overlay: TrackOverlay,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    companion object {
        private const val SOURCE_ID = "track-overlay-source"
        private const val LAYER_ID = "track-overlay-layer"
    }

    /** Start subscribing and rendering. Idempotent — safe to call once. */
    fun start() {
        val style = map.style ?: run {
            Timber.w("TrackOverlayLayer: map has no style yet; overlay inactive")
            return
        }
        if (style.getSource(SOURCE_ID) == null) {
            val source = GeoJsonSource(SOURCE_ID)
            style.addSource(source)
            val layer = LineLayer(LAYER_ID, SOURCE_ID).apply {
                setProperties(
                    PropertyFactory.lineColor("#66BB6A"),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineOpacity(0.85f),
                )
            }
            style.addLayerAbove(layer, "road")
        }
        job = overlay.polyline
            .onEach { points -> renderPoints(points) }
            .launchIn(scope)
    }

    /** Cancel the subscription. Does NOT remove the layer from the map. */
    fun stop() {
        job?.cancel()
        job = null
    }

    private fun renderPoints(points: List<com.twocircle.bike.domain.model.Coord>) {
        val style = map.style ?: return
        val source = style.getSource(SOURCE_ID) as? GeoJsonSource ?: return
        if (points.isEmpty()) {
            source.setGeoJson("{\"type\":\"FeatureCollection\",\"features\":[]}")
            return
        }
        val coords = points.joinToString(",") { p -> "[${p.lon},${p.lat}]" }
        val geojson = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{
                "type":"LineString","coordinates":[$coords]
              },"properties":{}}
            ]}
        """.trimIndent()
        source.setGeoJson(geojson)
    }
}
