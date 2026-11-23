package com.routewise.app.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routewise.app.core.ErrorHandler
import com.routewise.app.core.Messages
import com.routewise.app.data.Calculation
import com.routewise.app.data.TripCalculator
import com.routewise.app.data.VehicleRepository
import com.routewise.app.data.api.ApiErrorKind
import com.routewise.app.data.api.ApiException
import com.routewise.app.data.api.PointDto
import com.routewise.app.data.api.TripRequest
import com.routewise.app.data.api.VehicleDto
import com.routewise.app.domain.Place
import com.routewise.app.domain.PlaceCatalog
import com.routewise.app.domain.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaceField(
    val query: String = "",
    val selected: Place? = null,
    val suggestions: List<Place> = emptyList(),
    val error: String? = null,
)

/** A failed calculation shown as a panel with "Try again" (UX SCR-08). */
data class PlanFailure(val title: String, val message: String)

data class PlanState(
    val origin: PlaceField = PlaceField(),
    val destination: PlaceField = PlaceField(),
    val dailyLimitH: Int = 8,
    val lodgingClass: String = "standard",
    val startFuel: String = "full",
    val vehicles: List<VehicleDto> = emptyList(),
    val vehicleId: String? = null,
    val vehiclesLoaded: Boolean = false,
    val calculating: Boolean = false,
    val slow: Boolean = false,
    val formError: String? = null,
    val failure: PlanFailure? = null,
) {
    val hasVehicle: Boolean? get() = if (vehiclesLoaded) vehicles.isNotEmpty() else null
    val vehicle: VehicleDto? get() = vehicles.firstOrNull { it.id == vehicleId } ?: vehicles.firstOrNull { it.isDefault } ?: vehicles.firstOrNull()

    /** The fuel-at-start control is shown only for a car with a known tank size (UX SCR-03, element 5). */
    val showFuelAtStart: Boolean get() = vehicle?.tankL != null

    val canCalculate: Boolean get() = origin.selected != null && destination.selected != null

    /** The reason why Calculate is disabled; the button is never a dead end. */
    val hint: String?
        get() = when {
            destination.selected == null -> Messages.SET_DESTINATION
            origin.selected == null -> Messages.SET_START
            else -> null
        }
}

/** SCR-03: choose the start, the destination, the day limit and the car, then calculate. */
class PlanViewModel(
    private val vehicles: VehicleRepository,
    private val calculator: TripCalculator,
    private val errors: ErrorHandler,
    private val result: MutableStateFlow<Calculation?>,
) : ViewModel() {

    private val _state = MutableStateFlow(PlanState())
    val state: StateFlow<PlanState> = _state

    /** Called when the screen is shown: the user may have added a car on the other tab. */
    fun refreshVehicles() {
        viewModelScope.launch {
            try {
                val list = vehicles.list()
                _state.update { it.copy(vehicles = list, vehiclesLoaded = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(formError = errors.messageFor(e)) }
            }
        }
    }

    fun onOriginQuery(text: String) = _state.update { it.copy(origin = typed(text), formError = null, failure = null) }
    fun onDestinationQuery(text: String) = _state.update { it.copy(destination = typed(text), formError = null, failure = null) }

    fun pickOrigin(place: Place) = _state.update {
        it.copy(origin = if (PlaceCatalog.isSupported(place)) PlaceField(place.label, place)
        else PlaceField(place.label, null, emptyList(), Messages.START_OUTSIDE_AREA))
    }

    fun pickDestination(place: Place) = _state.update {
        it.copy(destination = if (PlaceCatalog.isSupported(place)) PlaceField(place.label, place)
        else PlaceField(place.label, null, emptyList(), Messages.OUTSIDE_AREA))
    }

    /** The stepper of the daily limit: [delta] is +1 or -1 and the value stops at 4 and 10 hours. */
    fun stepLimit(delta: Int) = _state.update {
        it.copy(dailyLimitH = (it.dailyLimitH + delta).coerceIn(Validators.MIN_DAILY_LIMIT_H, Validators.MAX_DAILY_LIMIT_H))
    }

    fun onLodging(value: String) = _state.update { it.copy(lodgingClass = value) }
    fun onFuelAtStart(value: String) = _state.update { it.copy(startFuel = value) }
    fun onVehicle(id: String) = _state.update { it.copy(vehicleId = id) }
    fun dismissFailure() = _state.update { it.copy(failure = null) }

    private fun typed(text: String): PlaceField {
        val query = text.trim()
        val matches = if (query.length >= Validators.MIN_SEARCH_LENGTH) PlaceCatalog.search(query) else emptyList()
        val error = when {
            query.length < Validators.MIN_SEARCH_LENGTH -> null
            matches.isEmpty() -> Messages.noPlaces(query)
            else -> null
        }
        return PlaceField(text, null, matches, error)
    }

    fun calculate(onDone: () -> Unit) {
        val s = _state.value
        if (s.calculating) return
        val origin = s.origin.selected
        val destination = s.destination.selected
        if (origin == null || destination == null) return
        if (origin == destination) {
            _state.update { it.copy(destination = it.destination.copy(error = "Start and destination must differ")) }
            return
        }
        val vehicle = s.vehicle
        if (vehicle == null) {
            _state.update { it.copy(formError = Messages.NO_CAR) }
            return
        }
        _state.update { it.copy(calculating = true, slow = false, formError = null, failure = null) }
        val request = TripRequest(
            originLabel = origin.label,
            destinationLabel = destination.label,
            origin = PointDto(origin.lon, origin.lat),
            destination = PointDto(destination.lon, destination.lat),
            vehicleId = vehicle.id,
            dailyLimitH = s.dailyLimitH,
            lodgingClass = s.lodgingClass,
            startFuel = if (s.showFuelAtStart) s.startFuel else null,
        )
        val timer = viewModelScope.launch {
            delay(SLOW_AFTER_MS)
            _state.update { if (it.calculating) it.copy(slow = true) else it }
        }
        viewModelScope.launch {
            try {
                result.value = calculator.calculate(request)
                _state.update { it.copy(calculating = false, slow = false) }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { fail(it, e) }
            } finally {
                timer.cancel()
            }
        }
    }

    private fun fail(state: PlanState, e: Exception): PlanState {
        val kind = (e as? ApiException)?.kind
        val base = state.copy(calculating = false, slow = false)
        return when (kind) {
            ApiErrorKind.NETWORK -> base.copy(failure = PlanFailure("No connection", Messages.CONNECTION))
            ApiErrorKind.SERVER, ApiErrorKind.UPSTREAM ->
                base.copy(failure = PlanFailure("Service is unavailable", (e as ApiException).userMessage()))
            ApiErrorKind.VALIDATION -> base.copy(formError = Messages.NO_CAR)
            else -> base.copy(formError = errors.messageFor(e))
        }
    }

    companion object {
        const val SLOW_AFTER_MS = 5_000L
    }
}
