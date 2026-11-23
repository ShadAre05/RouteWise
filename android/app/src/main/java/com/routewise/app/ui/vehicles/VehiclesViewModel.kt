package com.routewise.app.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routewise.app.core.ErrorHandler
import com.routewise.app.core.Messages
import com.routewise.app.core.Notifier
import com.routewise.app.data.VehicleRepository
import com.routewise.app.data.api.VehicleDto
import com.routewise.app.data.api.VehicleRequest
import com.routewise.app.domain.Validators
import com.routewise.app.ui.Load
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VehicleForm(
    val id: String? = null,
    val name: String = "",
    val fuelType: String = "petrol95",
    val consumption: String = "",
    val tank: String = "",
    val nameError: String? = null,
    val consumptionError: String? = null,
    val tankError: String? = null,
    val saving: Boolean = false,
)

/** SCR-02: the list of the user's cars with add, edit, delete and "make default". */
class VehiclesViewModel(
    private val repository: VehicleRepository,
    private val errors: ErrorHandler,
    private val notifier: Notifier,
) : ViewModel() {

    private val _list = MutableStateFlow<Load<List<VehicleDto>>>(Load.Loading)
    val list: StateFlow<Load<List<VehicleDto>>> = _list

    private val _form = MutableStateFlow<VehicleForm?>(null)
    val form: StateFlow<VehicleForm?> = _form

    private val _toDelete = MutableStateFlow<VehicleDto?>(null)
    val toDelete: StateFlow<VehicleDto?> = _toDelete

    init {
        load()
    }

    fun load() {
        _list.value = Load.Loading
        viewModelScope.launch {
            _list.value = try {
                Load.Ready(repository.list())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failed(errors.messageFor(e))
            }
        }
    }

    // ---- form -----------------------------------------------------------------------------------------------

    fun startAdd() {
        _form.value = VehicleForm()
    }

    fun startEdit(vehicle: VehicleDto) {
        _form.value = VehicleForm(
            id = vehicle.id,
            name = vehicle.name,
            fuelType = vehicle.fuelType,
            consumption = vehicle.consumptionL100,
            tank = vehicle.tankL.orEmpty(),
        )
    }

    fun dismissForm() {
        _form.value = null
    }

    fun onName(value: String) = _form.update { it?.copy(name = value, nameError = null) }
    fun onFuel(value: String) = _form.update { it?.copy(fuelType = value) }
    fun onConsumption(value: String) = _form.update { it?.copy(consumption = value, consumptionError = null) }
    fun onTank(value: String) = _form.update { it?.copy(tank = value, tankError = null) }

    fun save() {
        val f = _form.value ?: return
        if (f.saving) return
        val nameError = Validators.vehicleName(f.name)
        val consumptionError = Validators.consumption(f.consumption)
        val tankError = if (f.tank.isBlank()) null else Validators.tank(f.tank)
        if (nameError != null || consumptionError != null || tankError != null) {
            _form.value = f.copy(nameError = nameError, consumptionError = consumptionError, tankError = tankError)
            return
        }
        val request = VehicleRequest(
            name = f.name.trim(),
            fuelType = f.fuelType,
            consumptionL100 = Validators.parseDecimal(f.consumption)!!.toPlainString(),
            tankL = if (f.tank.isBlank()) null else Validators.parseDecimal(f.tank)!!.toPlainString(),
        )
        _form.value = f.copy(saving = true)
        viewModelScope.launch {
            try {
                if (f.id == null) repository.add(request) else repository.update(f.id, request)
                _form.value = null
                refreshQuietly()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _form.update { it?.copy(saving = false) }
                notifier.show(errors.messageFor(e))
            }
        }
    }

    // ---- other actions --------------------------------------------------------------------------------------

    fun makeDefault(vehicle: VehicleDto) = perform { repository.setDefault(vehicle.id) }

    fun askDelete(vehicle: VehicleDto) {
        _toDelete.value = vehicle
    }

    fun cancelDelete() {
        _toDelete.value = null
    }

    fun confirmDelete() {
        val vehicle = _toDelete.value ?: return
        _toDelete.value = null
        perform { repository.delete(vehicle.id) }
    }

    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                refreshQuietly()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notifier.show(errors.messageFor(e))
            }
        }
    }

    private suspend fun refreshQuietly() {
        try {
            _list.value = Load.Ready(repository.list())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notifier.show(errors.messageFor(e))
        }
    }
}
