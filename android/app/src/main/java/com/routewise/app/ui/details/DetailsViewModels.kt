package com.routewise.app.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routewise.app.core.ErrorHandler
import com.routewise.app.core.Notifier
import com.routewise.app.data.Calculation
import com.routewise.app.data.ReferenceRepository
import com.routewise.app.data.TripCalculator
import com.routewise.app.data.api.ApiErrorKind
import com.routewise.app.data.api.ApiException
import com.routewise.app.data.api.TrendPointDto
import com.routewise.app.domain.DetailsText
import com.routewise.app.domain.Validators
import com.routewise.app.ui.Load
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** SCR-10: speed and limit of every country of the selected route. */
class RouteDetailsViewModel(
    private val holder: StateFlow<Calculation?>,
    private val reference: ReferenceRepository,
    private val errors: ErrorHandler,
) : ViewModel() {
    private val _rows = MutableStateFlow<Load<List<DetailsText.SpeedRow>>>(Load.Loading)
    val rows: StateFlow<Load<List<DetailsText.SpeedRow>>> = _rows
    val calculation: StateFlow<Calculation?> get() = holder

    fun load() {
        val trip = holder.value?.selected ?: return
        _rows.value = Load.Loading
        viewModelScope.launch {
            try {
                _rows.value = Load.Ready(DetailsText.speedRows(trip, reference.speedProfiles()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _rows.value = Load.Failed(errors.messageFor(e))
            }
        }
    }
}

data class TrendState(
    val countries: List<String> = emptyList(),
    val country: String? = null,
    val fuelType: String = "",
    val points: Load<List<TrendPointDto>> = Load.Loading,
)

/** SCR-12: weekly national average prices of one country; the fuel type is the one of the car on the trip. */
class FuelTrendViewModel(
    private val holder: StateFlow<Calculation?>,
    private val reference: ReferenceRepository,
    private val errors: ErrorHandler,
) : ViewModel() {
    private val _state = MutableStateFlow(TrendState())
    val state: StateFlow<TrendState> = _state

    fun start() {
        val trip = holder.value?.selected ?: return
        val countries = trip.lines.filter { it.kind == "fuel" }.mapNotNull { it.country }.distinct()
        _state.update { it.copy(countries = countries, fuelType = trip.fuelType) }
        countries.firstOrNull()?.let(::select)
    }

    fun select(country: String) {
        _state.update { it.copy(country = country, points = Load.Loading) }
        val fuel = _state.value.fuelType
        viewModelScope.launch {
            val result: Load<List<TrendPointDto>> = try {
                Load.Ready(reference.fuelTrend(country, fuel))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.kind == ApiErrorKind.NOT_FOUND) Load.Ready(emptyList()) else Load.Failed(errors.messageFor(e))
            } catch (e: Exception) {
                Load.Failed(errors.messageFor(e))
            }
            if (_state.value.country == country) _state.update { it.copy(points = result) }
        }
    }
}

data class LodgingState(
    val lodgingClass: String = "standard",
    val price: String = "",
    val error: String? = null,
    val busy: Boolean = false,
)

/** SCR-05: the lodging class and the price the user found; the trip is calculated again with it. */
class LodgingViewModel(
    private val calculator: TripCalculator,
    private val holder: MutableStateFlow<Calculation?>,
    private val errors: ErrorHandler,
    private val notifier: Notifier,
) : ViewModel() {
    private val _state = MutableStateFlow(LodgingState(lodgingClass = holder.value?.request?.lodgingClass ?: "standard"))
    val state: StateFlow<LodgingState> = _state
    val calculation: StateFlow<Calculation?> get() = holder

    fun onPrice(value: String) = _state.update { it.copy(price = value, error = null) }

    fun chooseClass(value: String) {
        if (_state.value.busy || value == _state.value.lodgingClass) return
        _state.update { it.copy(lodgingClass = value) }
        recalculate(entered = null)
    }

    fun apply() {
        val error = Validators.lodgingPrice(_state.value.price)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        recalculate(_state.value.price.trim().replace(',', '.').toBigDecimal().setScale(2).toPlainString())
    }

    fun useEstimate() {
        _state.update { it.copy(price = "", error = null) }
        recalculate(entered = null)
    }

    private fun recalculate(entered: String?) {
        val current = holder.value ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                holder.value = calculator.withLodging(current, _state.value.lodgingClass, entered)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notifier.show(errors.messageFor(e))
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

}
