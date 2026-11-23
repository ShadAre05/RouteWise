package com.routewise.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routewise.app.core.ErrorHandler
import com.routewise.app.core.Notifier
import com.routewise.app.data.Calculation
import com.routewise.app.data.FakeWeatherSource
import com.routewise.app.data.TripCalculator
import com.routewise.app.data.WeatherSource
import com.routewise.app.domain.TripText
import com.routewise.app.domain.WeatherText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** SCR-04: the result of the calculation with the choice between route variants. */
class BudgetViewModel(
    private val calculator: TripCalculator,
    private val errors: ErrorHandler,
    private val notifier: Notifier,
    private val holder: MutableStateFlow<Calculation?>,
    private val weatherSource: WeatherSource = FakeWeatherSource(),
) : ViewModel() {

    private val _weather = MutableStateFlow<String?>(null)
    /** One line about the weather at the destination on the day of arrival; null when there is no forecast. */
    val weather: StateFlow<String?> = _weather

    fun loadWeather() {
        val current = holder.value ?: return
        val trip = current.selected
        val day = try {
            java.time.OffsetDateTime.parse(trip.arrivalAt ?: return).toLocalDate()
        } catch (e: java.time.format.DateTimeParseException) {
            return
        }
        _weather.value = null
        viewModelScope.launch {
            val forecast = try {
                weatherSource.forecast(current.request.destination.lat, current.request.destination.lon, day)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _weather.value = forecast?.let { WeatherText.line(TripText.routeTitle(trip).substringAfter("→ "), it) }
        }
    }

    val calculation: StateFlow<Calculation?> get() = holder

    private val _switching = MutableStateFlow(false)
    val switching: StateFlow<Boolean> = _switching

    fun select(variant: String) {
        val current = holder.value ?: return
        if (_switching.value || variant == current.selected.variant) return
        _switching.value = true
        viewModelScope.launch {
            try {
                holder.value = calculator.select(current, variant)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notifier.show(errors.messageFor(e))
            } finally {
                _switching.value = false
            }
        }
    }
}
