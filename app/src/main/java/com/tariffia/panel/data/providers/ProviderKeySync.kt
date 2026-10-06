package com.tariffia.panel.data.providers

import android.content.Context
import com.tariffia.panel.data.router.RouterClient
import com.tariffia.panel.data.router.RouterResult

/**
 * Outcome of pushing one provider's key to the Router. Never carries the key.
 */
sealed interface ProviderSyncResult {
    data object Synced : ProviderSyncResult
    data object SkippedNoKey : ProviderSyncResult
    data object AuthRejected : ProviderSyncResult
    data object ProviderNotAllowed : ProviderSyncResult
    data object Unreachable : ProviderSyncResult
    data class Failed(val message: String) : ProviderSyncResult
}

data class ProviderSyncOutcome(val providerId: String, val result: ProviderSyncResult)

/** Aggregate of a sync run. Only counts and provider IDs — never keys. */
data class ProviderSyncReport(val outcomes: List<ProviderSyncOutcome>) {
    val synced: Int get() = outcomes.count { it.result is ProviderSyncResult.Synced }
    val skipped: Int get() = outcomes.count { it.result is ProviderSyncResult.SkippedNoKey }
    val failed: Int get() = outcomes.count {
        it.result !is ProviderSyncResult.Synced && it.result !is ProviderSyncResult.SkippedNoKey
    }
    val failedProviderIds: List<String> get() =
        outcomes.filter { it.result !is ProviderSyncResult.Synced && it.result !is ProviderSyncResult.SkippedNoKey }
            .map { it.providerId }
}

/**
 * Pushes every locally stored provider key to the Router, one PUT per provider.
 * Pure coordination: the key source and the transport are injected so it is unit
 * testable and so the key value never leaves the read/PUT path.
 *
 * A single provider's failure never stops the others.
 */
class ProviderKeySync(
    private val hasKey: (providerId: String) -> Boolean,
    private val readKey: (providerId: String) -> String?,
    private val putKey: suspend (providerId: String, key: String) -> ProviderSyncResult,
) {

    suspend fun syncAll(providerIds: List<String>): ProviderSyncReport {
        val outcomes = ArrayList<ProviderSyncOutcome>(providerIds.size)
        for (id in providerIds) {
            val result: ProviderSyncResult = when {
                !hasKey(id) -> ProviderSyncResult.SkippedNoKey
                else -> {
                    val key = readKey(id)
                    if (key.isNullOrBlank()) {
                        ProviderSyncResult.SkippedNoKey
                    } else {
                        // Guard the transport so one failure cannot abort the loop.
                        runCatching { putKey(id, key) }
                            .getOrElse { ProviderSyncResult.Failed("request failed") }
                    }
                }
            }
            outcomes.add(ProviderSyncOutcome(id, result))
        }
        return ProviderSyncReport(outcomes)
    }
}

/** Maps a Router HTTP result onto a [ProviderSyncResult]. Carries no body/key. */
object ProviderSyncResultMapper {
    fun from(result: RouterResult<Unit>): ProviderSyncResult = when (result) {
        is RouterResult.Success -> ProviderSyncResult.Synced
        RouterResult.AuthenticationFailed -> ProviderSyncResult.AuthRejected
        is RouterResult.HttpError ->
            if (result.code == 404) ProviderSyncResult.ProviderNotAllowed
            else ProviderSyncResult.Failed("Router error (HTTP ${result.code}).")
        is RouterResult.InvalidResponse -> ProviderSyncResult.Failed("Router rejected the request.")
        is RouterResult.ConnectionFailed -> ProviderSyncResult.Unreachable
    }
}

/** Human-readable, key-free summary for the Home screen. */
fun ProviderSyncReport.summaryText(): String {
    val parts = mutableListOf("$synced synced")
    if (skipped > 0) parts += "$skipped skipped"
    if (failed > 0) parts += "$failed failed"
    val base = "Key sync: ${parts.joinToString(", ")}."
    return if (failedProviderIds.isEmpty()) base else "$base Failed: ${failedProviderIds.joinToString(", ")}."
}

/**
 * Production wiring: reads every stored key from [SecureProviderKeyStore] (the single
 * source of truth) and PUTs it to the local Router via the existing [RouterClient].
 */
object ProviderKeySyncRunner {

    suspend fun syncToLocalRouter(
        context: Context,
        baseUrl: String,
        token: String,
    ): ProviderSyncReport {
        val store = SecureProviderKeyStore(context)
        val client = RouterClient()
        val sync = ProviderKeySync(
            hasKey = store::hasKey,
            readKey = store::readKey,
            putKey = { providerId, key ->
                ProviderSyncResultMapper.from(client.syncProviderKey(baseUrl, token, providerId, key))
            },
        )
        return sync.syncAll(store.providerIdsWithKeys())
    }
}
