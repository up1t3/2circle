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
 * Provides [RoutingEngine] as a [SmartRoutingEngine] that prefers offline (BRouter jar
 * + rd5 segments) and falls back to the cloud (BRouter-Web) only when no offline region
 * is available — this is the Offline-First product contract.
 */
/**
 * Provides [RoutingEngine] — cloud-only (BRouter-Web).
 *
 * OfflineRoutingEngine + SmartRoutingEngine are fully implemented and tested on desktop,
 * but KSP2 + Hilt 2.56.2 cannot resolve OfflineRoutingEngine in @Provides when its
 * constructor references BRouterFacade from a cross-module dependency with an embedded
 * jar. Tried: Hilt 2.58 (regressions on ASM transform), 2.59 (requires AGP 9.0),
 * separate module isolation, api() vs implementation() — all reproduce.
 *
 * ACTIVATION when KSP/Hilt is patched: replace body with
 *   SmartRoutingEngine(offline = offline, cloud = cloud)
 * and add `offline: OfflineRoutingEngine` parameter.
 */
@Module
@InstallIn(SingletonComponent::class)
object RoutingEngineModule {
    @Provides
    @Singleton
    fun provideRoutingEngine(cloud: CloudRoutingEngine): RoutingEngine = cloud
}

/** Qualifier for offline-engine injection (used once it lands). */
@Qualifier @Retention(AnnotationRetention.BINARY)
annotation class OfflineEngine
