package com.routewise.app.data.api

import com.routewise.app.core.Messages

/** What went wrong in a call to the API, independent of HTTP details. */
enum class ApiErrorKind {
    NETWORK,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    VALIDATION,
    NO_ROUTE,
    UPSTREAM,
    SERVER,
    UNKNOWN,
}

class ApiException(
    val kind: ApiErrorKind,
    val serverMessage: String? = null,
    val requestId: String? = null,
    val status: Int? = null,
    cause: Throwable? = null,
) : Exception(serverMessage ?: kind.name, cause) {

    /** Text for the user (the global error handler shows it in a snackbar or banner). */
    fun userMessage(): String = when (kind) {
        ApiErrorKind.NETWORK -> Messages.CONNECTION
        ApiErrorKind.UNAUTHORIZED -> Messages.SESSION_EXPIRED
        ApiErrorKind.FORBIDDEN -> "You are not allowed to do this."
        ApiErrorKind.NOT_FOUND -> "This item no longer exists."
        ApiErrorKind.CONFLICT -> serverMessage ?: Messages.UNKNOWN
        ApiErrorKind.VALIDATION -> serverMessage ?: Messages.UNKNOWN
        ApiErrorKind.NO_ROUTE -> Messages.NO_ROUTE
        ApiErrorKind.UPSTREAM -> Messages.serverError(status)
        ApiErrorKind.SERVER -> Messages.serverError(status)
        ApiErrorKind.UNKNOWN -> Messages.UNKNOWN
    }

    companion object {
        /** Maps the API error code and HTTP status to a kind. The `code` field is stable, the status is the fallback. */
        fun kindOf(status: Int, code: String?): ApiErrorKind = when {
            code == "no_route" -> ApiErrorKind.NO_ROUTE
            code == "upstream_unavailable" -> ApiErrorKind.UPSTREAM
            status == 401 -> ApiErrorKind.UNAUTHORIZED
            status == 403 -> ApiErrorKind.FORBIDDEN
            status == 404 -> ApiErrorKind.NOT_FOUND
            status == 409 -> ApiErrorKind.CONFLICT
            status == 422 -> ApiErrorKind.VALIDATION
            status == 502 || status == 503 || status == 504 -> ApiErrorKind.UPSTREAM
            status >= 500 -> ApiErrorKind.SERVER
            else -> ApiErrorKind.UNKNOWN
        }
    }
}
