package com.twocircle.bike.feature.tracks.gpx

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.twocircle.bike.feature.tracks.model.GpxDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a [GpxDocument] to a file under the app's FileProvider-shared directory and
 * returns a shareable [Uri].
 *
 * Why a file and not a content stream? Because most consumers (Strava import, email,
 * AirDrop-style sharing) want a URI they can hand to ACTION_SEND, and FileProvider is
 * the supported mechanism for that on modern Android. Scoped storage means we must
 * write into our own files-dir subdirectory and expose it via the provider.
 *
 * Filenames are timestamped so multiple exports don't collide; the FileProvider
 * authority is `<applicationId>.fileprovider` (configured in the :app manifest).
 */
@Singleton
class GpxExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Directory under filesDir that FileProvider exposes (see file_provider_paths.xml). */
    private val exportDir: File get() = File(context.filesDir, "exports").apply { mkdirs() }

    /**
     * @param doc the GPX document to write.
     * @param nameHint optional display name; if null, a timestamped default is used.
     * @return the shareable [Uri], or null on I/O failure.
     */
    fun export(doc: GpxDocument, nameHint: String? = null): Uri? {
        val safeName = sanitizeFileName(nameHint ?: defaultName())
        val file = File(exportDir, "$safeName.gpx")
        return try {
            file.writeText(GpxWriter.write(doc), Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: IOException) {
            Timber.e(e, "GPX export failed")
            null
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "FileProvider authority mismatch")
            null
        }
    }

    private fun defaultName(): String =
        "2circle_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"

    /** Strip anything that isn't safe in a filename across common filesystems. */
    private fun sanitizeFileName(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9._-]"), "_").take(MAX_NAME_LEN).ifEmpty { "track" }

    companion object {
        private const val MAX_NAME_LEN = 60
    }
}
