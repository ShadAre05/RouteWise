package com.routewise.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routewise.app.core.ErrorHandler
import com.routewise.app.core.Notifier
import com.routewise.app.data.Calculation
import com.routewise.app.data.TripCalculator
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
) : ViewModel() {

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
