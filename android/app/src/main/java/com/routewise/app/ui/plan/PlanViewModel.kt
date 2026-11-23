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
import com.routewise.app.domain.Place
import com.routewise.app.domain.PlaceCatalog
import com.routewise.app.domain.Validators
import kotlinx.coroutines.CancellationException
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

data class PlanState(
    val origin: PlaceField = PlaceField(),
    val destination: PlaceField = PlaceField(),
    val dailyLimitH: Int = 8,
    val lodgingClass: String = "standard",
    val hasVehicle: Boolean? = null,
    val calculating: Boolean = false,
    val formError: String? = null,
)

/** SCR-03: choose the start, the destination and the day limit, then calculate. */
class PlanViewModel(
    private val vehicles: VehicleRepository,
    private val calculator: TripCalculator,
    private val errors: ErrorHandler,
    private val result: MutableStateFlow<Calculation?>,
) : ViewModel() {

    private val _state = MutableStateFlow(PlanState())
    val state: StateFlow<PlanState> = _state

    private var defaultVehicleId: String? = null

    /** Called when the screen is shown: the user may have added a car on the other tab. */
    fun refreshVehicles() {
        viewModelScope.launch {
            try {
                val list = vehicles.list()
                defaultVehicleId = (list.firstOrNull { it.isDefault } ?: list.firstOrNull())?.id
                _state.update { it.copy(hasVehicle = list.isNotEmpty()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(formError = errors.messageFor(e)) }
            }
        }
    }

    fun onOriginQuery(text: String) = _state.update { it.copy(origin = typed(text), formError = null) }
    fun onDestinationQuery(text: String) = _state.update { it.copy(destination = typed(text), formError = null) }
    fun pickOrigin(place: Place) = _state.update { it.copy(origin = PlaceField(place.label, place)) }
    fun pickDestination(place: Place) = _state.update { it.copy(destination = PlaceField(place.label, place)) }
    fun onLimit(hours: Int) = _state.update { it.copy(dailyLimitH = hours) }
    fun onLodging(value: String) = _state.update { it.copy(lodgingClass = value) }

    private fun typed(text: String): PlaceField {
        val query = text.trim()
        val matches = if (query.length >= Validators.MIN_SEARCH_LENGTH) PlaceCatalog.search(query) else emptyList()
        val error = when {
            query.isEmpty() || query.length < Validators.MIN_SEARCH_LENGTH -> null
            matches.isEmpty() -> Messages.noPlaces(query)
            else -> null
        }
        return PlaceField(text, null, matches, error)
    }

    fun calculate(onDone: () -> Unit) {
        val s = _state.value
        if (s.calculating) return
        val originError = placeError(s.origin)
        val destinationError = placeError(s.destination)
        val limitError = Validators.dailyLimit(s.dailyLimitH)
        if (originError != null || destinationError != null || limitError != null) {
            _state.update {
                it.copy(
                    origin = it.origin.copy(error = originError),
                    destination = it.destination.copy(error = destinationError),
                    formError = limitError,
                )
            }
            return
        }
        val origin = s.origin.selected!!
        val destination = s.destination.selected!!
        if (origin == destination) {
            _state.update { it.copy(destination = it.destination.copy(error = "Start and destination must differ")) }
            return
        }
        if (s.hasVehicle == false) {
            _state.update { it.copy(formError = Messages.NO_CAR) }
            return
        }
        _state.update { it.copy(calculating = true, formError = null) }
        val request = TripRequest(
            originLabel = origin.label,
            destinationLabel = destination.label,
            origin = PointDto(origin.lon, origin.lat),
            destination = PointDto(destination.lon, destination.lat),
            vehicleId = defaultVehicleId,
            dailyLimitH = s.dailyLimitH,
            lodgingClass = s.lodgingClass,
        )
        viewModelScope.launch {
            try {
                result.value = calculator.calculate(request)
                _state.update { it.copy(calculating = false) }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val noCar = e is ApiException && e.kind == ApiErrorKind.VALIDATION
                _state.update {
                    it.copy(calculating = false, formError = if (noCar) Messages.NO_CAR else errors.messageFor(e))
                }
            }
        }
    }

    private fun placeError(field: PlaceField): String? = when {
        field.selected != null -> null
        field.query.trim().length < Validators.MIN_SEARCH_LENGTH -> Messages.SEARCH_SHORT
        field.suggestions.isEmpty() -> Messages.noPlaces(field.query.trim())
        else -> "Choose a place from the list"
    }
}
