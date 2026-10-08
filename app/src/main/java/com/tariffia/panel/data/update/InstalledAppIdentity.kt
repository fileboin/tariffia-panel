package com.tariffia.panel.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/**
 * DIAGNOSTIC ONLY: identity of the running app build, used to check update compatibility.
 * Public package metadata only (versions and certificate digests); no secrets, no app data.
 */
data class InstalledAppIdentity(
    val versionName: String,
    val versionCode: Long,
    /** SHA-256 digest of each signing certificate, colon-separated uppercase hex. */
    val signingCertSha256: List<String>,
)

/** SHA-256 of DER certificate bytes as colon-separated uppercase hex, e.g. "BA:78:16:...". */
internal fun certSha256Colon(der: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(der).joinToString(":") { "%02X".format(it) }

/**
 * Reads this app's own identity from PackageManager. Returns null if it cannot be read.
 * Querying the app's own package needs no extra permission.
 */
fun readInstalledAppIdentity(context: Context): InstalledAppIdentity? = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, signatureFlags())
    InstalledAppIdentity(
        versionName = info.versionName.orEmpty(),
        versionCode = versionCodeOf(info),
        signingCertSha256 = signers(info).map { certSha256Colon(it.toByteArray()) },
    )
}.getOrNull()

@Suppress("DEPRECATION")
private fun signatureFlags(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }

@Suppress("DEPRECATION")
private fun versionCodeOf(info: PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

@Suppress("DEPRECATION")
private fun signers(info: PackageInfo): List<Signature> {
    val array: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.signingInfo?.apkContentsSigners
    } else {
        info.signatures
    }
    return array.orEmpty().toList()
}
