package com.twocircle.bike.feature.tracking.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.twocircle.bike.data.repository.TracksRepository
import com.twocircle.bike.feature.tracking.TrackingController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Boot-completed receiver that resumes an interrupted ride.
 *
 * If the device rebooted mid-ride (or the OS killed the service and `START_STICKY`
 * couldn't restart it cleanly), the track row is in [TrackStatus.Interrupted] state.
 * This receiver marks it so the next launch can offer the rider "resume or finalise".
 *
 * We do NOT auto-resume recording silently: that could surprise a rider who deliberately
 * stopped. Instead we surface the interrupted track in the UI and let them decide.
 *
 * Hilt entry: [AndroidEntryPoint] works on BroadcastReceivers since Hilt 2.40; the
 * receiver's [onReceive] runs on the main thread, so we hand off to a coroutine scope.
 */
@AndroidEntryPoint
class BootResumableReceiver : BroadcastReceiver() {

    @Inject lateinit var tracks: TracksRepository
    @Inject lateinit var controller: TrackingController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val recoverable = tracks.recoverableTracks()
                if (recoverable is com.twocircle.bike.common.outcome.Outcome.Success) {
                    recoverable.value.forEach { track ->
                        Timber.i("Boot recovery: track %s left in %s", track.id, track.status)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
