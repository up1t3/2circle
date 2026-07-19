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
 * Provides [RoutingEngine].
 *
 * CURRENT: cloud-only (BRouter-Web). The offline engine (OfflineRoutingEngine wrapping
 * the BRouter jar via BRouterFacade) is fully implemented and tested on desktop, but
 * KSP2 + Hilt has a type-resolution glitch that prevents binding it in DI when its
 * constructor references the facade module. To enable offline routing once the KSP/Hilt
 * bug is fixed (or worked around), replace the body with:
 *
 *     SmartRoutingEngine(offline = offline, cloud = cloud)
 *
 * and add `offline: OfflineRoutingEngine` to the parameter list.
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
