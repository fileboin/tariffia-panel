package com.tariffia.panel.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallValidationTest {

    private val stableCert = setOf("c73bfdc0302ac7313b0e61287e53b20bf8ea6ceda32b5ecd382762f8338f6ad2")
    private val otherCert = setOf("3a0e455f0ebc21240ec42c7f9a146d1104a537ec7a2ad596acb5cbb8b51c6628")

    private fun identity(
        packageName: String? = InstallValidation.EXPECTED_PACKAGE,
        versionCode: Long,
        certs: Set<String> = stableCert,
    ) = PackageIdentity(packageName, versionCode, certs)

    @Test
    fun matchingCertificateAndNewerVersionIsAllowed() {
        val result = InstallValidation.check(
            archive = identity(versionCode = 3),
            installed = identity(versionCode = 2),
        )
        assertEquals(InstallCheck.Allowed, result)
    }

    @Test
    fun differentCertificateIsRejectedWithKeyMismatchMessage() {
        val result = InstallValidation.check(
            archive = identity(versionCode = 3, certs = stableCert),
            installed = identity(versionCode = 2, certs = otherCert),
        )
        assertEquals(
            InstallCheck.Rejected(InstallValidation.SIGNATURE_MISMATCH_MESSAGE),
            result,
        )
        assertTrue((result as InstallCheck.Rejected).message.contains("different key"))
    }

    @Test
    fun sameVersionCodeIsRejected() {
        val result = InstallValidation.check(
            archive = identity(versionCode = 2),
            installed = identity(versionCode = 2),
        )
        assertEquals(InstallCheck.Rejected(InstallValidation.NOT_NEWER_MESSAGE), result)
    }

    @Test
    fun olderVersionCodeIsRejected() {
        val result = InstallValidation.check(
            archive = identity(versionCode = 1),
            installed = identity(versionCode = 2),
        )
        assertEquals(InstallCheck.Rejected(InstallValidation.NOT_NEWER_MESSAGE), result)
    }

    @Test
    fun wrongPackageIsRejected() {
        val result = InstallValidation.check(
            archive = identity(packageName = "com.example.other", versionCode = 3),
            installed = identity(versionCode = 2),
        )
        assertEquals(InstallCheck.Rejected(InstallValidation.WRONG_PACKAGE_MESSAGE), result)
    }

    @Test
    fun unreadableSignatureIsRejectedWithoutKeyMismatchMessage() {
        val result = InstallValidation.check(
            archive = identity(versionCode = 3, certs = emptySet()),
            installed = identity(versionCode = 2),
        )
        assertEquals(InstallCheck.Rejected(InstallValidation.UNREADABLE_SIGNATURE_MESSAGE), result)
    }

    @Test
    fun packageIsCheckedBeforeSignatureAndVersion() {
        val result = InstallValidation.check(
            archive = identity(packageName = "com.example.other", versionCode = 1, certs = otherCert),
            installed = identity(versionCode = 2),
        )
        assertEquals(InstallCheck.Rejected(InstallValidation.WRONG_PACKAGE_MESSAGE), result)
    }
}
