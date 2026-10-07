package com.tariffia.panel.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Outcome of launching the Android package installer. */
sealed interface InstallOutcome {
    /** The system installer UI was launched; the user still confirms the install. */
    data object Launched : InstallOutcome

    /** The app is not allowed to install packages yet (unknown sources disabled). */
    data object PermissionRequired : InstallOutcome

    data class Failed(val message: String) : InstallOutcome
}

/**
 * Downloads an update APK into the app cache and launches the system package installer.
 *
 * The install is handed to Android's own installer via an `ACTION_VIEW` intent on a
 * FileProvider `content://` URI with the `application/vnd.android.package-archive` type.
 * That is the mechanism that keeps working on modern Android (including Android 15): the
 * system owns the install UI and the user confirmation. We only need
 * `REQUEST_INSTALL_PACKAGES` and the user's "install unknown apps" consent for this app.
 *
 * The APK is never executed in-process; the OS verifies the signature and installs it.
 */
class UpdateInstaller(
    private val client: OkHttpClient = defaultClient,
) {

    /**
     * Downloads [apkUrl] into `cacheDir/updates/<name>` and returns the file, or null on any
     * failure (offline, non-2xx, empty body). Never throws.
     */
    suspend fun download(context: Context, apkUrl: String, fileName: String): File? =
        withContext(Dispatchers.IO) {
            if (!apkUrl.startsWith("https://")) return@withContext null
            val safeName = fileName.substringAfterLast('/').ifBlank { DEFAULT_FILE_NAME }
            val dir = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }
            val target = File(dir, safeName)
            try {
                val request = Request.Builder()
                    .url(apkUrl)
                    .header("Accept", "application/octet-stream")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null
                    body.byteStream().use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                if (target.length() == 0L) null else target
            } catch (e: Exception) {
                target.delete()
                null
            }
        }

    /** Launches the system installer for a previously downloaded [apkFile]. */
    fun install(context: Context, apkFile: File): InstallOutcome {
        if (!context.packageManager.canRequestPackageInstalls()) {
            return InstallOutcome.PermissionRequired
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            InstallOutcome.Launched
        } catch (e: Exception) {
            InstallOutcome.Failed(e.message ?: "Could not start the installer.")
        }
    }

    companion object {
        private const val UPDATES_DIR = "updates"
        const val DEFAULT_FILE_NAME = "tariffia-panel-update.apk"
        private const val APK_MIME = "application/vnd.android.package-archive"

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .build()
        }
    }
}
