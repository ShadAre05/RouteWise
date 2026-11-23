package com.routewise.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.routewise.app.domain.Formatting
import com.routewise.app.ui.Load
import com.routewise.app.ui.budget.TripBreakdown
import com.routewise.app.ui.components.ConfirmDialog
import com.routewise.app.ui.components.EmptyState
import com.routewise.app.ui.components.ErrorState
import com.routewise.app.ui.components.SectionCard
import com.routewise.app.ui.components.SkeletonList

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onOpen: (String) -> Unit, onPlan: () -> Unit) {
    val list by viewModel.list.collectAsState()
    val toDelete by viewModel.toDelete.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    Column(Modifier.fillMaxSize()) {
        Text("Trip history", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        when (val state = list) {
            Load.Loading -> SkeletonList()
            is Load.Failed -> ErrorState(state.message, viewModel::load)
            is Load.Ready -> if (state.data.isEmpty()) {
                EmptyState("No saved trips", "Every calculation you make is saved here.", "Plan a trip", onPlan)
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().testTag("trip_list"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.data, key = { it.id }) { trip ->
                        SectionCard(Modifier.clickable { onOpen(trip.id) }.testTag("trip_${trip.id}")) {
                            Text(trip.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${Formatting.date(trip.createdAt ?: trip.departedAt)} · ${Formatting.km(trip.distanceKm)} · ${Formatting.variantName(trip.variant)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(Formatting.eur(trip.totalEur), style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { viewModel.askDelete(trip) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
    toDelete?.let {
        ConfirmDialog(
            title = "Delete this trip?",
            text = "${it.title} will be removed from your history.",
            confirmLabel = "Delete",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
fun TripDetailScreen(viewModel: TripDetailViewModel) {
    val trip by viewModel.trip.collectAsState()
    when (val state = trip) {
        Load.Loading -> SkeletonList(rows = 3, rowHeight = 120)
        is Load.Failed -> ErrorState(state.message, viewModel::load)
        is Load.Ready -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.data.title, style = MaterialTheme.typography.titleLarge)
            TripBreakdown(state.data)
        }
    }
}
