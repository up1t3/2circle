package com.twocircle.bike.feature.map.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.MainThread
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * One-shot "where am I" lookup for the my-location FAB.
 *
 * Uses FusedLocationProvider's [Priority.PRIORITY_BALANCED_POWER_ACCURACY] — good enough
 * for centre-the-map, doesn't burn battery like HIGH_ACCURACY (which the tracking service
 * owns anyway). Returns null if location is unavailable (permissions denied, GPS off,
 * no last fix and no quick fresh fix).
 *
 * Kept separate from the tracking service's polling client on purpose: this is a
 * user-tapped, foreground-only, one-shot call. The tracking pipeline has very different
 * cadence/accuracy requirements and shouldn't share state with the map's "find me" button.
 */
@Singleton
class MyLocationController @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var locationCallback: LocationCallback? = null

    /**
     * Resolve the current best location, or null. Must be called off the main thread.
     *
     * `@SuppressLint("MissingPermission")` because the caller (the map screen) checks
     * ACCESS_FINE_LOCATION via the LocationPermissionGate wrapper before reaching us —
     * by the time we get here the permission is granted. An explicit re-check would be
     * defensive but the gate is the single authority.
     */
    @SuppressLint("MissingPermission")
    suspend fun lastKnown(): LatLon? {
        if (!hasPermission()) {
            Timber.w("MyLocation: location permission not granted")
            return null
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { cont ->
            // getCurrentLocation gives us a fresh-ish fix (better than getLastLocation on
            // a cold device) without running a long-running request loop.
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc == null) cont.resume(null)
                    else cont.resume(LatLon(loc.latitude to loc.longitude))
                }
                .addOnFailureListener { e ->
                    Timber.w(e, "MyLocation: getCurrentLocation failed")
                    cont.resume(null)
                }
        }
    }

    /**
     * Start continuous location updates for active follow mode.
     * Uses [Priority.PRIORITY_HIGH_ACCURACY] (combining GPS, GLONASS, Galileo) with 2000ms interval.
     */
    @SuppressLint("MissingPermission")
    @MainThread
    fun startContinuousUpdates(onLocationChanged: (LatLon) -> Unit) {
        if (!hasPermission()) {
            Timber.w("MyLocation: location permission not granted for continuous updates")
            return
        }
        stopContinuousUpdates()

        val request = com.google.android.gms.location.LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                Timber.d("MyLocation: continuous fix lat=%.5f, lon=%.5f, acc=%.1fm", loc.latitude, loc.longitude, loc.accuracy)
                onLocationChanged(LatLon(loc.latitude to loc.longitude))
            }
        }
        locationCallback = cb

        val client = LocationServices.getFusedLocationProviderClient(context)
        client.requestLocationUpdates(request, cb, android.os.Looper.getMainLooper())
            .addOnFailureListener { e ->
                Timber.w(e, "MyLocation: failed to request continuous location updates")
            }
    }

    /**
     * Stop continuous updates to save battery when follow mode is turned off.
     */
    @SuppressLint("MissingPermission")
    @MainThread
    fun stopContinuousUpdates() {
        val cb = locationCallback ?: return
        locationCallback = null
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.removeLocationUpdates(cb)
    }

    private fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}

@JvmInline
value class LatLon(val pair: Pair<Double, Double>) {
    val lat: Double get() = pair.first
    val lon: Double get() = pair.second
}
