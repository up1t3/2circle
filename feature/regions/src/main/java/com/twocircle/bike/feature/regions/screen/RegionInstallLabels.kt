package com.twocircle.bike.feature.regions.screen

import androidx.annotation.StringRes
import com.twocircle.bike.data.db.entity.RegionInstallState
import com.twocircle.bike.designsystem.R

/**
 * Localised label for [RegionInstallState] in the regions list.
 *
 * RegionInstallState lives in :core:data (which :core:designsystem can't depend on
 * without creating a cycle), so the mapping lives here next to the screen that uses it.
 */
@StringRes
fun RegionInstallState.displayNameRes(): Int = when (this) {
    RegionInstallState.Installed -> R.string.region_install_state_installed
    RegionInstallState.NotInstalled -> R.string.region_install_state_not_installed
    RegionInstallState.Failed -> R.string.region_install_state_failed
    RegionInstallState.Downloading -> R.string.region_install_state_downloading
    RegionInstallState.Verifying -> R.string.region_install_state_verifying
    RegionInstallState.Extracting -> R.string.region_install_state_extracting
}
