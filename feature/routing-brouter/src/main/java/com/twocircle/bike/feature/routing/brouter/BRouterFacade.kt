package com.twocircle.bike.feature.routing.brouter

import btools.router.OsmNodeNamed
import btools.router.OsmPathElement
import btools.router.OsmTrack
import btools.router.RoutingContext
import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.common.outcome.Outcome
import com.twocircle.bike.data.filesystem.RegionAssets
import com.twocircle.bike.data.repository.RegionsRepository
import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.RoutePlanId
import com.twocircle.bike.domain.model.RoutingProfile
import com.twocircle.bike.domain.model.Segment
import com.twocircle.bike.domain.model.Smoothness
import com.twocircle.bike.domain.model.Surface
import com.twocircle.bike.domain.model.Waypoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade over the embedded BRouter jar.
 *
 * Isolated in its own Gradle module so the btools.* classes (and the brouter jar on
 * the compile classpath) never enter KSP analysis in the consuming module — this
 * sidesteps a known KSP2 + Hilt type-resolution glitch when @Inject parameters
 * reference classes loaded from a third-party jar.
 *
 * Consumers inject this single class; they see only 2circle domain types (no
 * `btools.router.*` leaks). Internally we map domain [Waypoint]s to [OsmNodeNamed],
 * invoke [btools.router.RoutingEngine], and translate the resulting [OsmTrack] back
 * to a domain [com.twocircle.bike.domain.model.Route].
 *
 * The returned [BRouterResult] carries the geometry + aggregates. The caller
 * (OfflineRoutingEngine in :feature:routing) wraps it in our domain Route shape.
 */
@Singleton
class BRouterFacade @Inject constructor(
    private val regions: RegionsRepository,
    private val assets: RegionAssets,
) {

    /**
     * Compute an offline route through [waypoints] using the active region's rd5 data.
     *
     * @return [BRouterResult] on success, or a typed [Failure] (typically
     *  [Failure.Routing.OfflineUnavailable] when no region / segments are installed,
     *  or [Failure.Routing.NoPath] when BRouter couldn't connect the waypoints).
     */
    suspend fun computeRoute(
        waypoints: List<Waypoint>,
        profile: RoutingProfile,
    ): Outcome<BRouterResult> = withContext(Dispatchers.Default) {
        if (waypoints.size < 2) {
            return@withContext Outcome.Failure(Failure.InvalidInput("waypoints", "need ≥ 2"))
        }
        val region = regions.firstInstalledOrNull()
            ?: return@withContext Outcome.Failure(Failure.Routing.OfflineUnavailable)
        val segmentsDir = File(assets.regionDir(region), "segments4")
        if (!segmentsDir.isDirectory || segmentsDir.list().isNullOrEmpty()) {
            Timber.w("BRouterFacade: no segments4/ under region %s", region.id)
            return@withContext Outcome.Failure(Failure.Routing.OfflineUnavailable)
        }

        val profileName = profile.brouterProfileName()
        val osmWaypoints = waypoints.mapIndexed { i, wp -> wp.toOsmNodeNamed(i) }
        val rc = RoutingContext().apply { localFunction = profileName }

        val engine = btools.router.RoutingEngine(
            null, null, segmentsDir, osmWaypoints, rc,
            btools.router.RoutingEngine.BROUTER_ENGINEMODE_ROUTING,
        )
        try {
            engine.doRun(TIMEOUT_MS)
        } catch (e: Throwable) {
            Timber.e(e, "BRouter engine threw")
            return@withContext Outcome.Failure(Failure.Routing.EngineError(e))
        }
        val err = engine.errorMessage
        if (!err.isNullOrEmpty()) {
            Timber.w("BRouter error: %s", err)
            return@withContext Outcome.Failure(Failure.Routing.NoPath(err))
        }
        val track = engine.foundTrack
            ?: return@withContext Outcome.Failure(Failure.Routing.NoPath("no track produced"))

        Outcome.Success(track.toResult())
    }

    companion object {
        private const val TIMEOUT_MS = 60_000L
    }
}

/**
 * Domain-clean result of an offline routing call.
 *
 * Contains only 2circle types — no BRouter leakage. The consuming OfflineRoutingEngine
 * wraps this in a full [com.twocircle.bike.domain.model.Route].
 */
data class BRouterResult(
    val coords: List<Coord>,
    val distanceMeters: Double,
    /** BRouter travel-time cost in milliseconds (profile-specific physics model). */
    val costMs: Long,
    val ascentMeters: Double,
)

// ─── internal mapping helpers (btools.* ↔ domain) ─────────────────────────────

private fun OsmTrack.toResult(): BRouterResult {
    val pathNodes = nodes ?: emptyList()
    val coords = ArrayList<Coord>(pathNodes.size + 2)
    pathNodes.forEach { el ->
        coords += Coord(
            lat = el.iLatToDouble(),
            lon = el.iLonToDouble(),
            ele = el.elev.takeIf { !it.isNaN() },
        )
    }
    return BRouterResult(
        coords = coords,
        distanceMeters = distance.toDouble(),
        costMs = cost.toLong(),
        ascentMeters = ascend.toDouble(),
    )
}

private fun Waypoint.toOsmNodeNamed(index: Int): OsmNodeNamed = OsmNodeNamed().apply {
    ilon = ((coord.lon + 180.0) * 1_000_000.0 + 0.5).toInt()
    ilat = ((coord.lat + 90.0) * 1_000_000.0 + 0.5).toInt()
    name = when (role) {
        Waypoint.Role.Start -> "from"
        Waypoint.Role.End -> "to"
        Waypoint.Role.Via -> "via$index"
    }
}

private fun OsmPathElement.iLatToDouble(): Double = getILat() / 1_000_000.0 - 90.0
private fun OsmPathElement.iLonToDouble(): Double = getILon() / 1_000_000.0 - 180.0

/** Map our RoutingProfile enum to the BRouter profile filename (without .brf). */
private fun RoutingProfile.brouterProfileName(): String = when (this) {
    RoutingProfile.Touring -> "trekking"
    RoutingProfile.Road -> "fastbike"
    RoutingProfile.Mtb -> "mundo"
}
