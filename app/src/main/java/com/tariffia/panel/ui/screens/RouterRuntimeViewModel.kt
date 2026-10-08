package com.tariffia.panel.ui.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.tariffia.panel.data.router.RouterRuntime
import com.tariffia.panel.data.router.RouterService
import com.tariffia.panel.data.router.RouterServiceContract
import kotlinx.coroutines.flow.StateFlow

/** Processed per Home ViewModel so recomposition/restoration does not dispatch duplicate starts. */
internal class HomeEntryStartGate {
    private var dispatched = false

    @Synchronized
    fun tryDispatch(): Boolean {
        if (dispatched) return false
        dispatched = true
        return true
    }
}

/**
 * Thin UI controller: it does NOT own the runtime. Start/stop are routed through the
 * foreground [RouterService] (the single owner); the runtime state and the key-sync
 * summary are observed from [RouterRuntime].
 */
class RouterRuntimeViewModel(application: Application) : AndroidViewModel(application) {

    private val homeEntryStartGate = HomeEntryStartGate()

    val state: StateFlow<RouterRuntime.State> = RouterRuntime.state
    val syncSummary: StateFlow<String?> = RouterRuntime.syncSummary

    fun start(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, RouterService::class.java).setAction(RouterServiceContract.ACTION_START),
        )
    }

    /** One-shot automatic entry point; dispatches through the existing service start action. */
    fun startOnHomeEntry(context: Context) {
        if (homeEntryStartGate.tryDispatch()) start(context)
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, RouterService::class.java).setAction(RouterServiceContract.ACTION_STOP),
        )
    }
}
