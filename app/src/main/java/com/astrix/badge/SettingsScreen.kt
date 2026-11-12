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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext

@Composable
fun SettingsScreen(vm: BadgeViewModel) {
    val context = LocalContext.current
    val categories by vm.categories.collectAsState()
    val buttons by vm.quickButtons.collectAsState()
    var catName by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var btnCat by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { item { Text("Currency", style = MaterialTheme.typography.titleLarge) }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CurrencyStore.options.forEach { s ->
                    FilterChip(
                        selected = CurrencyStore.symbol == s,
                        onClick = { CurrencyStore.set(context, s) },
                        label = { Text(s) }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }

        item { Text("Categories", style = MaterialTheme.typography.titleLarge) }
        items(categories, key = { "c" + it.id }) { c ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(c.name)
                IconButton(onClick = { vm.deleteCategory(c) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    catName, { catName = it },
                    label = { Text("New category") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (catName.isNotBlank()) { vm.addCategory(catName.trim()); catName = "" }
                }) { Text("Add") }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
        item { Text("Quick buttons", style = MaterialTheme.typography.titleLarge) }
        items(buttons, key = { "b" + it.id }) { b ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(b.label + ": " + money(b.amount))
                IconButton(onClick = { vm.deleteQuickButton(b) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    label, { label = it },
                    label = { Text("Button name (e.g. Jeepney)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AmountField(amount, { amount = it }, Modifier.fillMaxWidth())
                CategoryChips(categories, btnCat) { btnCat = it }
                Button(onClick = {
                    val a = amount.toDoubleOrNull()
                    if (label.isNotBlank() && a != null && a > 0) {
                        vm.addQuickButton(label.trim(), a, btnCat)
                        label = ""; amount = ""
                    }
                }) { Text("Add quick button") }
            }
        }
    }
}