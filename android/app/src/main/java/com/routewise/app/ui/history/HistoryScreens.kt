package com.routewise.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.routewise.app.data.api.TripDto
import com.routewise.app.domain.Formatting
import com.routewise.app.domain.TripText
import com.routewise.app.ui.Load
import com.routewise.app.ui.budget.CostCard
import com.routewise.app.ui.budget.RefuelCard
import com.routewise.app.ui.components.ConfirmDialog
import com.routewise.app.ui.components.EmptyState
import com.routewise.app.ui.components.ErrorState
import com.routewise.app.ui.components.InfoBanner
import com.routewise.app.ui.components.SectionCard
import com.routewise.app.ui.components.SkeletonList

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onOpen: (String) -> Unit, onPlan: () -> Unit) {
    val list by viewModel.list.collectAsState()
    val toDelete by viewModel.toDelete.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    Column(Modifier.fillMaxSize()) {
        Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        when (val state = list) {
            Load.Loading -> SkeletonList()
            is Load.Failed -> ErrorState(state.message, viewModel::load)
            is Load.Ready -> if (state.data.isEmpty()) {
                EmptyState("No trips yet", "Calculate a trip and it appears here automatically.", "Plan a trip", onPlan)
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().testTag("trip_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.data, key = { it.id }) { trip -> TripRow(trip, onOpen, viewModel::askDelete) }
                }
            }
        }
    }
    toDelete?.let {
        ConfirmDialog(
            title = "Delete this trip?",
            text = "“${TripText.routeTitle(it)}” will be removed permanently. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

/** A trip row. Swiping it to the left reveals Delete, which asks for confirmation (UX SCR-06). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripRow(trip: TripDto, onOpen: (String) -> Unit, onAskDelete: (TripDto) -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onAskDelete(trip)
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp)).padding(16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Text("Delete", color = MaterialTheme.colorScheme.onError) }
        },
    ) {
        SectionCard(Modifier.clickable { onOpen(trip.id) }.testTag("trip_${trip.id}")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(TripText.routeTitle(trip), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${Formatting.date(trip.createdAt ?: trip.departedAt)} · ${Formatting.km(trip.distanceKm)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(Formatting.eur(trip.totalEur), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun TripDetailScreen(viewModel: TripDetailViewModel, onDeleted: () -> Unit) {
    val trip by viewModel.trip.collectAsState()
    val confirm by viewModel.confirmDelete.collectAsState()
    when (val state = trip) {
        Load.Loading -> SkeletonList(rows = 3, rowHeight = 120)
        is Load.Failed -> ErrorState(state.message, viewModel::load)
        is Load.Ready -> {
            val t = state.data
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Trip detail", style = MaterialTheme.typography.titleLarge)
                Text(TripText.routeTitle(t), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${Formatting.date(t.createdAt ?: t.departedAt)} · ${Formatting.variantName(t.variant)} · " +
                        "${Formatting.km(t.distanceKm)} · ${Formatting.duration(t.durationMin)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                InfoBanner(
                    "Saved with fuel prices of ${Formatting.date(t.priceDate)}. Car: ${t.vehicleName}, " +
                        "${Formatting.fuelName(t.fuelType)}, ${t.consumptionL100} l/100 km.",
                    tag = "saved_with",
                )
                CostCard(t)
                RefuelCard(t)
                OutlinedButton(onClick = viewModel::askDelete, modifier = Modifier.fillMaxWidth().testTag("delete_trip")) {
                    Text("Delete trip", color = MaterialTheme.colorScheme.error)
                }
            }
            if (confirm) {
                ConfirmDialog(
                    title = "Delete this trip?",
                    text = "“${TripText.routeTitle(t)}” will be removed permanently. This cannot be undone.",
                    confirmLabel = "Delete",
                    onConfirm = { viewModel.delete(onDeleted) },
                    onDismiss = viewModel::cancelDelete,
                )
            }
        }
    }
}
