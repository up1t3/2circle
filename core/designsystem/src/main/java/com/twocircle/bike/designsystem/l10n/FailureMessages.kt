package com.twocircle.bike.designsystem.l10n

import androidx.annotation.StringRes
import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.designsystem.R

/**
 * Maps a typed [Failure] to a localised, user-facing message resource.
 *
 * Replaces the previous `failure.javaClass.simpleName` rendering that leaked
 * Kotlin class names to the user. The UI calls [messageRes] for a generic
 * error detail, or picks a more specific resource itself (e.g. a dedicated
 * title) when it wants to distinguish cases.
 *
 * Keep exhaustive: when a new [Failure] subtype is added, the compiler will
 * force this `when` to cover it.
 */
@StringRes
fun Failure.messageRes(): Int = when (this) {
    is Failure.Storage.Inaccessible -> R.string.map_error_storage_inaccessible
    is Failure.Storage.OutOfDisk -> R.string.map_error_storage_inaccessible

    is Failure.Location.PermissionDenied -> R.string.map_error_generic
    is Failure.Location.ProvidersDisabled -> R.string.map_error_generic
    is Failure.Location.Timeout -> R.string.map_error_generic

    is Failure.Region.NotDownloaded -> R.string.map_error_region_not_downloaded
    is Failure.Region.Corrupted -> R.string.map_error_region_corrupted
    is Failure.Region.Outdated -> R.string.map_error_region_corrupted

    is Failure.Routing.NoPath -> R.string.route_error_no_path
    is Failure.Routing.EngineError -> R.string.route_error_generic
    is Failure.Routing.OfflineUnavailable -> R.string.route_error_region_not_downloaded

    is Failure.Network.Offline -> R.string.map_error_generic
    is Failure.Network.Server -> R.string.map_error_generic

    is Failure.Gpx.Malformed -> R.string.map_error_generic
    is Failure.Gpx.Empty -> R.string.map_error_generic
    is Failure.Gpx.Io -> R.string.map_error_storage_inaccessible

    is Failure.InvalidInput -> R.string.map_error_generic
    is Failure.Unknown -> R.string.map_error_generic
}
