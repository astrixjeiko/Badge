package com.astrix.badge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    vm: BadgeViewModel,
    onDeleteBudget: (Budget) -> Unit,
    onDeleteRecurring: (Recurring) -> Unit
) {
    val tour = LocalTourTargets.current
    val selection = LocalSelection.current
    var section by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(tour.active) { if (tour.active == TourTarget.NEW_BUDGET) section = 0 }
    LaunchedEffect(section) { selection.clear() }

    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                .height(40.dp)
        ) {
            listOf("Budgets", "Recurring").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = section == index,
                    onClick = { section = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    label = { Text(label) }
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (section == 0) {
                BudgetScreen(vm, onDeleteBudget)
            } else {
                RecurringScreen(vm, onDeleteRecurring)
            }
        }
    }
}

@Composable
fun RecurringScreen(vm: BadgeViewModel, onDelete: (Recurring) -> Unit) {
    val context = LocalContext.current
    val list by vm.recurring.collectAsState()
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val resumes by vm.resumes.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Recurring?>(null) }

    val notificationsOn = remember(resumes) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.refreshPeriods() }

    val turnOnReminders: () -> Unit = {
        val prefs = context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("asked_notifications", false)) {
            prefs.edit { putBoolean("asked_notifications", true) }
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }

    val monthly = list.filter { it.enabled }.sumOf { it.amount }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Button(onClick = { showAdd = true }) { Text("+ New recurring") } }

            if (!notificationsOn) {
                item { ReminderCard(onTurnOn = turnOnReminders) }
            }

            if (list.any { it.enabled }) {
                item {
                    Text(
                        "Total: " + money(monthly) + " per month",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            items(list, key = { it.id }) { r ->
                RecurringCard(
                    r = r,
                    category = categories.firstOrNull { it.id == r.categoryId },
                    account = accounts.firstOrNull { it.id == r.accountId },
                    onEdit = { editing = r },
                    onDelete = { onDelete(r) },
                    onToggle = { vm.updateRecurring(r.copy(enabled = it)) }
                )
            }
        }

        if (list.isEmpty()) {
            Text(
                "No recurring expenses yet. Tap + New recurring to add.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
            )
        }
    }

    if (showAdd) {
        RecurringDialog(
            title = "New recurring expense",
            initial = null,
            categories = categories,
            accounts = accounts,
            onDismiss = { showAdd = false },
            onSave = { name, amount, day, cat, acc ->
                vm.addRecurring(name, amount, day, cat, acc)
                showAdd = false
            }
        )
    }

    editing?.let { r ->
        RecurringDialog(
            title = "Edit recurring expense",
            initial = r,
            categories = categories,
            accounts = accounts,
            onDismiss = { editing = null },
            onSave = { name, amount, day, cat, acc ->
                vm.updateRecurring(
                    r.copy(name = name, amount = amount, dayOfMonth = day, categoryId = cat, accountId = acc)
                )
                editing = null
            }
        )
    }
}

@Composable
private fun ReminderCard(onTurnOn: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Reminders are off", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Turn on notifications to be reminded when a bill is due. Due items still show up on Home.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onTurnOn) { Text("Turn on") }
        }
    }
}

@Composable
private fun RecurringCard(
    r: Recurring,
    category: Category?,
    account: Account?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val due = r.enabled && r.nextDue <= System.currentTimeMillis()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        money(r.amount) + " · every month on the " + ordinal(r.dayOfMonth),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }

            if (category != null || account != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (account != null) {
                        AccountPill(account, Modifier.padding(end = 6.dp))
                    }
                    if (category != null) {
                        Text(
                            category.display(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    when {
                        !r.enabled -> "Paused"
                        due -> "Due now"
                        else -> "Next: " + formatDate(r.nextDue)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (due) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(checked = r.enabled, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
fun RecurringDialog(
    title: String,
    initial: Recurring?,
    categories: List<Category>,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, day: Int, categoryId: Long?, accountId: Long?) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amount by remember { mutableStateOf(initial?.let { plainAmount(it.amount) } ?: "") }
    var day by remember {
        mutableStateOf(
            (initial?.dayOfMonth ?: Calendar.getInstance().get(Calendar.DAY_OF_MONTH)).toString()
        )
    }
    var cat by remember { mutableStateOf(initial?.categoryId) }
    var acc by remember {
        mutableStateOf(initial?.accountId?.takeIf { id -> accounts.any { it.id == id } })
    }
    val dayNumber = day.toIntOrNull()

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
                    label = { Text("Name (e.g. Spotify)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AmountField(amount, { amount = it }, Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = day,
                    onValueChange = { v ->
                        if (v.matches(Regex("[0-9]{0,2}")) && (v.isEmpty() || v.toInt() <= 31)) day = v
                    },
                    label = { Text("Day of the month (1 to 31)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        if (dayNumber != null && dayNumber >= 29) {
                            Text("In shorter months, it's due on the last day.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    CategoryChips(categories, cat) { cat = it }
                    AccountChips(accounts, acc) { acc = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toDoubleOrNull()
                if (name.isNotBlank() && a != null && a > 0 && dayNumber != null && dayNumber in 1..31) {
                    onSave(name.trim(), a, dayNumber, cat, acc)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun DueSection(
    due: List<Recurring>,
    onAdd: (Recurring) -> Unit,
    onSkip: (Recurring) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Due", style = MaterialTheme.typography.titleMedium)
        due.forEach { r ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            money(r.amount) + " · due " + formatDate(r.nextDue),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = { onSkip(r) }) { Text("Skip") }
                    Button(onClick = { onAdd(r) }) { Text("Add") }
                }
            }
        }
    }
}