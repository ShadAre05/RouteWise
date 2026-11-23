package com.routewise.app.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Carries short messages from view models to the one snackbar host of the app. */
class Notifier {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun show(message: String) {
        _events.tryEmit(message)
    }
}
