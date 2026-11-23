package com.routewise.app.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.routewise.app.data.api.CostLineDto
import com.routewise.app.data.api.TripDto
import com.routewise.app.domain.Formatting
import com.routewise.app.ui.components.LabelValue
import com.routewise.app.ui.components.SectionCard

/** Text of one cost line: what it is and why the figure may be approximate (never hide an estimate). */
fun lineTitle(line: CostLineDto): String {
    val place = Formatting.countryName(line.country)
    return when (line.kind) {
        "fuel" -> "Fuel, $place"
        "road" -> "Road fee, $place"
        "lodging" -> "Overnight stay"
        else -> line.kind
    }
}

fun lineNote(line: CostLineDto): String? = when (line.status) {
    "estimate" -> "estimate"
    "eu_average" -> "EU average price"
    "no_data" -> "no data"
    "user_entered" -> "entered by you"
    else -> null
}

/** The cost breakdown shared by the Budget screen (SCR-04) and the trip detail (SCR-06). */
@Composable
fun TripBreakdown(trip: TripDto) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(Modifier.testTag("total_card")) {
            Text("Total", style = MaterialTheme.typography.labelLarge)
            Text(Formatting.eur(trip.totalEur), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("total"))
            Text(
                "${Formatting.km(trip.distanceKm)} · ${Formatting.duration(trip.durationMin)}" +
                    if (trip.nights > 0) " · ${trip.nights} night(s)" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("${trip.vehicleName}, ${trip.consumptionL100} l/100 km", style = MaterialTheme.typography.bodySmall)
        }
        SectionCard {
            Text("Costs", style = MaterialTheme.typography.titleMedium)
            trip.lines.forEach { line ->
                val note = lineNote(line)
                LabelValue(
                    lineTitle(line) + (note?.let { " ($it)" } ?: ""),
                    if (line.status == "no_data") "-" else Formatting.eur(line.amountEur),
                )
            }
        }
        SectionCard {
            Text("Prices from ${Formatting.date(trip.priceDate)}. Fuel prices change; treat the total as an estimate.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
