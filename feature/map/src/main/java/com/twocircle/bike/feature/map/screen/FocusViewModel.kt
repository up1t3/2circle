package com.twocircle.bike.feature.map.screen

import androidx.lifecycle.ViewModel
import com.twocircle.bike.feature.map.focus.MapFocusController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin VM to expose the singleton [MapFocusController] to Compose via hiltViewModel().
 * MapFocusController is @Singleton so all callers share the same instance — the Search
 * screen pushes focus targets, MapScreen collects them.
 */
@HiltViewModel
class FocusViewModel @Inject constructor(
    val focusController: MapFocusController,
) : ViewModel()
