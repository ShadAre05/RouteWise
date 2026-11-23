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
import com.routewise.app.data.Calculation
import com.routewise.app.domain.Formatting
import com.routewise.app.ui.components.EmptyState
import com.routewise.app.ui.components.SkeletonBox

@Composable
fun BudgetScreen(viewModel: BudgetViewModel, onPlan: () -> Unit) {
    val calculation by viewModel.calculation.collectAsState()
    val switching by viewModel.switching.collectAsState()
    val current = calculation
    if (current == null) {
        EmptyState("Nothing calculated yet", "Choose a start and a destination first.", "Plan a trip", onPlan)
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(current.selected.title, style = MaterialTheme.typography.titleLarge)
        VariantChips(current, switching, viewModel::select)
        if (switching) {
            SkeletonBox(Modifier.fillMaxWidth().height(240.dp).testTag("switching"))
        } else {
            TripBreakdown(current.selected)
        }
    }
}

@Composable
private fun VariantChips(current: Calculation, disabled: Boolean, onSelect: (String) -> Unit) {
    val cheapest = current.cheapestVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Route", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            current.summaries.values.forEach { summary ->
                val deltaEur = current.deltaEur(summary.variant)
                val deltaMin = current.deltaMinutes(summary.variant)
                FilterChip(
                    selected = summary.variant == current.selected.variant,
                    onClick = { if (!disabled) onSelect(summary.variant) },
                    label = {
                        Column {
                            Text(Formatting.variantName(summary.variant) + if (summary.variant == cheapest) " ★" else "")
                            Text(Formatting.eur(summary.totalEur.toPlainString()), style = MaterialTheme.typography.bodySmall)
                            if (summary.variant != Calculation.FASTEST && deltaEur != null && deltaMin != null) {
                                Text("${Formatting.signedEur(deltaEur)} · ${Formatting.signedMinutes(deltaMin)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    modifier = Modifier.testTag("variant_${summary.variant}"),
                )
            }
        }
    }
}
