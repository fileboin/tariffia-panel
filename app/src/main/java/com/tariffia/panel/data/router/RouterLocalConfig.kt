package com.tariffia.panel.data.router

/**
 * Pure, dependency-free decisions for the embedded local Router configuration.
 * Kept free of Android APIs so it can be unit tested on the JVM.
 */
object RouterLocalConfig {

    /** The embedded Router always binds to loopback on this address. */
    const val LOCAL_URL = "http://127.0.0.1:8910"

    /** The resolved config: the local URL plus the token the Router must be given. */
    data class Resolved(
        val url: String,
        val token: String,
        val tokenWasGenerated: Boolean,
    )

    /**
     * Resolves the config for the embedded Router. A non-blank stored token is reused;
     * otherwise [generate] is called. The URL is always the local one.
     */
    fun resolve(storedToken: String?, generate: () -> String): Resolved {
        val hasStored = !storedToken.isNullOrBlank()
        val token = if (hasStored) storedToken!! else generate()
        return Resolved(url = LOCAL_URL, token = token, tokenWasGenerated = !hasStored)
    }
}
