package com.astrix.badge

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

@Composable
fun BudgetScreen(vm: BadgeViewModel, onDelete: (Budget) -> Unit) {
    val tour = LocalTourTargets.current
    val selection = LocalSelection.current
    val haptics = LocalHapticFeedback.current
    val budgets by vm.budgets.collectAsState()
    val categories by vm.categories.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Budget?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = { showAdd = true },
                modifier = Modifier.tourTarget(tour, TourTarget.NEW_BUDGET)
            ) { Text("+ New budget") }
        }
        items(budgets, key = { it.id }) { b ->
            val spent by remember(b.period, b.categoryId) {
                vm.spentFlow(b.period, b.categoryId)
            }.collectAsState(initial = 0.0)
            val ratio = (spent / b.limitAmount).toFloat().coerceIn(0f, 1f)
            val over = spent > b.limitAmount
            val catName = categories.firstOrNull { it.id == b.categoryId }?.display() ?: "All spending"
            val detail = catName + " · " + b.period.lowercase()
            val selected = selection.isSelected(b)
            val selecting = selection.active

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(b, selecting) {
                        detectTapGestures(
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                selection.toggle(b)
                            },
                            onTap = { if (selecting) selection.toggle(b) }
                        )
                    },
                colors = if (selected) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    CardDefaults.cardColors()
                }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                b.name.ifBlank { detail },
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (b.name.isNotBlank()) {
                                Text(
                                    detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (selecting) {
                            Checkbox(checked = selected, onCheckedChange = null)
                        } else {
                            IconButton(onClick = { editing = b }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { onDelete(b) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
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

    if (showAdd) {
        BudgetDialog(
            title = "New budget",
            initial = null,
            categories = categories,
            onDismiss = { showAdd = false },
            onSave = { n, p, c, a -> vm.addBudget(n, p, c, a); showAdd = false }
        )
    }

    editing?.let { b ->
        BudgetDialog(
            title = "Edit budget",
            initial = b,
            categories = categories,
            onDismiss = { editing = null },
            onSave = { n, p, c, a ->
                vm.updateBudget(b.copy(name = n, period = p, categoryId = c, limitAmount = a))
                editing = null
            }
        )
    }
}

@Composable
fun BudgetDialog(
    title: String,
    initial: Budget?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (String, String, Long?, Double) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var period by remember { mutableStateOf(initial?.period ?: "MONTH") }
    var cat by remember { mutableStateOf(initial?.categoryId) }
    var amount by remember { mutableStateOf(initial?.let { plainAmount(it.limitAmount) } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("DAY", "WEEK", "MONTH", "YEAR").forEach { p ->
                            FilterChip(
                                period == p,
                                { period = p },
                                { Text(p.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    CategoryChips(categories, cat, anyLabel = "All") { cat = it }
                }
                AmountField(amount, { amount = it }, Modifier.fillMaxWidth(), label = "Limit")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { onSave(name.trim(), period, cat, it) }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}