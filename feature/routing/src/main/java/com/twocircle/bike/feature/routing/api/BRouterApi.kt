package com.twocircle.bike.feature.routing.api

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the public BRouter-Web instance.
 *
 * Endpoint: `GET https://brouter.de/brouter?lonlats=...&profile=...&format=geojson`
 *
 * Notes:
 *  - `lonlats` is a pipe-separated list of `lon,lat` pairs (note lon-first).
 *  - The response is a GeoJSON FeatureCollection (see [BRouterResponse]).
 *  - This is a free public service; for production we'd self-host an instance
 *    (see design doc backend pipeline Step 9). The base URL is configurable so we can
 *    swap to a self-hosted URL without code changes.
 *
 * Used ONLY as a cloud fallback when no offline region is downloaded. The offline
 * engine is the primary path (Offline-First contract).
 */
interface BRouterApi {

    @GET("brouter")
    suspend fun route(
        @Query("lonlats") lonlats: String,
        @Query("profile") profile: String,
        @Query("alternativeidx") alternativeIndex: Int = 0,
        @Query("format") format: String = "geojson",
    ): BRouterResponse
}
