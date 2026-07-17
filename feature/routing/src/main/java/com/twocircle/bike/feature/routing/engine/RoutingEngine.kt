package com.twocircle.bike.feature.routing.engine

import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.common.outcome.Outcome
import com.twocircle.bike.domain.model.Route
import com.twocircle.bike.domain.model.RoutePlanId
import com.twocircle.bike.domain.model.RoutingProfile
import com.twocircle.bike.domain.model.Waypoint
import com.twocircle.bike.feature.routing.api.BRouterApi
import com.twocircle.bike.feature.routing.api.BRouterRequestBuilder
import com.twocircle.bike.feature.routing.mapper.RouteMapper
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes between waypoints.
 *
 * Sealed by transport: [CloudRoutingEngine] talks to a BRouter-Web instance (the v1
 * primary path until the offline engine ships); [OfflineRoutingEngine] is a stub that
 * will wrap the bundled BRouter jar + rd5 segments once Step 9's pipeline produces them.
 *
 * The sealed hierarchy keeps the call site uniform — callers ask for a route and let
 * the engine pick the transport. Failures are typed via [Outcome.Failure] so the UI can
 * render specific recovery actions (download region, retry, switch profile…).
 */
sealed interface RoutingEngine {
    suspend fun route(
        waypoints: List<Waypoint>,
        profile: RoutingProfile,
        planId: RoutePlanId,
    ): Outcome<Route>
}

/**
 * Cloud fallback using BRouter-Web.
 *
 * Used as the v1 primary routing path. Requires network; on failure the caller surfaces
 * a typed [Failure.Network] or [Failure.Routing] error. When the offline engine lands,
 * the caller first tries offline and only falls back here if no region covers the area.
 */
@Singleton
class CloudRoutingEngine @Inject constructor(
    private val api: BRouterApi,
) : RoutingEngine {

    override suspend fun route(
        waypoints: List<Waypoint>,
        profile: RoutingProfile,
        planId: RoutePlanId,
    ): Outcome<Route> {
        if (waypoints.size < 2) {
            return Outcome.Failure(Failure.InvalidInput("waypoints", "need ≥ 2"))
        }
        val lonlats = BRouterRequestBuilder.buildLonLats(waypoints)
        val profileName = BRouterRequestBuilder.profileName(profile)
        return try {
            val response = api.route(
                lonlats = lonlats,
                profile = profileName,
            )
            RouteMapper.map(response, waypoints, planId, profile)
                .fold(
                    onSuccess = { Outcome.Success(it) },
                    onFailure = { e ->
                        Timber.e(e, "Route mapping failed")
                        Outcome.Failure(Failure.Routing.EngineError(e))
                    },
                )
        } catch (e: IOException) {
            Outcome.Failure(Failure.Network.Offline)
        } catch (e: HttpException) {
            // Error body logged for diagnosis but not surfaced — could leak server internals.
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            Timber.w(e, "BRouter HTTP %d: %s", e.code(), body?.take(200))
            Outcome.Failure(Failure.Network.Server(e.code()))
        } catch (e: Exception) {
            Timber.e(e, "Unexpected routing failure")
            Outcome.Failure(Failure.Unknown(e))
        }
    }
}

/**
 * Placeholder offline engine. Returns a typed failure so the caller knows to fall back
 * to cloud routing. Implementation lands with the bundled BRouter jar in a later step.
 */
@Singleton
class OfflineRoutingEngine @Inject constructor() : RoutingEngine {
    override suspend fun route(
        waypoints: List<Waypoint>,
        profile: RoutingProfile,
        planId: RoutePlanId,
    ): Outcome<Route> = Outcome.Failure(Failure.Routing.OfflineUnavailable)
}
