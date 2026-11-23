package com.routewise.app.ui.budget

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.routewise.app.core.Messages
import com.routewise.app.data.Calculation
import com.routewise.app.domain.Formatting
import com.routewise.app.domain.TripText
import com.routewise.app.ui.components.EmptyState
import com.routewise.app.ui.components.InfoBanner
import com.routewise.app.ui.components.SkeletonBox
import java.time.LocalDate

@Composable
fun BudgetScreen(viewModel: BudgetViewModel, onPlan: () -> Unit, today: LocalDate = LocalDate.now()) {
    val calculation by viewModel.calculation.collectAsState()
    val switching by viewModel.switching.collectAsState()
    val current = calculation
    if (current == null) {
        EmptyState("Nothing calculated yet", "Choose a start and a destination first.", "Plan a trip", onPlan)
        return
    }
    val trip = current.selected
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Trip budget", style = MaterialTheme.typography.titleLarge)
        Text(TripText.routeTitle(trip), style = MaterialTheme.typography.titleMedium)
        VariantChips(current, switching, viewModel::select)
        if (Calculation.NO_TOLLS in current.unavailable) {
            InfoBanner(Messages.NO_TOLLS_UNAVAILABLE, tag = "no_tolls_unavailable")
        }
        if (switching) {
            SkeletonBox(Modifier.fillMaxWidth().height(240.dp).testTag("switching"))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${Formatting.km(trip.distanceKm)} · ${Formatting.duration(trip.durationMin)}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag("distance_time"),
                )
                TripText.kmByCountry(trip).takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                TripText.arrivalLine(trip)?.let { Text("🕑 $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("arrival")) }
                TripText.lateArrivalNote(trip)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (TripText.isPriceOld(trip.priceDate, today)) {
                    InfoBanner(Messages.pricesOld(Formatting.date(trip.priceDate)), warning = true, tag = "prices_old")
                } else {
                    Text("ⓘ Fuel prices of ${Formatting.date(trip.priceDate)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("price_date"))
                }
                Text("✓ Saved to History", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("saved_badge"))
                CostCard(trip)
                RefuelCard(trip)
            }
        }
    }
}

@Composable
private fun VariantChips(current: Calculation, disabled: Boolean, onSelect: (String) -> Unit) {
    val cheapest = current.cheapestVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        current.summaries.values.forEach { summary ->
            val deltaEur = current.deltaEur(summary.variant)
            val deltaMin = current.deltaMinutes(summary.variant)
            FilterChip(
                selected = summary.variant == current.selected.variant,
                onClick = { if (!disabled) onSelect(summary.variant) },
                label = {
                    Column {
                        Text(Formatting.variantName(summary.variant))
                        Text(Formatting.eur(summary.totalEur.toPlainString()), style = MaterialTheme.typography.bodySmall)
                        if (summary.variant == Calculation.FASTEST) {
                            Text("fastest = reference", style = MaterialTheme.typography.bodySmall)
                        } else if (deltaEur != null && deltaMin != null) {
                            Text("${Formatting.signedAmount(deltaEur)} · ${Formatting.signedMinutes(deltaMin)}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (summary.variant == cheapest) {
                            Text("✓ Cheapest", style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("cheapest_badge"))
                        }
                    }
                },
                modifier = Modifier.testTag("variant_${summary.variant}"),
            )
        }
    }
}
