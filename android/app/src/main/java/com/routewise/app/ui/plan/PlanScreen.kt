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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.routewise.app.core.Messages
import com.routewise.app.domain.Place
import com.routewise.app.domain.Validators
import com.routewise.app.ui.components.FormField
import com.routewise.app.ui.components.LoadingButton

@Composable
fun PlanScreen(viewModel: PlanViewModel, onCalculated: () -> Unit, onAddCar: () -> Unit) {
    val s by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshVehicles() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Plan a trip", style = MaterialTheme.typography.titleLarge)
        if (s.hasVehicle == false) {
            Text(Messages.NO_CAR, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("no_car"))
            FilterChip(false, onAddCar, { Text("Add car") })
        }
        PlaceInput("From", s.origin, "origin", viewModel::onOriginQuery, viewModel::pickOrigin)
        PlaceInput("To", s.destination, "destination", viewModel::onDestinationQuery, viewModel::pickDestination)

        Text("Maximum driving per day: ${s.dailyLimitH} h", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = s.dailyLimitH.toFloat(),
            onValueChange = { viewModel.onLimit(it.toInt()) },
            valueRange = Validators.MIN_DAILY_LIMIT_H.toFloat()..Validators.MAX_DAILY_LIMIT_H.toFloat(),
            steps = Validators.MAX_DAILY_LIMIT_H - Validators.MIN_DAILY_LIMIT_H - 1,
            modifier = Modifier.testTag("daily_limit"),
        )
        Text("Overnight stay", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("economy" to "Economy", "standard" to "Standard", "comfort" to "Comfort").forEach { (value, label) ->
                FilterChip(s.lodgingClass == value, { viewModel.onLodging(value) }, { Text(label) })
            }
        }
        s.formError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("plan_error")) }
        Spacer(Modifier.height(8.dp))
        LoadingButton("Calculate cost", s.calculating, { viewModel.calculate(onCalculated) }, tag = "calculate")
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
        FormField(field.query, onQuery, label, field.error, tag = tag)
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
