package com.routewise.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routewise.app.core.ErrorHandler
import com.routewise.app.data.TripRepository
import com.routewise.app.data.api.TripDto
import com.routewise.app.ui.Load
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TripDetailViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    private val errors: ErrorHandler,
) : ViewModel() {

    private val _trip = MutableStateFlow<Load<TripDto>>(Load.Loading)
    val trip: StateFlow<Load<TripDto>> = _trip

    init {
        load()
    }

    fun load() {
        _trip.value = Load.Loading
        viewModelScope.launch {
            _trip.value = try {
                Load.Ready(trips.get(tripId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failed(errors.messageFor(e))
            }
        }
    }
}
