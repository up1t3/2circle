package com.twocircle.bike.feature.tracking.screen

import androidx.lifecycle.ViewModel
import com.twocircle.bike.feature.tracking.TrackingController
import com.twocircle.bike.feature.tracking.model.TrackingState
import com.twocircle.bike.feature.tracking.model.initialTrackingState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin view model for the tracking screen.
 *
 * Just exposes the controller's StateFlow — all real logic lives in [TrackingController],
 * which is a singleton that survives activity recreation. The VM exists only to provide
 * the Hilt-scoped binding to Compose via hiltViewModel().
 */
@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val controller: TrackingController,
) : ViewModel() {

    /** Live tracking state. Driven by the service via the controller. */
    val state: kotlinx.coroutines.flow.StateFlow<TrackingState>
        get() = controller.state
}
