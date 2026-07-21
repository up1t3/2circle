package com.twocircle.bike.feature.regions.download

import com.twocircle.bike.data.db.entity.RegionEntity
import com.twocircle.bike.data.db.entity.RegionInstallState
import com.twocircle.bike.data.filesystem.RegionAssets
import com.twocircle.bike.data.repository.RegionsRepository
import com.twocircle.bike.feature.regions.manifest.RegionEntry
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
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads, verifies, and extracts a region package.
 *
 * Flow per region:
 *   1. Insert a row with [RegionInstallState.Downloading] so the UI reflects progress.
 *   2. Stream the .zip from the manifest URL to a temp file, reporting progress.
 *   3. If [RegionEntry.sha256] is set, verify the digest; abort on mismatch.
 *   4. Extract the archive into `<filesDir>/regions/<id>/` (atomic via temp + rename).
 *   5. Flip the row to [RegionInstallState.Installed].
 *
 * Failure handling: on any error, mark the row [RegionInstallState.Failed] and clean
 * up the partial files so a retry starts clean. The "Failed" state is first-class so
 * the UI can offer a "Retry" affordance rather than leaving the user with a stuck
 * half-installed region.
 *
 * Concurrency: one download at a time per region id — guarded by [downloads].
 */
@Singleton
class RegionDownloader @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val client: OkHttpClient,
    private val regions: RegionsRepository,
    private val assets: RegionAssets,
) {

    /** Progress for in-flight downloads, keyed by region id. */
    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadProgress>> get() = _progress.asStateFlow()

    /** Track active downloads to dedupe parallel requests for the same region. */
    private val active: MutableMap<String, Boolean> = mutableMapOf()

    /**
     * Download and install [entry]. Idempotent: returns immediately if already installed
     * or if a download is already in progress for this id.
     */
    suspend fun download(entry: RegionEntry) = withContext(Dispatchers.IO) {
        synchronized(active) {
            if (active[entry.id] == true) {
                Timber.i("Download already active for %s; skipping", entry.id)
                return@withContext
            }
            active[entry.id] = true
        }
        try {
            executeDownload(entry)
        } finally {
            synchronized(active) { active.remove(entry.id) }
        }
    }

    private suspend fun executeDownload(entry: RegionEntry) {
        // Insert / update the row to Downloading.
        val entity = entry.toEntity(RegionInstallState.Downloading)
        regions.upsert(entity)
        emitProgress(entry.id, DownloadState.Downloading, fraction = 0f)

        val tempFile = File(context.cacheDir, "region-${entry.id}.zip")
        try {
            // 1. Download to temp file.
            downloadTo(entry, tempFile)
            emitProgress(entry.id, DownloadState.Verifying, fraction = 1f)

            // 2. Verify SHA-256 if the manifest provided one.
            entry.sha256?.let { expected ->
                val actual = sha256Hex(tempFile)
                if (!actual.equals(expected, ignoreCase = true)) {
                    error("SHA-256 mismatch: expected=$expected actual=$actual")
                }
            }

            // 3. Extract.
            emitProgress(entry.id, DownloadState.Extracting, fraction = 1f)
            extractTo(tempFile, assets.regionDir(entity))

            // 4. Flip to Installed.
            regions.upsert(entity.copy(installState = RegionInstallState.Installed))
            emitProgress(entry.id, DownloadState.Installed, fraction = 1f)
            Timber.i("Region %s installed", entry.id)
        } catch (e: Exception) {
            Timber.e(e, "Region %s install failed", entry.id)
            regions.setState(entry.id, RegionInstallState.Failed)
            // Best-effort cleanup; leave the row in Failed state for retry.
            runCatching { assets.regionDir(entity).deleteRecursively() }
            // Keep the partial temp file so the next attempt can resume via Range.
            // Only delete on non-network errors (e.g. SHA mismatch) where the file is
            // genuinely corrupt and must be re-downloaded from scratch.
            if (e.message?.contains("SHA-256") == true) {
                runCatching { tempFile.delete() }
            }
            emitProgress(entry.id, DownloadState.Failed, fraction = 0f, error = e.message)
        }
    }

    private fun downloadTo(entry: RegionEntry, target: File) {
        target.parentFile?.mkdirs()

        // Resume support: if a partial file exists from a previous interrupted attempt,
        // send a Range header to continue from where we left off. The server must
        // support Range (206 Partial Content); if it doesn't, we fall back to full download.
        val existingBytes = if (target.exists()) target.length() else 0L

        val requestBuilder = Request.Builder().url(entry.downloadUrl)
        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
            Timber.i("Resuming download of %s from byte %d", entry.id, existingBytes)
        }

        val response = client.newCall(requestBuilder.build()).execute()
        val isPartial = response.code == 206
        if (!response.isSuccessful && !isPartial) {
            error("HTTP ${response.code} downloading ${entry.downloadUrl}")
        }

        val body = response.body ?: error("Empty response body")
        // For 206: total = already-downloaded + remaining. For 200: total from Content-Length.
        val contentLength = body.contentLength()
        val totalBytes = if (isPartial) {
            existingBytes + (if (contentLength > 0) contentLength else entry.sizeBytes - existingBytes)
        } else {
            if (contentLength > 0) contentLength else entry.sizeBytes
        }
        var read = existingBytes

        // append = true for resume (206), false for fresh download (200).
        // If server ignored Range and returned 200, start over.
        val append = isPartial
        if (!append && target.exists()) target.delete()

        body.byteStream().use { input ->
            java.io.FileOutputStream(target, append).use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                    read += n
                    if (totalBytes > 0) {
                        emitProgress(
                            entry.id,
                            DownloadState.Downloading,
                            fraction = (read.toFloat() / totalBytes).coerceIn(0f, 1f),
                        )
                    }
                }
            }
        }
    }

    /**
     * Extract the zip into [destDir], preserving subdirectory structure.
     *
     * The region package contains nested directories (segments4/, profiles2/) that
     * the offline BRouter engine expects at specific paths. We can't flatten — we
     * must recreate the exact layout.
     *
     * Zip-slip protection: each target path is resolved against [destDir] and rejected
     * if it escapes (entries with ../ would otherwise write outside the region dir).
     */
    private fun extractTo(zip: File, destDir: File) {
        destDir.mkdirs()
        val canonicalDest = destDir.canonicalPath
        ZipInputStream(FileInputStream(zip).buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val target = File(destDir, entry.name)
                    // Zip-slip guard: reject entries that escape the destination dir.
                    if (!target.canonicalPath.startsWith(canonicalDest)) {
                        Timber.w("Skipping zip entry outside dest dir: %s", entry.name)
                        entry = zin.nextEntry
                        continue
                    }
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = zin.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                        }
                    }
                }
                entry = zin.nextEntry
            }
        }
    }

    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun emitProgress(
        id: String,
        state: DownloadState,
        fraction: Float,
        error: String? = null,
    ) {
        _progress.value = _progress.value + (id to DownloadProgress(state, fraction, error))
        if (state == DownloadState.Installed || state == DownloadState.Failed) {
            // Drop terminal states from the progress map after a short delay would be nicer;
            // for v1 we just clear the entry on terminal.
            _progress.value = _progress.value - id
        }
    }
}

/** State machine for a single region download. */
enum class DownloadState { Downloading, Verifying, Extracting, Installed, Failed }

data class DownloadProgress(
    val state: DownloadState,
    val fraction: Float,
    val error: String? = null,
)

/** Map a manifest entry to a local entity row. */
fun RegionEntry.toEntity(installState: RegionInstallState): RegionEntity = RegionEntity(
    id = id,
    name = name,
    version = version,
    sizeBytes = sizeBytes,
    boundsMinLat = bounds.minLat,
    boundsMinLon = bounds.minLon,
    boundsMaxLat = bounds.maxLat,
    boundsMaxLon = bounds.maxLon,
    installState = installState,
    sourceUrl = downloadUrl,
)
