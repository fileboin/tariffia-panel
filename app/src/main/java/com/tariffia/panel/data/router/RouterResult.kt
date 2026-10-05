package com.tariffia.panel.data.router

/**
 * Outcome of a single router HTTP call. Deliberately carries no response body for
 * failures so that nothing which might contain a secret can reach the UI or logs.
 */
sealed interface RouterResult<out T> {
    data class Success<T>(val value: T) : RouterResult<T>

    /** HTTP 401 or 403. */
    data object AuthenticationFailed : RouterResult<Nothing>

    /** Any other non-2xx HTTP status. */
    data class HttpError(val code: Int) : RouterResult<Nothing>

    /** 2xx but the body could not be parsed. */
    data class InvalidResponse(val reason: String) : RouterResult<Nothing>

    /** The host was unreachable or the request could not be completed. */
    data class ConnectionFailed(val reason: String) : RouterResult<Nothing>
}
