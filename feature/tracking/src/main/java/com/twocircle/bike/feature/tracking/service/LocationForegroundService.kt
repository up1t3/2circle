package com.twocircle.bike.feature.tracking.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.twocircle.bike.feature.tracking.TrackingController
import com.twocircle.bike.feature.tracking.model.PointSample
import com.twocircle.bike.feature.tracking.sampler.PollingPolicy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service that owns the GPS subscription for an active ride.
 *
 * Why a service and not just a coroutine? Because the OS kills background coroutines
 * aggressively — a phone in a jersey pocket is exactly the scenario Doze targets. A
 * foreground service with `foregroundServiceType=location` survives, and the persistent
 * notification is what makes the "is the app still recording?" affordance trustworthy.
 *
 * Architecture: the service is a thin shell. It owns the FusedLocationProvider callback
 * and a checkpoint timer; all real logic lives in [TrackingController] (singleton, so
 * survives Service recreation). On each location callback we feed the sample to the
 * controller; on each checkpoint tick we flush the pipeline.
 *
 * Start/stop: [ACTION_START] / [ACTION_STOP] / [ACTION_PAUSE] intents drive the
 * controller's lifecycle. The Activity talks to the service via these intents, NOT via
 * a bound binder, because bound services die with the activity.
 */
@AndroidEntryPoint
class LocationForegroundService : Service() {

    @Inject lateinit var controller: TrackingController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var fusedClient: com.google.android.gms.location.FusedLocationProviderClient? = null
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null
    private var checkpointJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification(controller.state.value))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> scope.launch {
                val trackId = controller.start()
                if (trackId == null) {
                    Timber.e("Tracking start failed; stopping service")
                    stopSelf()
                    return@launch
                }
                startLocationUpdates()
                startCheckpointLoop()
            }
            ACTION_STOP -> scope.launch {
                stopLocationUpdates()
                controller.stop()
                stopSelf()
            }
            ACTION_PAUSE -> scope.launch {
                controller.pause()
                stopLocationUpdates()
            }
            ACTION_RESUME -> scope.launch {
                controller.resumeFromPause()
                startLocationUpdates()
            }
        }
        // START_STICKY: if the OS kills the service, restart it with a null intent so
        // the ride survives (the controller singleton still holds session state).
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        checkpointJob?.cancel()
        // Mark the ride Interrupted so the next launch offers recovery — the OS killed
        // us before we got a clean stop.
        scope.launch {
            val tid = controller.state.value.trackId
            if (tid != null && controller.isActive()) {
                controller.checkpoint()
            }
        }
        scope.cancel()
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.w("No location permission; cannot start updates")
            stopSelf()
            return
        }
        val client = fusedClient ?: LocationServices.getFusedLocationProviderClient(this).also {
            fusedClient = it
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, PollingPolicy.ACTIVE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(PollingPolicy.ACTIVE_INTERVAL_MS)
            .build()
        val cb = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.lastLocation?.let { loc -> onFix(loc) }
            }
        }
        locationCallback = cb
        try {
            client.requestLocationUpdates(request, cb, mainLooper)
        } catch (e: SecurityException) {
            Timber.e(e, "Permission revoked mid-session")
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        val cb = locationCallback ?: return
        fusedClient?.removeLocationUpdates(cb)
        locationCallback = null
    }

    private fun onFix(loc: Location) {
        val sample = PointSample(
            lat = loc.latitude,
            lon = loc.longitude,
            ele = if (loc.hasAltitude()) loc.altitude else null,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
            speedMps = if (loc.hasSpeed()) loc.speed else null,
            bearingDeg = if (loc.hasBearing()) loc.bearing else null,
            timestampMs = loc.time,
        )
        scope.launch { controller.onSample(sample) }
    }

    /**
     * Periodic flush — drains the in-memory pipeline and writes aggregates.
     * The interval bounds the worst-case data loss window on a crash.
     */
    private fun startCheckpointLoop() {
        checkpointJob?.cancel()
        checkpointJob = scope.launch {
            while (true) {
                delay(CHECKPOINT_INTERVAL_MS)
                runCatching { controller.checkpoint() }
                    .onFailure { Timber.e(it, "Checkpoint failed") }
                // Update the notification with fresh aggregates.
                val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                mgr.notify(NOTIFICATION_ID, buildNotification(controller.state.value))
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Ride recording",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Persistent notification while a ride is being recorded."
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(state: com.twocircle.bike.feature.tracking.model.TrackingState): Notification {
        val agg = state.aggregates
        val content = buildString {
            append(formatKm(agg.distanceMeters))
            append("  ·  ")
            append(formatMin(agg.movingSeconds))
            if (agg.ascentMeters > 0) {
                append("  ·  ↑")
                append(agg.ascentMeters.toInt())
                append("m")
            }
        }
        // PendingIntent: tapping the notification reopens the app. The host activity
        // is wired up by the :app manifest; here we just launch the launcher intent.
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        val pi = PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("2circle — recording")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun formatKm(m: Double): String =
        String.format(java.util.Locale.US, "%.1f km", m / 1000.0)

    private fun formatMin(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    companion object {
        const val CHANNEL_ID = "twocircle_tracking"
        const val NOTIFICATION_ID = 4_2_1
        const val CHECKPOINT_INTERVAL_MS = 10_000L

        const val ACTION_START = "com.twocircle.bike.tracking.START"
        const val ACTION_STOP = "com.twocircle.bike.tracking.STOP"
        const val ACTION_PAUSE = "com.twocircle.bike.tracking.PAUSE"
        const val ACTION_RESUME = "com.twocircle.bike.tracking.RESUME"

        fun start(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
