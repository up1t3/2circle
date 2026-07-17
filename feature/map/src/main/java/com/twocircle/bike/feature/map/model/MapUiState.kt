package com.twocircle.bike.feature.map.model

import com.twocircle.bike.common.outcome.Failure

/**
 * Map screen UI state — single source of truth for the Composable.
 *
 * [Idle] is shown when no region is downloaded yet — the map can't render without an
 * .mbtiles source, so we show a clear "download a region" prompt instead of an empty
 * surface. [Loading] covers the brief window between region resolution and style
 * construction. [Ready] is the steady state; [Error] surfaces any failure with the
 * typed [Failure] so the UI can render the right recovery action.
 */
sealed interface MapUiState {
    /** No offline region available; prompt the user to download one. */
    data object NoRegion : MapUiState

    /** Region located; building the style JSON. */
    data object Loading : MapUiState

    /** Style built and ready to render. */
    data class Ready(
        val styleJson: String,
        val initialCamera: MapCamera,
        val regionName: String,
    ) : MapUiState

    data class Error(val failure: Failure) : MapUiState
}
