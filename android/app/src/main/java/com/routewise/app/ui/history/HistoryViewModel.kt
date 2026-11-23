package com.routewise.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routewise.app.core.ErrorHandler
import com.routewise.app.core.Messages
import com.routewise.app.core.Notifier
import com.routewise.app.data.TripRepository
import com.routewise.app.data.api.ApiErrorKind
import com.routewise.app.data.api.ApiException
import com.routewise.app.data.api.TripDto
import com.routewise.app.ui.Load
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** SCR-06: saved trips, newest first. */
class HistoryViewModel(
    private val trips: TripRepository,
    private val errors: ErrorHandler,
    private val notifier: Notifier,
) : ViewModel() {

    private val _list = MutableStateFlow<Load<List<TripDto>>>(Load.Loading)
    val list: StateFlow<Load<List<TripDto>>> = _list

    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline

    private val _toDelete = MutableStateFlow<TripDto?>(null)
    val toDelete: StateFlow<TripDto?> = _toDelete

    fun load() {
        if (_list.value !is Load.Ready) _list.value = Load.Loading
        viewModelScope.launch {
            try {
                _list.value = Load.Ready(trips.page().items)
                _offline.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = errors.messageFor(e)
                val saved = trips.cached()
                if (e is ApiException && e.kind == ApiErrorKind.NETWORK && saved.isNotEmpty()) {
                    _list.value = Load.Ready(saved)
                    _offline.value = true
                } else if (_list.value is Load.Ready) {
                    notifier.show(message)
                } else {
                    _list.value = Load.Failed(message)
                }
            }
        }
    }

    fun askDelete(trip: TripDto) {
        _toDelete.value = trip
    }

    fun cancelDelete() {
        _toDelete.value = null
    }

    fun confirmDelete() {
        val trip = _toDelete.value ?: return
        _toDelete.value = null
        viewModelScope.launch {
            try {
                trips.delete(trip.id)
                val current = (_list.value as? Load.Ready)?.data.orEmpty()
                _list.value = Load.Ready(current.filter { it.id != trip.id })
                notifier.show(Messages.TRIP_DELETED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notifier.show(errors.messageFor(e))
            }
        }
    }
}
