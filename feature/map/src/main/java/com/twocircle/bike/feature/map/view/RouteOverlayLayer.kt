package com.twocircle.bike.feature.map.view

import com.twocircle.bike.domain.PlannedRouteHolder
import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.Route
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
 * Renders the planned route as a polyline on the map.
 *
 * Non-Composable — created from [BikeMap]'s onMapReady callback (a plain Kotlin hook),
 * the same lifecycle pattern as [TrackOverlayLayer]. Sets up a GeoJSON source + line
 * layer once, then subscribes to [PlannedRouteHolder.plannedRoute] on a background scope
 * and redraws the geometry on every emission.
 *
 * Visually distinct from the live track ([TrackOverlayLayer] uses green `#66BB6A`):
 * the planned route is a bolder blue `#1976D2` with round caps/joins so the two lines
 * never read as the same thing. The layer is added *below* the POI layer so markers
 * stay clickable on top of the line.
 *
 * The scope is cancelled via [stop] — callers call this from the host's
 * DisposableEffect to avoid leaking the subscription.
 */
class RouteOverlayLayer(
    private val map: MapLibreMap,
    holder: PlannedRouteHolder,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val routeFlow = holder.plannedRoute

    companion object {
        private const val SOURCE_ID = "route-overlay-source"
        private const val LAYER_ID = "route-overlay-layer"
        private const val POI_LAYER_REF = "poi-layer"
        // Bold blue, visually distinct from the live track's green.
        private const val LINE_COLOR = "#1976D2"
    }

    /** Start subscribing and rendering. Idempotent — safe to call once. */
    fun start() {
        val style = map.style ?: run {
            Timber.w("RouteOverlayLayer: map has no style yet; overlay inactive")
            return
        }
        if (style.getSource(SOURCE_ID) == null) {
            val source = GeoJsonSource(SOURCE_ID)
            style.addSource(source)
            val layer = LineLayer(LAYER_ID, SOURCE_ID).apply {
                setProperties(
                    PropertyFactory.lineColor(LINE_COLOR),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineOpacity(0.9f),
                    PropertyFactory.lineCap("round"),
                    PropertyFactory.lineJoin("round"),
                )
            }
            // Draw under the POI markers so they remain tappable above the line.
            // Depends on poi-layer being attached first; order matters in MapScreen.onMapReady.
            val poiLayer = style.getLayer(POI_LAYER_REF)
            checkNotNull(poiLayer) {
                "RouteOverlayLayer: '$POI_LAYER_REF' not found in style. Order matters in MapScreen.onMapReady (POI -> Route -> Track -> Waypoint)."
            }
            style.addLayerBelow(layer, POI_LAYER_REF)
        }
        job = routeFlow
            .onEach { route -> renderRoute(route) }
            .launchIn(scope)
    }

    /** Cancel the subscription. Does NOT remove the layer from the map. */
    fun stop() {
        job?.cancel()
        job = null
        // Clear the rendered geometry so a stale line doesn't linger after stop.
        renderRoute(null)
    }

    private fun renderRoute(route: Route?) {
        val style = map.style ?: return
        val source = style.getSource(SOURCE_ID) as? GeoJsonSource ?: return
        val geometry = route?.geometry
        if (geometry.isNullOrEmpty()) {
            source.setGeoJson("{\"type\":\"FeatureCollection\",\"features\":[]}")
            return
        }
        source.setGeoJson(buildLineStringGeoJson(geometry))
    }

    private fun buildLineStringGeoJson(points: List<Coord>): String {
        val coords = points.joinToString(",") { p -> "[${p.lon},${p.lat}]" }
        return """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{
                "type":"LineString","coordinates":[$coords]
              },"properties":{}}
            ]}
        """.trimIndent()
    }
}
