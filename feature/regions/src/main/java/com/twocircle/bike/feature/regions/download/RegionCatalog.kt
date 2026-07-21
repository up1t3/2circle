package com.twocircle.bike.feature.regions.download

import com.twocircle.bike.common.di.ManifestUrl
import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.common.outcome.Outcome
import com.twocircle.bike.feature.regions.manifest.RegionManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the region catalog (manifest.json) from the backend.
 *
 * One GET; no Retrofit ceremony. The manifest URL is injected via Hilt (`@Named("manifest_url")`)
 * so it can differ per build type (debug → emulator alias 10.0.2.2, release → LAN IP)
 * without the feature module depending on :app's BuildConfig.
 *
 * Failures map to typed [Failure.Network]: offline → [Failure.Network.Offline],
 * non-2xx → [Failure.Network.Server]. The UI surfaces these so the rider knows whether
 * to retry or check connectivity.
 */
@Singleton
class RegionCatalog @Inject constructor(
    private val client: OkHttpClient,
    @ManifestUrl private val manifestUrl: String,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    suspend fun fetch(): Outcome<RegionManifest> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(manifestUrl).build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    Outcome.Failure(Failure.Network.Server(response.code))
                } else {
                    val body = response.body?.string()
                        ?: return@use Outcome.Failure(Failure.Network.Server(response.code))
                    val manifest = json.decodeFromString(RegionManifest.serializer(), body)
                    Outcome.Success(manifest)
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "Catalog fetch offline")
            Outcome.Failure(Failure.Network.Offline)
        } catch (e: Exception) {
            Timber.e(e, "Catalog fetch failed")
            Outcome.Failure(Failure.Unknown(e))
        }
    }
}
