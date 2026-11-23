package com.routewise.app.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.routewise.app.data.api.CostLineDto
import com.routewise.app.data.api.TripDto
import com.routewise.app.domain.Formatting
import com.routewise.app.domain.TripText
import com.routewise.app.ui.components.SectionCard
import androidx.compose.ui.unit.dp

/** One cost row: title, a line that explains the figure, and the amount ("no data" is never shown as zero). */
@Composable
private fun CostRow(line: CostLineDto, trip: TripDto) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(TripText.lineTitle(line, trip.nights), style = MaterialTheme.typography.bodyLarge)
            TripText.lineDetail(line, trip.destinationLabel)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            if (line.status == "no_data") "no data" else Formatting.eur(line.amountEur),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** The budget card shared by the Budget screen (SCR-04) and the trip detail (SCR-06): the rows and the total. */
@Composable
fun CostCard(trip: TripDto) {
    SectionCard(Modifier.testTag("cost_card")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            trip.lines.forEach { CostRow(it, trip) }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.titleMedium)
                Text(Formatting.eur(trip.totalEur), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("total"))
            }
        }
    }
}

/** The refuel plan returned by the API when the start fuel level was given. */
@Composable
fun RefuelCard(trip: TripDto) {
    if (trip.refuelStops.isEmpty()) return
    SectionCard(Modifier.testTag("refuel_card")) {
        Text("Refuel plan", style = MaterialTheme.typography.titleMedium)
        trip.refuelStops.forEach { stop ->
            Text(
                "${stop.seq}. ${Formatting.countryName(stop.country)} · km ${Formatting.km(stop.kmFromStart).removeSuffix(" km")} · " +
                    "${stop.liters} l · ${stop.priceEurL} €/l",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        trip.refuelSavingEur?.let { Text("Saving against refuelling in one country: ${Formatting.eur(it)}", style = MaterialTheme.typography.bodySmall) }
    }
}
