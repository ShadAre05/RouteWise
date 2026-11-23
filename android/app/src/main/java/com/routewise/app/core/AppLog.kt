package com.routewise.app.core

import android.util.Log

/** Minimal logging facade. Messages never contain passwords, tokens or e-mail addresses. */
interface Logger {
    fun info(message: String)
    fun warn(message: String, error: Throwable? = null)
    fun error(message: String, error: Throwable? = null)
}

class AndroidLogger(private val tag: String = "RouteWise") : Logger {
    override fun info(message: String) {
        Log.i(tag, message)
    }

    override fun warn(message: String, error: Throwable?) {
        Log.w(tag, message, error)
    }

    override fun error(message: String, error: Throwable?) {
        Log.e(tag, message, error)
    }
}

/** Logger for unit tests and previews. */
object SilentLogger : Logger {
    override fun info(message: String) = Unit
    override fun warn(message: String, error: Throwable?) = Unit
    override fun error(message: String, error: Throwable?) = Unit
}
