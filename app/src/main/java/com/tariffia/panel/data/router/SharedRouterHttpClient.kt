package com.tariffia.panel.data.router

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Single, process-scoped OkHttpClient shared by every [RouterClient]. Sharing one client
 * keeps a single connection pool and dispatcher for the app instead of one per screen.
 *
 * It is intentionally never closed: it lives for the process lifetime and no
 * ViewModel/screen owns or shuts it down. No logging/interceptor is added, so the
 * Authorization token is never logged.
 */
internal object SharedRouterHttpClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
