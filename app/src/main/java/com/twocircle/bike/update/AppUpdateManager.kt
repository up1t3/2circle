package com.twocircle.bike.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.twocircle.bike.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class AppVersionInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val forceUpdate: Boolean = false,
    val releaseNotes: String = "",
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: AppVersionInfo) : UpdateState
    data class Downloading(val progressPercent: Int) : UpdateState
    data class Error(val message: String) : UpdateState
}

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    var lastVersionInfo: AppVersionInfo? = null
        private set

    private val versionUrl = "http://72.56.238.106/version.json"

    suspend fun checkForUpdates() {
        withContext(Dispatchers.IO) {
            try {
                _updateState.value = UpdateState.Checking
                val request = Request.Builder().url(versionUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _updateState.value = UpdateState.Idle
                        return@use
                    }
                    val bodyString = response.body?.string() ?: ""
                    if (bodyString.isEmpty()) {
                        _updateState.value = UpdateState.Idle
                        return@use
                    }
                    val jsonObj = org.json.JSONObject(bodyString)
                    val info = AppVersionInfo(
                        versionCode = jsonObj.optInt("versionCode", 0),
                        versionName = jsonObj.optString("versionName", ""),
                        apkUrl = jsonObj.optString("apkUrl", ""),
                        forceUpdate = jsonObj.optBoolean("forceUpdate", false),
                        releaseNotes = jsonObj.optString("releaseNotes", ""),
                    )
                    if (info.versionCode > BuildConfig.VERSION_CODE) {
                        lastVersionInfo = info
                        Timber.d("New app version available: ${info.versionName} (${info.versionCode})")
                        _updateState.value = UpdateState.Available(info)
                    } else {
                        _updateState.value = UpdateState.Idle
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Check for update failed")
                _updateState.value = UpdateState.Idle
            }
        }
    }

    suspend fun downloadAndInstallApk(info: AppVersionInfo) {
        withContext(Dispatchers.IO) {
            try {
                _updateState.value = UpdateState.Downloading(0)
                val request = Request.Builder().url(info.apkUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    _updateState.value = UpdateState.Error("HTTP ${response.code} downloading update")
                    return@withContext
                }

                val body = response.body ?: run {
                    _updateState.value = UpdateState.Error("Empty APK body")
                    return@withContext
                }

                val totalBytes = body.contentLength()
                val apkFile = File(context.getExternalFilesDir(null), "update-${info.versionCode}.apk")
                if (apkFile.exists()) apkFile.delete()

                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var downloadedBytes = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                _updateState.value = UpdateState.Downloading(progress)
                            }
                        }
                    }
                }

                _updateState.value = UpdateState.Idle
                installApk(apkFile)

            } catch (e: Exception) {
                Timber.e(e, "Failed to download update APK")
                _updateState.value = UpdateState.Error(e.localizedMessage ?: "Download failed")
            }
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    private fun installApk(file: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(intent)
    }
}
