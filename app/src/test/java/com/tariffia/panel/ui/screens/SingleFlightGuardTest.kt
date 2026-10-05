package com.tariffia.panel.ui.screens

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** JVM tests for the in-flight guard used by the Providers refresh. */
class SingleFlightGuardTest {

    @Test
    fun ignoresSecondStartWhileRunningAndRunsAgainAfterCompletion() = runBlocking {
        val guard = SingleFlightGuard()
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val gate = CompletableDeferred<Unit>()
            val started = AtomicInteger(0)

            assertTrue(guard.tryStart(scope) { started.incrementAndGet(); gate.await() })
            assertTrue(guard.isRunning)

            // Second start while the first is still running must be ignored.
            assertFalse(guard.tryStart(scope) { started.incrementAndGet() })

            gate.complete(Unit)
            guard.join()
            assertFalse(guard.isRunning)
            assertEquals(1, started.get())

            // After completion a new run is allowed again.
            assertTrue(guard.tryStart(scope) { started.incrementAndGet() })
            guard.join()
            assertEquals(2, started.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun saveIsSingleFlight() = runBlocking {
        val guard = SingleFlightGuard()
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val gate = CompletableDeferred<Unit>()
            val saves = AtomicInteger(0)
            assertTrue(guard.tryStart(scope) { saves.incrementAndGet(); gate.await() })
            assertFalse(guard.tryStart(scope) { saves.incrementAndGet() })
            gate.complete(Unit)
            guard.join()
            assertEquals(1, saves.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun clearIsSingleFlight() = runBlocking {
        val guard = SingleFlightGuard()
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val gate = CompletableDeferred<Unit>()
            val clears = AtomicInteger(0)
            assertTrue(guard.tryStart(scope) { clears.incrementAndGet(); gate.await() })
            assertFalse(guard.tryStart(scope) { clears.incrementAndGet() })
            gate.complete(Unit)
            guard.join()
            assertEquals(1, clears.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun saveAndClearShareOneGuardSoTheyCannotRace() = runBlocking {
        // One guard is used for both mutations, so a Clear cannot start during a Save.
        val guard = SingleFlightGuard()
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val gate = CompletableDeferred<Unit>()
            val saveRuns = AtomicInteger(0)
            val clearRuns = AtomicInteger(0)
            assertTrue(guard.tryStart(scope) { saveRuns.incrementAndGet(); gate.await() })
            assertFalse(guard.tryStart(scope) { clearRuns.incrementAndGet() })
            gate.complete(Unit)
            guard.join()
            assertEquals(1, saveRuns.get())
            assertEquals(0, clearRuns.get())
        } finally {
            scope.cancel()
        }
    }
}
