package com.astrix.badge

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import kotlinx.coroutines.launch

private fun groupOf(key: Any): String? {
    val k = key.toString()
    return when {
        k.startsWith("cat-") -> "cat"
        k.startsWith("acc-") -> "acc"
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
fun SettingsScreen(
    vm: BadgeViewModel,
    onDeleteCategory: (Category) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    onDeleteButton: (QuickButton) -> Unit
) {
    val tour = LocalTourTargets.current
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val buttons by vm.quickButtons.collectAsState()
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    val accountById = remember(accounts) { accounts.associateBy { it.id } }

    var catOrder by remember(categories) { mutableStateOf(categories) }
    var accOrder by remember(accounts) { mutableStateOf(accounts) }
    var btnOrder by remember(buttons) { mutableStateOf(buttons) }

    var editingCat by remember { mutableStateOf<Category?>(null) }
    var editingAcc by remember { mutableStateOf<Account?>(null) }
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
                "acc" -> {
                    val moved = accOrder.moved(from.toString(), to.toString()) { "acc-" + it.id }
                    val changed = moved !== accOrder
                    accOrder = moved
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
            val accIds = accOrder.map { it.id }
            if (accIds != accounts.map { it.id }) vm.reorderAccounts(accIds)
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
        item(key = "gap-alerts") { Spacer(Modifier.height(8.dp)) }
        item(key = "alerts") { BudgetAlertsToggle(vm) }
        item(key = "gap-google") { Spacer(Modifier.height(16.dp)) }
        item(key = "google-title") {
            Text("Google account", style = MaterialTheme.typography.titleLarge)
        }
        item(key = "google") { GoogleAccountCard() }
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
            ReorderRow(reorder, "cat-" + c.id) { dragHandle ->
                CategoryRow(
                    category = c,
                    dragHandle = dragHandle,
                    onEdit = { editingCat = c },
                    onDelete = { onDeleteCategory(c) }
                )
            }
        }
        item(key = "add-category") {
            AddCategoryRow(onAdd = { name, icon -> vm.addCategory(name, icon) })
        }
        item(key = "gap-2") { Spacer(Modifier.height(16.dp)) }

        // Accounts
        item(key = "accounts-title") {
            Text("Accounts", style = MaterialTheme.typography.titleLarge)
        }
        items(accOrder, key = { "acc-" + it.id }, contentType = { "account" }) { a ->
            ReorderRow(reorder, "acc-" + a.id) { dragHandle ->
                AccountRow(
                    account = a,
                    dragHandle = dragHandle,
                    onEdit = { editingAcc = a },
                    onDelete = { onDeleteAccount(a) }
                )
            }
        }
        item(key = "add-account") {
            AddAccountRow(onAdd = { name, color -> vm.addAccount(name, color) })
        }
        item(key = "gap-3") { Spacer(Modifier.height(16.dp)) }

        // Quick buttons
        item(key = "buttons-title") {
            Text(
                "Quick buttons (" + buttons.size + "/" + MAX_QUICK_BUTTONS + ")",
                style = MaterialTheme.typography.titleLarge
            )
        }
        items(btnOrder, key = { "btn-" + it.id }, contentType = { "button" }) { b ->
            ReorderRow(reorder, "btn-" + b.id) { dragHandle ->
                QuickButtonRow(
                    button = b,
                    categoryText = categoryById[b.categoryId]?.display() ?: "No category",
                    account = accountById[b.accountId],
                    dragHandle = dragHandle,
                    onEdit = { editingBtn = b },
                    onDelete = { onDeleteButton(b) }
                )
            }
        }
        item(key = "add-button") {
            AddQuickButtonForm(
                categories = categories,
                accounts = accounts,
                atLimit = buttons.size >= MAX_QUICK_BUTTONS,
                onAdd = { label, amount, cat, acc -> vm.addQuickButton(label, amount, cat, acc) }
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
    editingAcc?.let { a ->
        EditAccountDialog(
            account = a,
            onDismiss = { editingAcc = null },
            onSave = { vm.updateAccount(it); editingAcc = null }
        )
    }
    editingBtn?.let { b ->
        EditQuickButtonDialog(
            button = b,
            categories = categories,
            accounts = accounts,
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
                "Liquid card and animated insights on Home.",
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
private fun CategoryRow(
    category: Category,
    dragHandle: @Composable () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dragHandle()
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
private fun AccountRow(
    account: Account,
    dragHandle: @Composable () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dragHandle()
        Box(Modifier.weight(1f)) { AccountPill(account) }
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
private fun AddAccountRow(onAdd: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(5) }
    var color by remember { mutableIntStateOf(ACCOUNT_COLORS[5]) }
    var showPicker by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.Bottom) {
        ColorPickerButton(color) { showPicker = true }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("New account") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onAdd(name.trim(), color)
                    name = ""
                    colorIndex = (colorIndex + 1) % ACCOUNT_COLORS.size
                    color = ACCOUNT_COLORS[colorIndex]
                }
            }) { Text("Add") }
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            initial = color,
            previewName = name,
            onDismiss = { showPicker = false },
            onPick = { color = it; showPicker = false }
        )
    }
}

@Composable
private fun QuickButtonRow(
    button: QuickButton,
    categoryText: String,
    account: Account?,
    dragHandle: @Composable () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dragHandle()
        Column(Modifier.weight(1f)) {
            Text(button.label + ": " + money(button.amount))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    categoryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (account != null) {
                    Spacer(Modifier.width(6.dp))
                    AccountPill(account)
                }
            }
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
    accounts: List<Account>,
    atLimit: Boolean,
    onAdd: (String, Double, Long?, Long?) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<Long?>(null) }
    var acc by remember { mutableStateOf<Long?>(null) }

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
            Column {
                CategoryChips(categories, cat) { cat = it }
                AccountChips(accounts, acc) { acc = it }
            }
            Button(onClick = {
                val a = amount.toDoubleOrNull()
                if (label.isNotBlank() && a != null && a > 0) {
                    onAdd(label.trim(), a, cat, acc)
                    label = ""
                    amount = ""
                }
            }) { Text("Add quick button") }
        }
    }
}

