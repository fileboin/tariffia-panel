package com.tariffia.panel.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDownloadValidationTest {

    @Test
    fun nonEmptyFileThatParsesIsAccepted() {
        assertTrue(acceptDownloadedApk(bytesOnDisk = 1024, parses = true))
    }

    @Test
    fun emptyFileIsRejectedEvenIfItParses() {
        assertFalse(acceptDownloadedApk(bytesOnDisk = 0, parses = true))
    }

    @Test
    fun fileThatDoesNotParseIsRejected() {
        assertFalse(acceptDownloadedApk(bytesOnDisk = 1024, parses = false))
    }

    @Test
    fun emptyAndUnparseableFileIsRejected() {
        assertFalse(acceptDownloadedApk(bytesOnDisk = 0, parses = false))
    }
}
