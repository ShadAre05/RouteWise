package com.routewise.app.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.routewise.app.core.Messages
import com.routewise.app.domain.Formatting
import com.routewise.app.domain.Place
import com.routewise.app.domain.Validators
import com.routewise.app.ui.components.FormField
import com.routewise.app.ui.components.InfoBanner
import com.routewise.app.ui.components.LoadingButton

@Composable
fun PlanScreen(viewModel: PlanViewModel, onCalculated: () -> Unit, onAddCar: () -> Unit) {
    val s by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshVehicles() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Plan trip", style = MaterialTheme.typography.titleLarge)
        if (s.hasVehicle == false) {
            InfoBanner(Messages.NO_CAR, actionLabel = "Add car", onAction = onAddCar, tag = "no_car")
        }
        PlaceInput("From", s.origin, "origin", viewModel::onOriginQuery, viewModel::pickOrigin)
        PlaceInput("To", s.destination, "destination", viewModel::onDestinationQuery, viewModel::pickDestination)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Daily driving limit", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { viewModel.stepLimit(-1) },
                    enabled = s.dailyLimitH > Validators.MIN_DAILY_LIMIT_H,
                    modifier = Modifier.testTag("limit_minus"),
                ) { Text("−") }
                Text("${s.dailyLimitH} h", modifier = Modifier.padding(horizontal = 12.dp).testTag("limit_value"))
                OutlinedButton(
                    onClick = { viewModel.stepLimit(1) },
                    enabled = s.dailyLimitH < Validators.MAX_DAILY_LIMIT_H,
                    modifier = Modifier.testTag("limit_plus"),
                ) { Text("+") }
            }
        }

        if (s.showFuelAtStart) {
            Text("Fuel at start", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("full" to "Full", "three_quarter" to "¾", "half" to "½", "quarter" to "¼").forEach { (value, label) ->
                    FilterChip(s.startFuel == value, { viewModel.onFuelAtStart(value) }, { Text(label) }, modifier = Modifier.testTag("fuel_$value"))
                }
            }
        }

        Text("Overnight stay", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("economy" to "Economy", "standard" to "Standard", "comfort" to "Comfort").forEach { (value, label) ->
                FilterChip(s.lodgingClass == value, { viewModel.onLodging(value) }, { Text(label) }, modifier = Modifier.testTag("lodging_$value"))
            }
        }

        CarSelector(s, viewModel)

        s.formError?.let { InfoBanner(it, warning = true, tag = "plan_error") }
        s.failure?.let { failure ->
            Column(Modifier.fillMaxWidth().testTag("failure"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(failure.title, style = MaterialTheme.typography.titleMedium)
                Text(failure.message, color = MaterialTheme.colorScheme.error)
                Text("Your trip points are kept.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        LoadingButton(
            text = if (s.failure != null) "Try again" else "Calculate",
            loading = s.calculating,
            onClick = { viewModel.calculate(onCalculated) },
            tag = "calculate",
            enabled = s.canCalculate,
        )
        if (s.calculating) {
            Text(
                "Calculating…" + if (s.slow) " ${Messages.SLOW}" else "",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("calculating"),
            )
        } else {
            s.hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("calculate_hint")) }
        }
    }
}

@Composable
private fun CarSelector(s: PlanState, viewModel: PlanViewModel) {
    val vehicle = s.vehicle ?: return
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag("car_selector")) {
            Text("⛟ ${vehicle.name} · ${Formatting.fuelName(vehicle.fuelType)} · ${vehicle.consumptionL100} l/100 km ▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            s.vehicles.forEach { v ->
                DropdownMenuItem(
                    text = { Text("${v.name} · ${Formatting.fuelName(v.fuelType)} · ${v.consumptionL100} l/100 km") },
                    onClick = {
                        viewModel.onVehicle(v.id)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaceInput(
    label: String,
    field: PlaceField,
    tag: String,
    onQuery: (String) -> Unit,
    onPick: (Place) -> Unit,
) {
    Column {
        FormField(
            field.query, onQuery, label, field.error, tag = tag,
            placeholder = if (tag == "origin") "Search a city" else "Search a city (at least 3 characters)",
        )
        if (field.selected == null) {
            field.suggestions.take(5).forEach { place ->
                Text(
                    place.label,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(place) }.padding(horizontal = 8.dp, vertical = 12.dp)
                        .testTag("${tag}_option_${place.name}"),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
