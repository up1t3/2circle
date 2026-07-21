package com.twocircle.bike.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.twocircle.bike.R

/**
 * Permission state machine.
 *
 * Encodes the Android permission lifecycle explicitly so the UI can render the right
 * affordance at each step. This matters for bicycle tracking: a rider who declined
 * once should get a rationale, but one who ticked "Don't ask again" must be sent to
 * system Settings (the system dialog will not reappear).
 */
sealed interface PermissionState {
    data object NotAsked : PermissionState
    data object ShouldShowRationale : PermissionState
    data object PermanentlyDenied : PermissionState
    data object Granted : PermissionState
}

fun Context.locationPermissionState(): PermissionState {
    val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
    return if (fine == PackageManager.PERMISSION_GRANTED) {
        PermissionState.Granted
    } else {
        // Rationality flag is activity-scoped and not directly queryable from a Context.
        // The Composable layer uses rememberLauncherForActivityResult + shouldShowRequest
        // to detect the permanent-denial case (true → rationale works, false → settings).
        PermissionState.NotAsked
    }
}

/**
 * Single-button rationale dialog. Reused for foreground and background location flows.
 *
 * [onConfirm] proceeds with the next permission step (request foreground, request
 * background, or open Settings). [onDismiss] lets the user defer.
 */
@Composable
fun PermissionRationaleDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.perm_dismiss_not_now))
            }
        },
    )
}

/**
 * Open the app's system details page so the user can grant a permanently-denied
 * permission. Used when `shouldShowRequestPermissionRationale` returns false after
 * a denial — the system dialog won't show again, so Settings is the only path.
 */
fun Activity.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
