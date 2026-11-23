package com.routewise.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routewise.app.core.ErrorHandler
import com.routewise.app.core.Messages
import com.routewise.app.core.Notifier
import com.routewise.app.data.TripRepository
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

    private val _toDelete = MutableStateFlow<TripDto?>(null)
    val toDelete: StateFlow<TripDto?> = _toDelete

    fun load() {
        if (_list.value !is Load.Ready) _list.value = Load.Loading
        viewModelScope.launch {
            try {
                _list.value = Load.Ready(trips.page().items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_list.value is Load.Ready) notifier.show(errors.messageFor(e))
                else _list.value = Load.Failed(errors.messageFor(e))
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
