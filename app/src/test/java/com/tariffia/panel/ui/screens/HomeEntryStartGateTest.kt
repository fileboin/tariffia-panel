package com.tariffia.panel.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeEntryStartGateTest {

    @Test
    fun homeEntryDispatchesAutomaticStartOnlyOnce() {
        val gate = HomeEntryStartGate()
        assertTrue(gate.tryDispatch())
        assertFalse(gate.tryDispatch())
        assertFalse(gate.tryDispatch())
    }
}
