package com.twocircle.bike.feature.map.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.twocircle.bike.domain.model.WaypointId

/**
 * Holds the map's "edit a waypoint" interaction state.
 *
 * Two modes, both rendered with the same centre-screen reticle (the user moves the map
 * under a fixed pin, then confirms):
 *  - **Placing** ([placing] true, [editingId] null): a brand-new waypoint. Confirm adds it.
 *  - **Dragging** ([editingId] non-null): an existing waypoint. Confirm updates its coord.
 *
 * Why centre-screen drag instead of finger-dragging the marker: MapLibre Android (without
 * the annotation plugin) has no native marker drag, and a hand-rolled touch tracker is
 * fragile across zoom/rotate gestures. A fixed reticle + "move the map" is the pattern
 * OsmAnd, Organic Maps and Google Maps' "drop a pin" all use — robust and discoverable.
 *
 * Created via [rememberMapEditController] so the Composable owns one instance per
 * [BikeMap] lifetime; the host reads [isEditing] to show/hide the reticle + toolbar.
 */
@Stable
interface MapEditWaypointController {
    val placing: Boolean
    val editingId: WaypointId?
    val isEditing: Boolean get() = placing || editingId != null

    fun beginPlace()
    fun beginDrag(id: WaypointId)
    fun cancel()
}

@Composable
fun rememberMapEditController(): MapEditWaypointController {
    // Using a concrete implementation rather than a functional type so we can add
    // derived state later (e.g. a "pending coord" buffer) without breaking callers.
    return remember { MapEditWaypointControllerImpl() }
}

private class MapEditWaypointControllerImpl : MapEditWaypointController {
    override var placing by mutableStateOf(false)
        private set
    override var editingId by mutableStateOf<WaypointId?>(null)
        private set

    override fun beginPlace() {
        editingId = null
        placing = true
    }

    override fun beginDrag(id: WaypointId) {
        placing = false
        editingId = id
    }

    override fun cancel() {
        placing = false
        editingId = null
    }
}
