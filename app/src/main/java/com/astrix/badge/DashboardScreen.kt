package com.astrix.badge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(vm: BadgeViewModel) {
    val expenses by vm.expenses.collectAsState()
    val categories by vm.categories.collectAsState()
    val quick by vm.quickButtons.collectAsState()
    var text by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<Long?>(null) }

    fun total(period: String) =
        expenses.filter { it.timestamp >= Periods.startOf(period) }.sumOf { it.amount }

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
                        money(total("DAY")),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text("Week: " + money(total("WEEK")))
                        Text("Month: " + money(total("MONTH")))
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmountField(text, { text = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    text.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                        vm.addExpense(it, cat)
                        text = ""
                    }
                }) { Text("Add") }
            }
        }

        item { Text("Recent", style = MaterialTheme.typography.titleMedium) }

        items(expenses.take(30), key = { it.id }) { e ->
            val catName = categories.firstOrNull { it.id == e.categoryId }?.name
            val time = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(e.timestamp))
            ListItem(
                headlineContent = { Text(money(e.amount)) },
                supportingContent = {
                    Text(listOfNotNull(catName, e.note.ifBlank { null }, time).joinToString(" · "))
                },
                trailingContent = {
                    IconButton(onClick = { vm.deleteExpense(e) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    }
}