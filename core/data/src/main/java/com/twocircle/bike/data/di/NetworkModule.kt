package com.twocircle.bike.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * App-wide HTTP client.
 *
 * Single shared [OkHttpClient] — OkHttp strongly recommends one instance per app to
 * reuse its connection pool and thread pool. Feature modules inject this and layer
 * their own Retrofit interfaces or direct calls on top.
 *
 * Timeouts: 15 s connect (generous for flaky tour-cell signal), 30 s read (long routing
 * responses), 10 s call (overall cap so a hung connection doesn't pin a coroutine).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()
}
