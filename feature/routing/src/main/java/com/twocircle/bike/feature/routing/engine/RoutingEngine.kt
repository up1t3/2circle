package com.twocircle.bike.feature.routing.engine

import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.common.outcome.Outcome
import com.twocircle.bike.domain.model.Coord
import com.twocircle.bike.domain.model.Route
import com.twocircle.bike.domain.model.RoutePlanId
import com.twocircle.bike.domain.model.RoutingProfile
import com.twocircle.bike.domain.model.Segment
import com.twocircle.bike.domain.model.Smoothness
import com.twocircle.bike.domain.model.Surface
import com.twocircle.bike.domain.model.Waypoint
import com.twocircle.bike.feature.routing.api.BRouterApi
import com.twocircle.bike.feature.routing.api.BRouterRequestBuilder
import com.twocircle.bike.feature.routing.brouter.BRouterFacade
import com.twocircle.bike.feature.routing.brouter.BRouterResult
import com.twocircle.bike.feature.routing.mapper.RouteMapper
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes between waypoints.
 *
 * Implementations:
 *  - [CloudRoutingEngine] — BRouter-Web (cloud fallback)
 *  - [OfflineRoutingEngine] — wraps [BRouterFacade] (embedded BRouter jar + rd5)
 *  - [SmartRoutingEngine] — picks offline first, falls back to cloud. Bound as the
 *    default [RoutingEngine] in RoutingEngineModule.
 *
 * Note: OfflineRoutingEngine doesn't import btools.* directly — that lives in the
 * separate :feature:routing-brouter module (BRouterFacade). This keeps KSP2 + Hilt
 * type resolution healthy: the btools jar is only on the classpath of the brouter
 * module, which has no @HiltAndroidApp-adjacent code that would trigger the glitch.
 */
interface RoutingEngine {
    suspend fun route(
        waypoints: List<Waypoint>,
        profile: RoutingProfile,
        planId: RoutePlanId,
    ): Outcome<Route>
}

/**
 * Cloud fallback using BRouter-Web. Used when no offline region is available.
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
            val response = api.route(lonlats = lonlats, profile = profileName)
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
 * Offline routing via the embedded BRouter jar (through [BRouterFacade]).
 *
 * Thin wrapper: maps [BRouterResult] to our domain [Route]. Doesn't touch any btools.*
 * type — that's why this class can be referenced by Hilt without tripping the KSP2
 * type-resolution glitch that affected the earlier in-module version.
 */
@Singleton
class OfflineRoutingEngine @Inject constructor(
    private val facade: BRouterFacade,
) : RoutingEngine {

    override suspend fun route(
        waypoints: List<Waypoint>,
        profile: RoutingProfile,
        planId: RoutePlanId,
    ): Outcome<Route> {
        val result = facade.computeRoute(waypoints, profile)
        return when (result) {
            is Outcome.Success -> Outcome.Success(
                result.value.toRoute(waypoints, planId, profile),
            )
            is Outcome.Failure -> result
        }
    }

    private fun BRouterResult.toRoute(
        waypoints: List<Waypoint>,
        planId: RoutePlanId,
        profile: RoutingProfile,
    ): Route {
        val segment = Segment(
            from = waypoints.first(),
            to = waypoints.last(),
            geometry = coords,
            distanceMeters = distanceMeters,
            plannedSeconds = (costMs / 1000L).coerceAtLeast(0L),
            ascentMeters = ascentMeters,
            descentMeters = ascentMeters,
            surface = Surface.Unknown,
            smoothness = Smoothness.Unknown,
        )
        return Route(
            id = planId,
            profile = profile,
            segments = listOf(segment),
            waypoints = waypoints,
        )
    }
}

/**
 * Offline-primary + cloud-fallback router. Bound as the default [RoutingEngine].
 */
@Singleton
class SmartRoutingEngine @Inject constructor(
    private val offline: OfflineRoutingEngine,
    private val cloud: CloudRoutingEngine,
) : RoutingEngine {

    override suspend fun route(
        waypoints: List<Waypoint>,
        profile: RoutingProfile,
        planId: RoutePlanId,
    ): Outcome<Route> {
        val offlineResult = offline.route(waypoints, profile, planId)
        if (offlineResult is Outcome.Success) return offlineResult
        val failure = (offlineResult as Outcome.Failure).failure
        Timber.i("Offline routing unavailable (%s); falling back to cloud", failure.javaClass.simpleName)
        return when (val cloudResult = cloud.route(waypoints, profile, planId)) {
            is Outcome.Success -> cloudResult
            is Outcome.Failure -> {
                if (cloudResult.failure is Failure.Network.Offline) offlineResult else cloudResult
            }
        }
    }
}
