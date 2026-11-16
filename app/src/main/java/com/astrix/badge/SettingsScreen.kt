package com.astrix.badge

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private fun groupOf(key: Any): String? {
    val k = key.toString()
    return when {
        k.startsWith("cat-") -> "cat"
        k.startsWith("btn-") -> "btn"
        else -> null
    }
}

private fun <T> List<T>.moved(fromKey: String, toKey: String, keyOf: (T) -> String): List<T> {
    val from = indexOfFirst { keyOf(it) == fromKey }
    val to = indexOfFirst { keyOf(it) == toKey }
    if (from < 0 || to < 0 || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

@Composable
fun SettingsScreen(vm: BadgeViewModel) {
    val tour = LocalTourTargets.current
    val categories by vm.categories.collectAsState()
    val buttons by vm.quickButtons.collectAsState()
    val categoryById = remember(categories) { categories.associateBy { it.id } }

    var catOrder by remember(categories) { mutableStateOf(categories) }
    var btnOrder by remember(buttons) { mutableStateOf(buttons) }

    var editingCat by remember { mutableStateOf<Category?>(null) }
    var editingBtn by remember { mutableStateOf<QuickButton?>(null) }

    val listState = rememberLazyListState()
    val reorder = rememberReorderState(
        listState = listState,
        sameGroup = { a, b -> groupOf(a) != null && groupOf(a) == groupOf(b) },
        onMove = { from, to ->
            when (groupOf(from)) {
                "cat" -> {
                    val moved = catOrder.moved(from.toString(), to.toString()) { "cat-" + it.id }
                    val changed = moved !== catOrder
                    catOrder = moved
                    changed
                }
                "btn" -> {
                    val moved = btnOrder.moved(from.toString(), to.toString()) { "btn-" + it.id }
                    val changed = moved !== btnOrder
                    btnOrder = moved
                    changed
                }
                else -> false
            }
        },
        onDrop = {
            val catIds = catOrder.map { it.id }
            if (catIds != categories.map { it.id }) vm.reorderCategories(catIds)
            val btnIds = btnOrder.map { it.id }
            if (btnIds != buttons.map { it.id }) vm.reorderQuickButtons(btnIds)
        }
    )

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Currency
        item(key = "currency-title") {
            Text("Currency", style = MaterialTheme.typography.titleLarge)
        }
        item(key = "currency") { CurrencyPicker() }
        item(key = "gap-anim") { Spacer(Modifier.height(8.dp)) }
        item(key = "animations") { AnimationToggle() }
        item(key = "gap-1") { Spacer(Modifier.height(16.dp)) }

        // Categories
        item(key = "categories-title") {
            Text(
                "Categories",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.tourTarget(tour, TourTarget.SETTINGS_CATEGORIES)
            )
        }
        items(catOrder, key = { "cat-" + it.id }, contentType = { "category" }) { c ->
            ReorderRow(reorder, "cat-" + c.id) { handle ->
                CategoryRow(
                    category = c,
                    handle = handle,
                    onEdit = { editingCat = c },
                    onDelete = { vm.deleteCategory(c) }
                )
            }
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
        items(btnOrder, key = { "btn-" + it.id }, contentType = { "button" }) { b ->
            ReorderRow(reorder, "btn-" + b.id) { handle ->
                QuickButtonRow(
                    button = b,
                    categoryText = categoryById[b.categoryId]?.display() ?: "No category",
                    handle = handle,
                    onEdit = { editingBtn = b },
                    onDelete = { vm.deleteQuickButton(b) }
                )
            }
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
private fun AnimationToggle() {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text("Animations", style = MaterialTheme.typography.titleMedium)
            Text(
                "Liquid effect on the Home card, which also reacts to tilting your phone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = AnimationStore.enabled,
            onCheckedChange = { AnimationStore.set(context, it) }
        )
    }
}

@Composable
private fun DragHandleIcon(handle: Modifier) {
    Box(
        Modifier.size(40.dp).then(handle),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.DragHandle,
            contentDescription = "Drag to reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    handle: Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DragHandleIcon(handle)
        Text(category.display(), modifier = Modifier.weight(1f))
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
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
    handle: Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DragHandleIcon(handle)
        Column(Modifier.weight(1f)) {
            Text(button.label + ": " + money(button.amount))
            Text(
                categoryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
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