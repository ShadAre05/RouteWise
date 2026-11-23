package com.routewise.app.core

import com.routewise.app.data.api.ApiErrorKind
import com.routewise.app.data.api.ApiException
import kotlinx.coroutines.CancellationException

/**
 * The global error handler: every failure of an operation ends here. It logs the cause (never user data) and
 * returns the text for the user, so no screen invents its own message and no exception goes unhandled.
 */
class ErrorHandler(private val logger: Logger) {

    fun messageFor(error: Throwable): String {
        if (error is CancellationException) throw error
        return if (error is ApiException) {
            logger.warn("API error ${error.kind} request=${error.requestId ?: "-"}")
            error.userMessage()
        } else {
            logger.error("Unexpected error", error)
            Messages.UNKNOWN
        }
    }

    /** True when the user has to sign in again (the session was ended by the server). */
    fun isSessionEnded(error: Throwable): Boolean =
        error is ApiException && error.kind == ApiErrorKind.UNAUTHORIZED
}
