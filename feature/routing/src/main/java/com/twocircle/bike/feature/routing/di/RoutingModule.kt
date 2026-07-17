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
 * Binds [CloudRoutingEngine] as the default [RoutingEngine] implementation.
 *
 * When the offline engine ships, we'll introduce a qualifier ([OfflineEngine]) and let
 * the caller pick based on region availability.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RoutingEngineModule {
    @Binds
    @Singleton
    abstract fun bindRoutingEngine(impl: CloudRoutingEngine): RoutingEngine
}

/** Qualifier for offline-engine injection (used once it lands). */
@Qualifier @Retention(AnnotationRetention.BINARY)
annotation class OfflineEngine
