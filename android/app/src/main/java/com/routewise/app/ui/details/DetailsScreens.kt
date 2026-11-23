package com.routewise.app.ui.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.routewise.app.domain.Stops
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.routewise.app.core.Messages
import com.routewise.app.data.Calculation
import com.routewise.app.data.api.TrendPointDto
import com.routewise.app.domain.DetailsText
import com.routewise.app.domain.Formatting
import com.routewise.app.domain.TripText
import com.routewise.app.ui.Load
import com.routewise.app.ui.components.EmptyState
import com.routewise.app.ui.components.ErrorState
import com.routewise.app.ui.components.FormField
import com.routewise.app.ui.components.InfoBanner
import com.routewise.app.ui.components.LoadingButton
import com.routewise.app.ui.components.SectionCard
import com.routewise.app.ui.components.SkeletonList
import java.time.LocalDate

/** SCR-10: country, kilometers, average speed and motorway limit of the season. */
@Composable
fun RouteDetailsScreen(
    viewModel: RouteDetailsViewModel,
    onRefuel: () -> Unit,
    onTrend: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val calculation by viewModel.calculation.collectAsState()
    val rows by viewModel.rows.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    val trip = calculation?.selected ?: return
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Route details", style = MaterialTheme.typography.titleLarge)
        Text(
            "${Formatting.variantName(trip.variant)} · ${Formatting.km(trip.distanceKm)} · ${Formatting.duration(trip.durationMin)}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text("Season: ${DetailsText.season(today)}", style = MaterialTheme.typography.bodySmall)
        when (val state = rows) {
            Load.Loading -> SkeletonList(rows = 3, rowHeight = 56)
            is Load.Failed -> ErrorState(state.message, viewModel::load)
            is Load.Ready -> {
                state.data.forEach { row ->
                    SectionCard(Modifier.testTag("speed_${row.country}")) {
                        Text(row.title, style = MaterialTheme.typography.titleSmall)
                        Text(row.detail, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                InfoBanner(
                    "The travel time of each country is its distance divided by the average speed of the season. Limits are shown for information.",
                    tag = "speed_note",
                )
            }
        }
        NavRow("Refuel plan", "Where to buy fuel along the route", "open_refuel", onRefuel)
        NavRow("Fuel price trend", "Last 12 weeks by country", "open_trend", onTrend)
    }
}

@Composable
fun NavRow(title: String, subtitle: String, tag: String, onClick: () -> Unit) {
    SectionCard(Modifier.testTag(tag)) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Text("›")
        }
    }
}

/** SCR-11: the refuel plan of the calculation (the API returns it when the start level was given). */
@Composable
fun RefuelPlanScreen(calculation: Calculation?, onAddFuelLevel: () -> Unit) {
    val trip = calculation?.selected
    if (trip == null) {
        EmptyState("Nothing calculated yet", "Calculate a trip first.")
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Refuel plan", style = MaterialTheme.typography.titleLarge)
        Text(TripText.routeTitle(trip), style = MaterialTheme.typography.titleMedium)
        if (trip.refuelStops.isEmpty()) {
            InfoBanner(
                "No refuel plan for this trip. Set the fuel level at the start when planning, and make sure the car has a tank size.",
                tag = "refuel_empty",
            )
            OutlinedButton(onClick = onAddFuelLevel, modifier = Modifier.fillMaxWidth().testTag("refuel_back")) { Text("Back to planning") }
            return@Column
        }
        InfoBanner("Start fuel: ${startFuelText(trip.startFuel)}. Fuel prices of ${Formatting.date(trip.priceDate)}.", tag = "refuel_assumptions")
        SectionCard(Modifier.testTag("refuel_start")) {
            Text("A  Start · ${TripText.routeTitle(trip).substringBefore(" →")}", style = MaterialTheme.typography.titleSmall)
        }
        trip.refuelStops.forEach { stop ->
            SectionCard(Modifier.testTag("refuel_stop_${stop.seq}")) {
                Text(
                    "⛽ Refuel in ${Formatting.countryName(stop.country)}, km ${Formatting.km(stop.kmFromStart).removeSuffix(" km")}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text("+${stop.liters} l · ${stop.priceEurL} €/l", style = MaterialTheme.typography.bodyMedium)
            }
        }
        SectionCard(Modifier.testTag("refuel_end")) {
            Text("B  Arrive · ${TripText.routeTitle(trip).substringAfter("→ ")}", style = MaterialTheme.typography.titleSmall)
        }
        val saving = trip.refuelSavingEur?.toBigDecimalOrNull()
        if (saving != null && saving.signum() > 0) {
            InfoBanner("Tip: saves about ${Formatting.eur(trip.refuelSavingEur)} against refuelling in one country. The budget total does not change.", tag = "refuel_tip")
        } else {
            InfoBanner("Refuelling as usual is just as cheap. No saving found.", tag = "refuel_tip")
        }
        Text("Prices are national averages; station prices differ.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun startFuelText(value: String?) = when (value) {
    "full" -> "full tank"
    "three_quarter" -> "¾ tank"
    "half" -> "½ tank"
    "quarter" -> "¼ tank"
    else -> "not given"
}

/** SCR-12: chips for the countries of the route, a line of the weekly prices and the same values as text. */
@Composable
fun FuelTrendScreen(viewModel: FuelTrendViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.start() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Fuel price trend", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.countries.forEach { code ->
                FilterChip(
                    selected = code == state.country,
                    onClick = { viewModel.select(code) },
                    label = { Text(Formatting.countryName(code)) },
                    modifier = Modifier.testTag("trend_$code"),
                )
            }
        }
        Text(
            "${Formatting.fuelName(state.fuelType)} · ${state.country?.let { Formatting.countryName(it) } ?: ""} · EUR per liter",
            style = MaterialTheme.typography.titleSmall,
        )
        when (val points = state.points) {
            Load.Loading -> SkeletonList(rows = 4, rowHeight = 40)
            is Load.Failed -> ErrorState(points.message) { state.country?.let(viewModel::select) }
            is Load.Ready -> if (points.data.isEmpty()) {
                InfoBanner(Messages.NO_PRICE_HISTORY, tag = "trend_empty")
            } else {
                TrendChart(points.data)
                DetailsText.trendNote(points.data)?.let { InfoBanner(it, tag = "trend_few") }
                Column(Modifier.testTag("trend_list"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DetailsText.trendRows(points.data).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.date, style = MaterialTheme.typography.bodyMedium)
                            Text(row.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Text(
            "Source: EC Weekly Oil Bulletin, national averages. The trend is informational and does not change the budget.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TrendChart(points: List<TrendPointDto>) {
    val values = points.map { it.priceEurL.toFloatOrNull() ?: 0f }
    if (values.size < 2) return
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(120.dp).testTag("trend_chart")) {
        val min = values.min()
        val span = (values.max() - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val point = Offset(i * stepX, size.height - (v - min) / span * size.height)
            if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, color, style = Stroke(width = 4f))
    }
}

/** SCR-05: lodging class and the price the user found on a booking site; the budget is calculated again. */
@Composable
fun LodgingScreen(viewModel: LodgingViewModel) {
    val state by viewModel.state.collectAsState()
    val calculation by viewModel.calculation.collectAsState()
    val trip = calculation?.selected ?: return
    val lodging = trip.lines.firstOrNull { it.kind == "lodging" }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Overnight stop", style = MaterialTheme.typography.titleLarge)
        Text(
            "${trip.nights} night${if (trip.nights == 1) "" else "s"} on the way to ${TripText.routeTitle(trip).substringAfter("→ ")}",
            style = MaterialTheme.typography.titleMedium,
        )
        val stop = Stops.firstStop(calculation!!.request, trip)
        val uriHandler = LocalUriHandler.current
        var linkFailed by remember { mutableStateOf(false) }
        if (stop != null) {
            SectionCard(Modifier.testTag("stop_card")) {
                Text("☾ ${stop.place.label}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Night 1 of ${trip.nights} · after ${Formatting.duration(stop.afterMinutes)} of driving (about km ${stop.km})",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Check-in ${stop.checkIn.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH))} · check-out ${stop.checkOut.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH))}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("stop_dates"))
                Text("The city is estimated on the straight line to the destination.", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Guests", style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedButton(onClick = { viewModel.changeGuests(-1) }, modifier = Modifier.testTag("guests_minus")) { Text("−") }
                    Text("${state.guests}", modifier = Modifier.padding(horizontal = 16.dp).testTag("guests"))
                    OutlinedButton(onClick = { viewModel.changeGuests(1) }, modifier = Modifier.testTag("guests_plus")) { Text("+") }
                }
            }
            OutlinedButton(
                onClick = {
                    try {
                        uriHandler.openUri(Stops.bookingUrl(stop, state.guests))
                        linkFailed = false
                    } catch (e: Exception) {
                        linkFailed = true
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("search_hotels"),
            ) { Text("Search accommodation ↗") }
            Text("Opens Booking.com in your browser with the city and dates. Nothing is booked in RouteWise.", style = MaterialTheme.typography.bodySmall)
            if (linkFailed) InfoBanner("Could not open the browser. Copy the link instead:\n${Stops.bookingUrl(stop, state.guests)}", warning = true, tag = "link_failed")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("economy" to "Economy", "standard" to "Standard", "comfort" to "Comfort").forEach { (value, label) ->
                FilterChip(
                    selected = state.lodgingClass == value,
                    onClick = { viewModel.chooseClass(value) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("class_$value"),
                )
            }
        }
        FormField(
            value = state.price,
            onValueChange = viewModel::onPrice,
            label = "Actual price from the booking site, EUR (all nights)",
            error = state.error,
            tag = "lodging_price",
            keyboard = KeyboardType.Decimal,
        )
        LoadingButton("Apply price", state.busy, viewModel::apply, tag = "apply_price")
        SectionCard(Modifier.testTag("lodging_result")) {
            Text(
                if (lodging?.status == "user_entered") "Lodging · entered by you" else "Lodging · estimate",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(Formatting.eur(lodging?.amountEur), style = MaterialTheme.typography.titleMedium)
            Text("New total: ${Formatting.eur(trip.totalEur)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("lodging_total"))
        }
        if (lodging?.status == "user_entered") {
            OutlinedButton(onClick = viewModel::useEstimate, modifier = Modifier.fillMaxWidth().testTag("use_estimate")) { Text("Use estimate again") }
        }
        Text("Enter the price you found for all nights; the total is calculated again.", style = MaterialTheme.typography.bodySmall)
    }
}
