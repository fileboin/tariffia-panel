package com.tariffia.panel.data.router

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class OllamaTunnelWatchTest {

    private val fastPollMs = 1L

    /** Records every update that reaches the (fake) embedded Router. */
    private class FakeRouter(private val failures: Int = 0) {
        val sent = mutableListOf<Boolean>()
        private var remainingFailures = failures

        suspend fun send(available: Boolean) {
            if (remainingFailures > 0) {
                remainingFailures--
                throw IllegalStateException("router unreachable")
            }
            sent += available
        }
    }

    private suspend fun awaitUntil(timeoutMs: Long = 2_000, condition: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!condition()) delay(1)
        }
    }

    @Test
    fun tunnelRemainsConnectedKeepsOllamaAvailable() = runBlocking {
        val router = FakeRouter()
        val publisher = OllamaAvailabilityPublisher(router::send)
        assertTrue(publisher.publish(true))
        val checks = AtomicInteger()
        val watch = OllamaTunnelWatch(this, isTunnelUp = { checks.incrementAndGet(); true }, publisher, fastPollMs)
        try {
            watch.start()
            awaitUntil { checks.get() >= 5 }
            assertTrue(watch.isRunning)
            assertEquals(listOf(true), router.sent)
        } finally {
            watch.stop()
        }
    }

    @Test
    fun tunnelLossMarksOllamaUnavailableAndWatcherExits() = runBlocking {
        val router = FakeRouter()
        val publisher = OllamaAvailabilityPublisher(router::send)
        publisher.publish(true)
        val checks = AtomicInteger()
        val watch = OllamaTunnelWatch(
            this,
            isTunnelUp = { checks.incrementAndGet() < 3 },
            publisher,
            fastPollMs,
        )
        try {
            watch.start()
            awaitUntil { !watch.isRunning }
            assertEquals(listOf(true, false), router.sent)
        } finally {
            watch.stop()
        }
    }

    @Test
    fun repeatedLossDoesNotSpamDuplicateUpdates() = runBlocking {
        val router = FakeRouter()
        val publisher = OllamaAvailabilityPublisher(router::send)
        publisher.publish(true)
        val watch = OllamaTunnelWatch(this, isTunnelUp = { false }, publisher, fastPollMs)
        try {
            watch.start()
            awaitUntil { !watch.isRunning }
            // A second watch on the already-lost tunnel and a later STOP both skip the send.
            watch.start()
            awaitUntil { !watch.isRunning }
            assertTrue(publisher.publish(false))
            assertEquals(listOf(true, false), router.sent)
        } finally {
            watch.stop()
        }
    }

    @Test
    fun failedLossUpdateIsRetriedUntilRouterAccepts() = runBlocking {
        val router = FakeRouter(failures = 2)
        val publisher = OllamaAvailabilityPublisher(router::send)
        publisher.publish(true) // the first attempt fails; nothing is recorded
        assertEquals(emptyList<Boolean>(), router.sent)
        val watch = OllamaTunnelWatch(this, isTunnelUp = { false }, publisher, fastPollMs)
        try {
            watch.start()
            awaitUntil { !watch.isRunning }
            assertEquals(listOf(false), router.sent)
        } finally {
            watch.stop()
        }
    }

    @Test
    fun explicitStopLeavesNoWatcherRunning() = runBlocking {
        val router = FakeRouter()
        val publisher = OllamaAvailabilityPublisher(router::send)
        publisher.publish(true)
        val checks = AtomicInteger()
        val watch = OllamaTunnelWatch(this, isTunnelUp = { checks.incrementAndGet(); true }, publisher, fastPollMs)
        watch.start()
        awaitUntil { checks.get() >= 2 }
        watch.stop()
        assertFalse(watch.isRunning)

        // Once stopped, the watcher cannot be restarted and a dropped tunnel is not reported.
        watch.start()
        assertFalse(watch.isRunning)
        val afterStop = checks.get()
        delay(20)
        assertEquals(afterStop, checks.get())
        assertEquals(listOf(true), router.sent)
    }

    @Test
    fun stopAfterLossDoesNotResendUnavailable() = runBlocking {
        val router = FakeRouter()
        val publisher = OllamaAvailabilityPublisher(router::send)
        publisher.publish(true)
        val watch = OllamaTunnelWatch(this, isTunnelUp = { false }, publisher, fastPollMs)
        try {
            watch.start()
            awaitUntil { !watch.isRunning }
        } finally {
            watch.stop()
        }
        // Explicit STOP path: publish(false) is a no-op because loss already reported it.
        assertTrue(publisher.publish(false))
        assertEquals(listOf(true, false), router.sent)
    }

    @Test
    fun startupBehaviorIsUnchangedAndWatcherStartsOnlyAfterTunnel() = runBlocking {
        val router = FakeRouter()
        val publisher = OllamaAvailabilityPublisher(router::send)
        val watchChecks = AtomicInteger()
        val watch = OllamaTunnelWatch(this, isTunnelUp = { watchChecks.incrementAndGet(); true }, publisher, fastPollMs)
        try {
            // Successful start: tunnel, then Router, then Ollama enabled exactly once.
            val started = startRouterAfterTunnel(
                tunnelStart = { true },
                routerStart = { true },
                setOllamaAvailable = { publisher.publish(it) },
            )
            assertTrue(started)
            watch.start()
            awaitUntil { watchChecks.get() >= 2 }
            assertEquals(listOf(true), router.sent)
        } finally {
            watch.stop()
        }

        // Failed tunnel: Ollama is disabled, Router still starts, and no watcher is started.
        val failedRouter = FakeRouter()
        val failedPublisher = OllamaAvailabilityPublisher(failedRouter::send)
        val failedChecks = AtomicInteger()
        val failedWatch = OllamaTunnelWatch(this, isTunnelUp = { failedChecks.incrementAndGet(); true }, failedPublisher, fastPollMs)
        val started = startRouterAfterTunnel(
            tunnelStart = { false },
            routerStart = { true },
            setOllamaAvailable = { failedPublisher.publish(it) },
        )
        assertFalse(started)
        assertEquals(listOf(false), failedRouter.sent)
        assertFalse(failedWatch.isRunning)
        assertEquals(0, failedChecks.get())
        failedWatch.stop()
    }
}
