package com.twocircle.bike.common.outcome

/**
 * Typed, exhaustive catalog of recoverable boundary errors.
 *
 * Sealed: the compiler enforces exhaustive `when` in UI/repositories, so no failure
 * mode can silently slip through. Each subtype carries exactly the data the UI needs
 * to offer the right recovery action — nothing more, nothing less.
 *
 * [cause] is the original throwable, kept for logging via Timber; never displayed
 * to the user directly.
 */
sealed interface Failure {

    /** Local storage (Room, files) is unreadable, locked, or out of space. */
    sealed interface Storage : Failure {
        data class Inaccessible(val cause: Throwable) : Storage
        data class OutOfDisk(val neededBytes: Long) : Storage
    }

    /** GPS / location providers unavailable. */
    sealed interface Location : Failure {
        data object PermissionDenied : Location
        data object ProvidersDisabled : Location
        data class Timeout(val cause: Throwable) : Location
    }

    /** Region package problems — search/routing/tiles depend on having one. */
    sealed interface Region : Failure {
        data object NotDownloaded : Region
        data class Corrupted(val regionId: String) : Region
        data class Outdated(val currentVersion: Int, val availableVersion: Int) : Region
    }

    /** Routing engine (BRouter offline or cloud fallback) could not compute. */
    sealed interface Routing : Failure {
        data class NoPath(val reason: String, val cause: Throwable? = null) : Routing
        data class EngineError(val cause: Throwable) : Routing
        data object OfflineUnavailable : Routing
    }

    /** Network / cloud routing fallback. */
    sealed interface Network : Failure {
        data object Offline : Network
        data class Server(val code: Int, val cause: Throwable? = null) : Network
    }

    /** GPX import / export malformed or unsupported. */
    sealed interface Gpx : Failure {
        data class Malformed(val detail: String, val cause: Throwable? = null) : Gpx
        data object Empty : Gpx
        data class Io(val cause: Throwable) : Gpx
    }

    /** Input failed validation at a trust boundary. */
    data class InvalidInput(val field: String, val reason: String) : Failure

    /** Catch-all for unexpected failures — surface honestly, never swallow. */
    data class Unknown(val cause: Throwable) : Failure
}
