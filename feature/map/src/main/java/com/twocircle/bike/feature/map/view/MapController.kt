package com.twocircle.bike.feature.map.view

import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import com.twocircle.bike.feature.map.model.MapBounds
import com.twocircle.bike.feature.map.model.MapCamera

/**
 * Thin wrapper around the MapLibreMap instance.
 *
 * The wrapper exists so the rest of the feature module never touches the SDK directly —
 * that keeps the SDK a swappable implementation detail and makes the controller's
 * methods individually unit-testable (with a fake MapLibreMap if we ever need to).
 *
 * All methods are no-ops when the underlying map is null (e.g. before the style loads
 * or after the host is destroyed). This avoids a class of lifecycle crashes where a
 * pending camera command fires after the MapView is gone.
 */
class MapController internal constructor(private var map: MapLibreMap?) {

    /** Move the camera to [camera]. Animated unless [instant]. */
    fun moveCamera(camera: MapCamera, instant: Boolean = false) {
        val m = map ?: return
        val position = CameraPosition.Builder()
            .target(LatLng(camera.lat, camera.lon))
            .zoom(camera.zoom)
            .bearing(camera.bearing)
            .tilt(camera.tilt)
            .build()
        val update = CameraUpdateFactory.newCameraPosition(position)
        if (instant) m.moveCamera(update) else m.easeCamera(update, 400)
    }

    /** Fit a bounds with padding (in px). Used for "show whole route". */
    fun fitBounds(bounds: MapBounds, paddingPx: Int = 80) {
        val m = map ?: return
        val lb = LatLngBounds.Builder()
            .include(LatLng(bounds.north, bounds.east))
            .include(LatLng(bounds.south, bounds.west))
            .build()
        m.animateCamera(CameraUpdateFactory.newLatLngBounds(lb, paddingPx))
    }

    /** Called by the host when the MapView is destroyed; drops the SDK reference. */
    internal fun detach() {
        map = null
    }

    /** Internal: wired by BikeMap once getMapAsync resolves. */
    internal fun attach(maplibreMap: MapLibreMap) {
        map = maplibreMap
    }
}
