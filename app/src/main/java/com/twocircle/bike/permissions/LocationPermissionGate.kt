package com.twocircle.bike.permissions

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.twocircle.bike.R

/**
 * Foreground location permission flow.
 *
 * Wrap any screen that needs the user's location. Handles the three-state Android
 * lifecycle (not asked → rationale → permanently denied → granted) and exposes
 * [onGranted] / [onDenied] callbacks for the parent to react.
 *
 * On Android 13+ the caller is also responsible for POST_NOTIFICATIONS — that is
 * tracked separately when the tracking service ships (Step 6).
 */
@Composable
fun LocationPermissionGate(
    onGranted: () -> Unit,
    onDenied: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var permanentlyDenied by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            onGranted()
        } else {
            // After denial, if the activity no longer wants to show rationale, the user
            // ticked "Don't ask again" — only Settings can recover this.
            val activity = context as? Activity
            val shouldRationale = activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) ?: false
            if (shouldRationale) {
                showRationale = true
            } else {
                permanentlyDenied = true
            }
            onDenied()
        }
    }

    val isGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    if (isGranted) {
        content()
    } else {
        // Trigger initial request once.
        androidx.compose.runtime.LaunchedEffect(Unit) {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    PermissionRationaleDialog(
        visible = showRationale,
        title = "Доступ к геолокации",
        message = stringResource(R.string.perm_location_rationale),
        confirmLabel = stringResource(R.string.perm_location_action),
        onConfirm = {
            showRationale = false
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        },
        onDismiss = { showRationale = false },
    )

    PermissionRationaleDialog(
        visible = permanentlyDenied,
        title = "Доступ к геолокации",
        message = stringResource(R.string.perm_location_rationale),
        confirmLabel = stringResource(R.string.perm_location_settings),
        onConfirm = {
            permanentlyDenied = false
            (context as? Activity)?.openAppSettings()
        },
        onDismiss = { permanentlyDenied = false },
    )
}
