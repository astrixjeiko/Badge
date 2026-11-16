package com.astrix.badge

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: BadgeViewModel) {
    val tour = LocalTourTargets.current
    val categories by vm.categories.collectAsState()
    val buttons by vm.quickButtons.collectAsState()
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    var editingCat by remember { mutableStateOf<Category?>(null) }
    var editingBtn by remember { mutableStateOf<QuickButton?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Currency
        item(key = "currency-title") {
            Text("Currency", style = MaterialTheme.typography.titleLarge)
        }
        item(key = "currency") { CurrencyPicker() }
        item(key = "gap-1") { Spacer(Modifier.height(16.dp)) }

        // Categories
        item(key = "categories-title") {
            Text(
                "Categories",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.tourTarget(tour, TourTarget.SETTINGS_CATEGORIES)
            )
        }
        items(categories, key = { "c" + it.id }, contentType = { "category" }) { c ->
            CategoryRow(
                category = c,
                onEdit = { editingCat = c },
                onDelete = { vm.deleteCategory(c) }
            )
        }
        item(key = "add-category") {
            AddCategoryRow(onAdd = { name, icon -> vm.addCategory(name, icon) })
        }
        item(key = "gap-2") { Spacer(Modifier.height(16.dp)) }

        // Quick buttons
        item(key = "buttons-title") {
            Text(
                "Quick buttons (" + buttons.size + "/" + MAX_QUICK_BUTTONS + ")",
                style = MaterialTheme.typography.titleLarge
            )
        }
        items(buttons, key = { "b" + it.id }, contentType = { "button" }) { b ->
            QuickButtonRow(
                button = b,
                categoryText = categoryById[b.categoryId]?.display() ?: "No category",
                onEdit = { editingBtn = b },
                onDelete = { vm.deleteQuickButton(b) }
            )
        }
        item(key = "add-button") {
            AddQuickButtonForm(
                categories = categories,
                atLimit = buttons.size >= MAX_QUICK_BUTTONS,
                onAdd = { label, amount, cat -> vm.addQuickButton(label, amount, cat) }
            )
        }
        item(key = "tutorial") {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.startTour() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Replay tutorial") }
        }
    }

    editingCat?.let { c ->
        EditCategoryDialog(
            category = c,
            onDismiss = { editingCat = null },
            onSave = { vm.updateCategory(it); editingCat = null }
        )
    }
    editingBtn?.let { b ->
        EditQuickButtonDialog(
            button = b,
            categories = categories,
            onDismiss = { editingBtn = null },
            onSave = { vm.updateQuickButton(it); editingBtn = null }
        )
    }
}

@Composable
private fun CurrencyPicker() {
    val context = LocalContext.current
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

@Composable
private fun CategoryRow(category: Category, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(category.display())
        Row {
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun AddCategoryRow(onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.Bottom) {
        IconPickerButton(icon) { showPicker = true }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("New category") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onAdd(name.trim(), icon)
                    name = ""
                    icon = ""
                }
            }) { Text("Add") }
        }
    }

    if (showPicker) {
        EmojiPickerDialog(
            onDismiss = { showPicker = false },
            onPick = { icon = it; showPicker = false }
        )
    }
}

@Composable
private fun QuickButtonRow(
    button: QuickButton,
    categoryText: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(button.label + ": " + money(button.amount))
            Text(
                categoryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row {
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun AddQuickButtonForm(
    categories: List<Category>,
    atLimit: Boolean,
    onAdd: (String, Double, Long?) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<Long?>(null) }

    if (atLimit) {
        Text(
            "You've reached the limit of " + MAX_QUICK_BUTTONS +
                    " quick buttons. Delete one to add another.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Button name (e.g. Jeepney)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            AmountField(amount, { amount = it }, Modifier.fillMaxWidth())
            CategoryChips(categories, cat) { cat = it }
            Button(onClick = {
                val a = amount.toDoubleOrNull()
                if (label.isNotBlank() && a != null && a > 0) {
                    onAdd(label.trim(), a, cat)
                    label = ""
                    amount = ""
                }
            }) { Text("Add quick button") }
        }
    }
}