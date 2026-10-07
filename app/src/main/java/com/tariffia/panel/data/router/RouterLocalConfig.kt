package com.tariffia.panel.data.router

/**
 * Pure, dependency-free decisions for the embedded local Router configuration.
 * Kept free of Android APIs so it can be unit tested on the JVM.
 */
object RouterLocalConfig {

    /** The embedded Router binds to loopback on this address. Used as the default. */
    const val LOCAL_URL = "http://127.0.0.1:8910"

    /** The resolved config: the effective URL, the token, and what had to be created. */
    data class Resolved(
        val url: String,
        val token: String,
        val tokenWasGenerated: Boolean,
        /** True when no URL was configured and the loopback default was applied. */
        val urlWasDefaulted: Boolean,
    )

    /**
     * Resolves the config for the embedded Router.
     *
     * - A non-blank stored URL is preserved (a deliberately configured remote URL is never
     *   overwritten); when none is configured the loopback default applies.
     * - A non-blank stored token is reused; otherwise [generate] is called.
     */
    fun resolve(storedUrl: String?, storedToken: String?, generate: () -> String): Resolved {
        val hasUrl = !storedUrl.isNullOrBlank()
        val url = if (hasUrl) storedUrl!!.trim() else LOCAL_URL

        val hasToken = !storedToken.isNullOrBlank()
        val token = if (hasToken) storedToken!! else generate()

        return Resolved(
            url = url,
            token = token,
            tokenWasGenerated = !hasToken,
            urlWasDefaulted = !hasUrl,
        )
    }
}
