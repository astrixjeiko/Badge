package com.astrix.badge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        content = content
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(vm: BadgeViewModel, onSeeAll: () -> Unit, onDelete: (Expense) -> Unit) {
    val recent by vm.recent.collectAsState()
    val today by vm.todayTotal.collectAsState()
    val week by vm.weekTotal.collectAsState()
    val month by vm.monthTotal.collectAsState()
    val categories by vm.categories.collectAsState()
    val quick by vm.quickButtons.collectAsState()
    var text by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Expense?>(null) }

    LazyColumn(contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp)) {
        // Summary card
        item {
            Section {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Spent today", style = MaterialTheme.typography.labelLarge)
                        Text(
                            money(today),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Text("Week: " + money(week))
                            Text("Month: " + money(month))
                        }
                    }
                }
            }
        }

        // Quick-add buttons
        item {
            Section {
                Text("Quick add", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (quick.isEmpty()) Text("No buttons yet. Add some in Settings.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { b ->
                        val icon = categories.firstOrNull { it.id == b.categoryId }?.icon.orEmpty()
                        AssistChip(
                            onClick = { vm.addExpense(b.amount, b.categoryId, b.label) },
                            label = {
                                Text((if (icon.isBlank()) "" else "$icon ") + b.label + " " + money(b.amount))
                            }
                        )
                    }
                }
            }
        }

        // Custom amount
        item {
            Section {
                Text("Custom amount", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                CategoryChips(categories, cat) { cat = it }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Name (e.g. Lunch)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    AmountField(text, { text = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                        Button(onClick = {
                            text.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                vm.addExpense(it, cat, note.trim())
                                text = ""
                                note = ""
                            }
                        }) { Text("Add") }
                    }
                }
            }
        }

        // Recent heading
        item {
            Section {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Recent", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onSeeAll) { Text("See all") }
                }
            }
        }

        if (recent.isEmpty()) {
            item {
                Section {
                    Text(
                        "No expenses yet. Add one above.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(recent, key = { it.id }) { e ->
            val catText = categories.firstOrNull { it.id == e.categoryId }?.display()
            ListItem(
                headlineContent = { ExpenseHeadline(e) },
                supportingContent = {
                    Text(listOfNotNull(catText, formatTime(e.timestamp)).joinToString(" · "))
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { editing = e }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { onDelete(e) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    }

    // Edit pop-up: shows only while an expense is being edited
    editing?.let { e ->
        EditExpenseDialog(
            expense = e,
            categories = categories,
            onDismiss = { editing = null },
            onSave = { vm.updateExpense(it); editing = null }
        )
    }
}