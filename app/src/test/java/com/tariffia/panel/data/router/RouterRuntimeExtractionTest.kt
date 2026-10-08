package com.tariffia.panel.data.router

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for Router-only bundle extraction/version marker behavior. */
class RouterRuntimeExtractionTest {

    @Test
    fun firstUseExtractsThenWritesVersionMarker() = withTempDir { root ->
        var extractions = 0

        ensureVersionedRouterBundle(root, "router-v1") { target ->
            extractions++
            File(target, "dist/src/cli/index.js").apply {
                parentFile.mkdirs()
                writeText("router v1")
            }
        }

        assertEquals(1, extractions)
        assertEquals("router-v1", File(root, ROUTER_BUNDLE_MARKER).readText())
        assertEquals("router v1", File(root, "dist/src/cli/index.js").readText())
    }

    @Test
    fun sameVersionDoesNotReextract() = withTempDir { root ->
        var extractions = 0
        val extract: (File) -> Unit = { target ->
            extractions++
            File(target, "dist/src/cli/index.js").apply {
                parentFile.mkdirs()
                writeText("router bundle")
            }
        }

        ensureVersionedRouterBundle(root, "router-v1", extract)
        ensureVersionedRouterBundle(root, "router-v1", extract)

        assertEquals(1, extractions)
        assertEquals("router bundle", File(root, "dist/src/cli/index.js").readText())
    }

    @Test
    fun versionMismatchReplacesOnlyRouterDirectoryContents() = withTempDir { parent ->
        val root = File(parent, "router")
        val nodeInfo = File(parent, "node-info.json").apply { writeText("keep") }
        ensureVersionedRouterBundle(root, "router-v1") { target ->
            File(target, "old-router-file").writeText("old")
        }

        ensureVersionedRouterBundle(root, "router-v2") { target ->
            File(target, "new-router-file").writeText("new")
        }

        assertEquals("router-v2", File(root, ROUTER_BUNDLE_MARKER).readText())
        assertFalse(File(root, "old-router-file").exists())
        assertEquals("new", File(root, "new-router-file").readText())
        // A sibling application file is outside the replacement directory and remains intact.
        assertTrue(nodeInfo.exists())
        assertEquals("keep", nodeInfo.readText())
    }

    @Test
    fun failedExtractionDoesNotLeaveAFalseVersionMarker() = withTempDir { parent ->
        val root = File(parent, "router")
        try {
            ensureVersionedRouterBundle(root, "router-v2") { target ->
                File(target, "partial-file").writeText("partial")
                throw IllegalStateException("extract failed")
            }
            throw AssertionError("expected extraction failure")
        } catch (expected: IllegalStateException) {
            assertEquals("extract failed", expected.message)
        }

        assertFalse(File(root, ROUTER_BUNDLE_MARKER).exists())
        assertFalse(root.exists())
    }

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("router-runtime-extraction-test").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
