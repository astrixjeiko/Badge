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

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary card
        item {
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

        // Quick-add buttons
        item {
            Text("Quick add", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (quick.isEmpty()) Text("No buttons yet. Add some in Settings.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quick.forEach { b ->
                    AssistChip(
                        onClick = { vm.addExpense(b.amount, b.categoryId, b.label) },
                        label = { Text(b.label + " " + money(b.amount)) }
                    )
                }
            }
        }

        // Custom amount
        item {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmountField(text, { text = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    text.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                        vm.addExpense(it, cat, note.trim())
                        text = ""
                        note = ""
                    }
                }) { Text("Add") }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Recent", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onSeeAll) { Text("See all") }
            }
        }

        if (recent.isEmpty()) {
            item {
                Text(
                    "No expenses yet. Add one above.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(recent, key = { it.id }) { e ->
            val catName = categories.firstOrNull { it.id == e.categoryId }?.name
            val time = formatTime(e.timestamp)
            ListItem(
                headlineContent = { Text(money(e.amount)) },
                supportingContent = {
                    Text(listOfNotNull(catName, e.note.ifBlank { null }, time).joinToString(" · "))
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

    // Edit pop-up: shows only while an entry is being edited
    editing?.let { e ->
        EditExpenseDialog(
            expense = e,
            categories = categories,
            onDismiss = { editing = null },
            onSave = { vm.updateExpense(it); editing = null }
        )
    }
}