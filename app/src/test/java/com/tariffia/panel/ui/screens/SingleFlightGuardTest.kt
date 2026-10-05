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
}
