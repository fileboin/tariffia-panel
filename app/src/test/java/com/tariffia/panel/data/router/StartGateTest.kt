package com.tariffia.panel.data.router

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the single-flight start gate (repeated START must be idempotent). */
class StartGateTest {

    @Test
    fun firstEnterSucceedsAndSecondIsRejected() {
        val gate = StartGate()
        assertTrue(gate.tryEnter())
        assertFalse(gate.tryEnter())
        assertTrue(gate.isInFlight)
    }

    @Test
    fun exitAllowsEnterAgain() {
        val gate = StartGate()
        assertTrue(gate.tryEnter())
        gate.exit()
        assertFalse(gate.isInFlight)
        assertTrue(gate.tryEnter())
    }

    @Test
    fun manyRapidEntersOnlyOneSucceeds() {
        val gate = StartGate()
        val accepted = (1..10).count { gate.tryEnter() }
        assertTrue(accepted == 1)
    }
}
