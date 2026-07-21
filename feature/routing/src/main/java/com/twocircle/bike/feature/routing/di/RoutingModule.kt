package com.twocircle.bike.feature.routing.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.twocircle.bike.feature.routing.api.BRouterApi
import com.twocircle.bike.feature.routing.engine.CloudRoutingEngine
import com.twocircle.bike.feature.routing.engine.RoutingEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Routing DI bindings.
 *
 * Uses the app-wide [OkHttpClient] from :core:data's NetworkModule (single shared
 * connection pool — OkHttp's recommendation). Only the Retrofit adapter is wired here.
 *
 * The base URL is the public BRouter-Web instance; in production we'd override it with
 * a self-hosted URL (Step 9 backend pipeline).
 */
@Module
@InstallIn(SingletonComponent::class)
object RoutingModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideBRouterApi(client: OkHttpClient, json: Json): BRouterApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BROUTER_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(BRouterApi::class.java)
    }

    /** Public BRouter-Web instance. Override via BuildConfig for self-hosted deployments. */
    private const val BROUTER_BASE_URL = "https://brouter.de/"
}

/**
 * Binds [RoutingEngine] to [SmartRoutingEngine] — the Offline-First engine that
 * prefers offline BRouter (jar + rd5 segments) and falls back to cloud only when
 * no offline region is available.
 *
 * **Why @Binds instead of @Provides:** KSP2 + Hilt 2.56.2 cannot resolve
 * `OfflineRoutingEngine` inside a `@Provides` method body when its constructor
 * references `BRouterFacade` from a cross-module dependency with an embedded jar.
 * `@Binds` has no method body — Hilt discovers `SmartRoutingEngine`'s own
 * `@Inject constructor`, which transitively discovers `OfflineRoutingEngine`'s
 * `@Inject constructor`, which transitively discovers `BRouterFacade`'s
 * `@Inject constructor`. All three are already annotated `@Singleton @Inject`,
 * so no `@Provides` body in the routing module needs to name the jar-dependent
 * type. This sidesteps the KSP2 symbol-resolution glitch entirely.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RoutingEngineModule {
    @Binds
    @Singleton
    abstract fun bindRoutingEngine(impl: com.twocircle.bike.feature.routing.engine.SmartRoutingEngine): RoutingEngine
}
