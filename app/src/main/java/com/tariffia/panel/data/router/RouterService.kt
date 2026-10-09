package com.tariffia.panel.data.router

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.tariffia.panel.data.ssh.SshSessionSecrets
import com.tariffia.panel.data.ssh.SshTunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the embedded Tariffia Router for its whole lifetime.
 * It is the ONLY component that starts/stops [RouterRuntime], which keeps the Router
 * alive when the Panel Activity is backgrounded or the screen is locked.
 *
 * ACTION_START: show the persistent notification, then bring the Router up (idempotent)
 * and sync provider keys once READY. ACTION_STOP: mark stopped, remove the notification,
 * stop the service and terminate the process — embedded Node has no safe graceful
 * shutdown/restart path in-process, so terminating the process is the deterministic stop.
 */
class RouterService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Single writer of Ollama availability to the embedded Router (deduplicated). */
    private val ollamaPublisher = OllamaAvailabilityPublisher { available ->
        check(RouterRuntime.setOllamaAvailability(applicationContext, available)) {
            "Ollama availability update was not accepted by the Router."
        }
    }

    /** Marks Ollama unavailable if the established SSH tunnel is lost while Router runs. */
    private val ollamaTunnelWatch = OllamaTunnelWatch(
        scope = scope,
        isTunnelUp = { SshTunnel.isUp() },
        publisher = ollamaPublisher,
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (RouterServiceContract.commandFor(intent?.action)) {
            RouterServiceContract.Command.START -> handleStart()
            RouterServiceContract.Command.STOP -> handleStop()
            RouterServiceContract.Command.NONE -> Unit
        }
        return START_NOT_STICKY
    }

    private fun handleStart() {
        startForegroundNow()
        // Idempotent: never start a second Node/Router instance.
        if (RouterStartGuard.shouldStart(RouterRuntime.state.value)) {
            scope.launch {
                // Establish the SSH local forward (127.0.0.1:11434 -> VPS 127.0.0.1:11434)
                // BEFORE the Router is expected to serve, so its existing Ollama baseUrl is
                // reachable. Best-effort: a tunnel failure is recorded in SshTunnel.lastError()
                // and must not prevent the Router from starting.
                val tunnelStarted = startRouterAfterTunnel(
                    tunnelStart = { SshTunnel.start(applicationContext) },
                    routerStart = { RouterRuntime.bringUp(applicationContext) },
                    setOllamaAvailable = { available -> ollamaPublisher.publish(available) },
                )
                if (tunnelStarted) {
                    // Only an established tunnel is watched; a failed start is reported below.
                    ollamaTunnelWatch.start()
                } else {
                    RouterRuntime.reportSshTunnelFailure(
                        SshTunnel.lastError(),
                        SshSessionSecrets.password(),
                    )
                }
            }
        }
    }

    private fun handleStop() {
        // Stop watching before the explicit STOP so a late tunnel drop cannot race it.
        ollamaTunnelWatch.stop()
        scope.launch {
            try {
                // Tell the embedded Router to fail closed BEFORE releasing the SSH forward.
                // Skipped when the loss watcher already reported unavailable (publisher dedup).
                stopTunnelAfterDisablingOllama(
                    setOllamaUnavailable = { ollamaPublisher.publish(false) },
                    stopTunnel = { SshTunnel.stop() },
                )
            } finally {
                // Drop the session-only SSH password and preserve the existing STOP behavior.
                SshSessionSecrets.clear()
                RouterRuntime.markStopped()
                ServiceCompat.stopForeground(this@RouterService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                // Deterministic stop (no graceful Node shutdown available in-process).
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    private fun startForegroundNow() {
        ensureChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Tariffia Router",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Keeps the local Tariffia Router running."
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tariffia Router")
            .setContentText("Running locally on 127.0.0.1:8910")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    override fun onDestroy() {
        ollamaTunnelWatch.stop()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "tariffia_router"
        const val NOTIFICATION_ID = 8910
    }
}
