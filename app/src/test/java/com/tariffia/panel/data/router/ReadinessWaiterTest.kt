package com.tariffia.panel.data.router

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessWaiterTest {

    private class FakeClock {
        var now = 0L
        var sleeps = 0
        val sleep: suspend (Long) -> Unit = { d ->
            sleeps++
            now += d
        }
    }

    @Test
    fun returnsTrueOnFirstSuccessWithoutSleeping() = runBlocking {
        val clock = FakeClock()
        val ready = ReadinessWaiter.awaitReady(
            timeoutMs = 1000,
            pollIntervalMs = 100,
            nowMs = { clock.now },
            sleep = clock.sleep,
            probe = { true },
        )
        assertTrue(ready)
        assertEquals(0, clock.sleeps)
    }

    @Test
    fun returnsTrueAfterSeveralPolls() = runBlocking {
        val clock = FakeClock()
        var calls = 0
        val ready = ReadinessWaiter.awaitReady(
            timeoutMs = 1000,
            pollIntervalMs = 100,
            nowMs = { clock.now },
            sleep = clock.sleep,
            probe = {
                calls++
                calls >= 3
            },
        )
        assertTrue(ready)
        assertEquals(3, calls)
        assertEquals(2, clock.sleeps)
    }

    @Test
    fun returnsFalseOnTimeout() = runBlocking {
        val clock = FakeClock()
        var calls = 0
        val ready = ReadinessWaiter.awaitReady(
            timeoutMs = 1000,
            pollIntervalMs = 100,
            nowMs = { clock.now },
            sleep = clock.sleep,
            probe = {
                calls++
                false
            },
        )
        assertFalse(ready)
        assertEquals(11, calls)
        assertEquals(10, clock.sleeps)
    }
}
