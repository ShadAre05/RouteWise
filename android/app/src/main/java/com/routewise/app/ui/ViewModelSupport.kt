package com.routewise.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Builds a view model with its dependencies without a DI framework. */
inline fun <reified VM : ViewModel> factoryOf(crossinline build: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = build() as T
    }

/** State of a screen that loads data. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String) : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
}