@Composable
private fun BudgetAlertsToggle(vm: BadgeViewModel) {
    val context = LocalContext.current
    val resumes by vm.resumes.collectAsState()
    val prefs = remember { context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("budget_alerts", true)) }

    val notificationsOn = remember(resumes) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val turnOn = rememberNotificationEnabler { vm.refreshPeriods() }

    Column {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Budget alerts", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Get a notification when an expense you add from the widget puts you over a budget",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    prefs.edit { putBoolean("budget_alerts", checked) }
                    if (checked && !notificationsOn) turnOn()
                }
            )
        }
        if (enabled && !notificationsOn) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Notifications are off for Badge, so alerts can't appear.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = turnOn) { Text("Turn on notifications") }
        }
    }
}

@Composable
private fun GoogleAccountCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account = GoogleAccountStore.account
    var busy by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (account == null) {
                Text("Link your Google account", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Linking lets Badge back up your data to your own Google account in a coming update. Nothing is uploaded yet, and your expenses stay on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val result = linkGoogleAccount(context)
                            result.onSuccess { GoogleAccountStore.save(context, it) }
                            result.onFailure { error ->
                                googleErrorMessage(error)?.let {
                                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                                }
                            }
                            busy = false
                        }
                    }
                ) { Text(if (busy) "Linking…" else "Link Google account") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            account.name.ifBlank { account.email }.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            account.name.ifBlank { "Google account" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            account.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            unlinkGoogleAccount(context)
                            busy = false
                        }
                    }
                ) { Text("Unlink") }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Unlinking doesn't delete anything on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}