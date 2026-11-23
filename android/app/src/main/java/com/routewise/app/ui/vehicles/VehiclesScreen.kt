package com.routewise.app.ui.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.routewise.app.data.api.VehicleDto
import com.routewise.app.domain.Formatting
import com.routewise.app.ui.Load
import com.routewise.app.ui.components.ConfirmDialog
import com.routewise.app.ui.components.EmptyState
import com.routewise.app.ui.components.ErrorState
import com.routewise.app.ui.components.FormField
import com.routewise.app.ui.components.SectionCard
import com.routewise.app.ui.components.SkeletonList

@Composable
fun VehiclesScreen(viewModel: VehiclesViewModel) {
    val list by viewModel.list.collectAsState()
    val form by viewModel.form.collectAsState()
    val toDelete by viewModel.toDelete.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("My cars", style = MaterialTheme.typography.titleLarge)
            Button(onClick = viewModel::startAdd, modifier = Modifier.testTag("add_vehicle")) { Text("Add car") }
        }
        when (val state = list) {
            Load.Loading -> SkeletonList()
            is Load.Failed -> ErrorState(state.message, viewModel::load)
            is Load.Ready -> if (state.data.isEmpty()) {
                EmptyState("No cars yet", "Add your car to calculate trip costs.", "Add car", viewModel::startAdd)
            } else {
                LazyColumn(Modifier.fillMaxSize().testTag("vehicle_list"), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.data, key = { it.id }) { vehicle ->
                        VehicleCard(vehicle, viewModel)
                    }
                }
            }
        }
    }
    form?.let { VehicleFormDialog(it, viewModel) }
    toDelete?.let {
        ConfirmDialog(
            title = "Delete ${it.name}?",
            text = "Saved trips keep their figures. This car will no longer be available for new calculations.",
            confirmLabel = "Delete",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun VehicleCard(vehicle: VehicleDto, viewModel: VehiclesViewModel) {
    SectionCard(Modifier.testTag("vehicle_${vehicle.name}")) {
        Text(vehicle.name, style = MaterialTheme.typography.titleMedium)
        Text("${Formatting.fuelName(vehicle.fuelType)} · ${vehicle.consumptionL100} l/100 km" +
            (vehicle.tankL?.let { " · tank $it l" } ?: ""), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vehicle.isDefault) {
                Text("Default", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                TextButton(onClick = { viewModel.makeDefault(vehicle) }) { Text("Make default") }
            }
            TextButton(onClick = { viewModel.startEdit(vehicle) }) { Text("Edit") }
            TextButton(onClick = { viewModel.askDelete(vehicle) }) { Text("Delete") }
        }
    }
}

@Composable
private fun VehicleFormDialog(form: VehicleForm, viewModel: VehiclesViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissForm,
        title = { Text(if (form.id == null) "Add car" else "Edit car") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FormField(form.name, viewModel::onName, "Name", form.nameError, tag = "vehicle_name")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(form.fuelType == "petrol95", { viewModel.onFuel("petrol95") }, { Text("Petrol 95") })
                    FilterChip(form.fuelType == "diesel", { viewModel.onFuel("diesel") }, { Text("Diesel") })
                }
                FormField(form.consumption, viewModel::onConsumption, "Consumption, l/100 km", form.consumptionError, tag = "vehicle_consumption", keyboard = KeyboardType.Decimal)
                FormField(form.tank, viewModel::onTank, "Tank, l (optional)", form.tankError, tag = "vehicle_tank", keyboard = KeyboardType.Decimal)
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::save, enabled = !form.saving, modifier = Modifier.testTag("save_vehicle")) {
                Text(if (form.saving) "Saving…" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissForm) { Text("Cancel") } },
    )
}
