package com.tariffia.panel.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Outcome of launching the Android package installer. */
sealed interface InstallOutcome {
    /** The system installer UI was launched; the user still confirms the install. */
    data object Launched : InstallOutcome

    /** The app is not allowed to install packages yet (unknown sources disabled). */
    data object PermissionRequired : InstallOutcome

    data class Failed(val message: String) : InstallOutcome
}

/** Identity of an APK or an installed package, as needed for pre-install validation. */
internal data class PackageIdentity(
    val packageName: String?,
    val versionCode: Long,
    /** SHA-256 hex digests of the signing certificates. */
    val signingCertSha256: Set<String>,
)

/** Result of [InstallValidation.check]. */
internal sealed interface InstallCheck {
    data object Allowed : InstallCheck

    data class Rejected(val message: String) : InstallCheck
}

/** Pure, JVM-testable rules that decide whether an update APK may be handed to the installer. */
internal object InstallValidation {
    const val EXPECTED_PACKAGE = "com.tariffia.panel"
    const val SIGNATURE_MISMATCH_MESSAGE =
        "This update is signed with a different key and cannot replace the installed app."
    const val UNREADABLE_SIGNATURE_MESSAGE = "Could not verify the update's signing certificate."
    const val WRONG_PACKAGE_MESSAGE = "This file is not a Tariffia Panel update."
    const val NOT_NEWER_MESSAGE = "This update is not newer than the installed version."

    /**
     * Order: package, then signing certificate (readable and equal to the installed one), then
     * versionCode (strictly greater than the installed one).
     */
    fun check(archive: PackageIdentity, installed: PackageIdentity): InstallCheck {
        if (archive.packageName != EXPECTED_PACKAGE) return InstallCheck.Rejected(WRONG_PACKAGE_MESSAGE)
        if (archive.signingCertSha256.isEmpty() || installed.signingCertSha256.isEmpty()) {
            return InstallCheck.Rejected(UNREADABLE_SIGNATURE_MESSAGE)
        }
        if (archive.signingCertSha256 != installed.signingCertSha256) {
            return InstallCheck.Rejected(SIGNATURE_MISMATCH_MESSAGE)
        }
        if (archive.versionCode <= installed.versionCode) return InstallCheck.Rejected(NOT_NEWER_MESSAGE)
        return InstallCheck.Allowed
    }
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
        // Validate before the installer opens, so a rejected update is never reported as launched.
        val check = validate(context, apkFile)
        if (check is InstallCheck.Rejected) return InstallOutcome.Failed(check.message)
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

    private fun validate(context: Context, apkFile: File): InstallCheck {
        val pm = context.packageManager
        val archive = readIdentity(pm, apkFile.absolutePath)
            ?: return InstallCheck.Rejected("The downloaded update could not be read.")
        val installed = try {
            identityOf(pm.getPackageInfo(context.packageName, signatureFlags()))
        } catch (_: Exception) {
            return InstallCheck.Rejected("The installed app could not be read.")
        }
        return InstallValidation.check(archive, installed)
    }

    private fun readIdentity(pm: PackageManager, path: String): PackageIdentity? =
        pm.getPackageArchiveInfo(path, signatureFlags())?.let { identityOf(it) }

    private fun identityOf(info: PackageInfo): PackageIdentity =
        PackageIdentity(
            packageName = info.packageName,
            versionCode = versionCodeOf(info),
            signingCertSha256 = signingDigests(info),
        )

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun signingDigests(info: PackageInfo): Set<String> {
        val signers: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return signers.orEmpty().map { sha256Hex(it.toByteArray()) }.toSet()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

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
