package com.astrix.badge

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BudgetScreen(vm: BadgeViewModel) {
    val budgets by vm.budgets.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val categories by vm.categories.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Button(onClick = { showDialog = true }) { Text("+ New budget") } }
        items(budgets, key = { it.id }) { b ->
            val start = Periods.startOf(b.period)
            val spent = expenses.filter {
                it.timestamp >= start && (b.categoryId == null || it.categoryId == b.categoryId)
            }.sumOf { it.amount }
            val ratio = (spent / b.limitAmount).toFloat().coerceIn(0f, 1f)
            val over = spent > b.limitAmount
            val name = categories.firstOrNull { it.id == b.categoryId }?.name ?: "All spending"

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name + " · " + b.period.lowercase(),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { vm.deleteBudget(b) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(money(spent) + " of " + money(b.limitAmount))
                }
            }
        }
    }

    if (showDialog) {
        AddBudgetDialog(
            categories = categories,
            onDismiss = { showDialog = false },
            onSave = { p, c, a -> vm.addBudget(p, c, a); showDialog = false }
        )
    }
}

@Composable
fun AddBudgetDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (String, Long?, Double) -> Unit
) {
    var period by remember { mutableStateOf("MONTH") }
    var cat by remember { mutableStateOf<Long?>(null) }
    var amount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DAY", "WEEK", "MONTH", "YEAR").forEach { p ->
                        FilterChip(period == p, { period = p }, { Text(p.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                CategoryChips(categories, cat, anyLabel = "All") { cat = it }
                AmountField(amount, { amount = it }, label = "Limit")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { onSave(period, cat, it) }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}