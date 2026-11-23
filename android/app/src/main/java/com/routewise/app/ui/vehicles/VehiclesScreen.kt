package com.routewise.app.ui.vehicles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.routewise.app.ui.components.InfoBanner
import com.routewise.app.ui.components.LoadingButton
import com.routewise.app.ui.components.SectionCard
import com.routewise.app.ui.components.SkeletonList

@Composable
fun VehiclesScreen(viewModel: VehiclesViewModel, onFirstCarSaved: () -> Unit) {
    val list by viewModel.list.collectAsState()
    val form by viewModel.form.collectAsState()
    val toDelete by viewModel.toDelete.collectAsState()

    BackHandler(enabled = form != null) { viewModel.dismissForm() }

    val currentForm = form
    if (currentForm != null) {
        VehicleFormScreen(currentForm, viewModel, onFirstCarSaved)
    } else {
        Column(Modifier.fillMaxSize()) {
            Text("Vehicles", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            Box(Modifier.weight(1f)) {
                when (val state = list) {
                    Load.Loading -> SkeletonList()
                    is Load.Failed -> ErrorState(state.message, viewModel::load)
                    is Load.Ready -> if (state.data.isEmpty()) {
                        EmptyState("No cars yet", "Add your car to calculate trip costs.")
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize().testTag("vehicle_list"),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.data, key = { it.id }) { vehicle -> VehicleRow(vehicle, viewModel) }
                        }
                    }
                }
            }
            Button(
                onClick = viewModel::startAdd,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp).testTag("add_vehicle"),
            ) { Text("＋ Add car") }
        }
    }
    toDelete?.let {
        ConfirmDialog(
            title = "Delete “${it.name}”?",
            text = "Saved trips keep their values. You need a car to calculate new trips.",
            confirmLabel = "Delete",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun VehicleRow(vehicle: VehicleDto, viewModel: VehiclesViewModel) {
    SectionCard(Modifier.clickable { viewModel.startEdit(vehicle) }.testTag("vehicle_${vehicle.name}")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(vehicle.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${Formatting.fuelName(vehicle.fuelType)} · ${vehicle.consumptionL100} l/100 km" +
                        (vehicle.tankL?.let { " · ${it.removeSuffix(".0")} l tank" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (vehicle.isDefault) {
                Text("Default", color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("default_${vehicle.name}"))
            } else {
                TextButton(onClick = { viewModel.makeDefault(vehicle) }, modifier = Modifier.testTag("set_default_${vehicle.name}")) {
                    Text("Set default")
                }
            }
        }
    }
}

@Composable
private fun VehicleFormScreen(form: VehicleForm, viewModel: VehiclesViewModel, onFirstCarSaved: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = viewModel::dismissForm, modifier = Modifier.testTag("form_back")) { Text("← Back") }
        }
        Text(
            if (form.id == null) "Add your car" else "Edit car",
            style = MaterialTheme.typography.titleLarge,
        )
        if (form.first) {
            InfoBanner("Add your car first. Fuel cost is calculated from your car’s type and average consumption.", tag = "first_car_info")
        }
        form.banner?.let { InfoBanner(it, warning = true, tag = "not_saved_banner") }
        FormField(
            form.name, viewModel::onName, "Name", form.nameError, tag = "vehicle_name",
            placeholder = "For example, My Skoda", onBlur = viewModel::onNameBlur,
        )
        Text("Fuel type", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(form.fuelType == "petrol95", { viewModel.onFuel("petrol95") }, { Text("Petrol 95") }, modifier = Modifier.testTag("fuel_petrol"))
            FilterChip(form.fuelType == "diesel", { viewModel.onFuel("diesel") }, { Text("Diesel") }, modifier = Modifier.testTag("fuel_diesel"))
        }
        FormField(
            form.consumption, viewModel::onConsumption, "Average consumption, l/100 km", form.consumptionError,
            tag = "vehicle_consumption", keyboard = KeyboardType.Decimal,
            supporting = "2.0 – 30.0. Use your fuel receipts.", onBlur = viewModel::onConsumptionBlur,
        )
        FormField(
            form.tank, viewModel::onTank, "Tank size, l (optional)", form.tankError,
            tag = "vehicle_tank", keyboard = KeyboardType.Decimal,
            supporting = "For the refuel plan. Allowed 20 – 120 l.", onBlur = viewModel::onTankBlur,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Use as default car")
            Switch(checked = form.isDefault, onCheckedChange = viewModel::onDefault, modifier = Modifier.testTag("vehicle_default"))
        }
        LoadingButton("Save car", form.saving, { viewModel.save(onFirstCarSaved) }, tag = "save_vehicle")
        if (form.id != null) {
            OutlinedButton(onClick = viewModel::askDeleteFromForm, modifier = Modifier.fillMaxWidth().testTag("delete_vehicle")) {
                Text("Delete car", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
