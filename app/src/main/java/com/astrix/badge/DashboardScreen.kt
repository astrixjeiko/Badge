package com.astrix.badge

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val WARN_START = 0.85f

@Composable
private fun Section(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .then(modifier),
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
    val accounts by vm.accounts.collectAsState()
    val due by vm.dueRecurring.collectAsState()
    val dueOffset = if (due.isNotEmpty()) 1 else 0
    var account by remember { mutableStateOf(vm.lastAccountId()) }
    val chosenAccount = account?.takeIf { id -> accounts.any { it.id == id } }
    val reference by vm.dailyReference.collectAsState()

    val ratio = if (reference.amount > 0) (today / reference.amount).toFloat() else 0f
    val full = reference.amount > 0 && ratio >= 1f
    val level = when {
        reference.amount > 0 -> ratio.coerceIn(0f, 1f)
        today > 0 -> 0.4f
        else -> 0f
    }

    val warnTarget = ((ratio - WARN_START) / (1f - WARN_START)).coerceIn(0f, 1f)
    val warn by animateFloatAsState(
        targetValue = warnTarget,
        animationSpec = if (AnimationStore.enabled) tween(700) else snap(),
        label = "card-warning"
    )
    val scheme = MaterialTheme.colorScheme
    val cardColor = lerp(scheme.primaryContainer, scheme.errorContainer, warn)
    val cardContent = lerp(scheme.onPrimaryContainer, scheme.onErrorContainer, warn)

    var text by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Expense?>(null) }

    val tour = LocalTourTargets.current
    val selection = LocalSelection.current
    val listState = rememberLazyListState()

    LaunchedEffect(tour.active) {
        when (tour.active) {
            TourTarget.SUMMARY -> listState.animateScrollToItem(0)
            TourTarget.QUICK_ADD -> listState.animateScrollToItem(1 + dueOffset)
            TourTarget.CUSTOM_AMOUNT -> listState.animateScrollToItem(2 + dueOffset)
            else -> Unit
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp)
    ) {
        // Summary card
        item {
            Section {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tourTarget(tour, TourTarget.SUMMARY),
                    colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = cardContent)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        LiquidBackground(
                            level = level,
                            full = full,
                            modifier = Modifier.matchParentSize()
                        )
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
        }

        // Recurring expenses that are due
        if (due.isNotEmpty()) {
            item(key = "due") {
                Section {
                    DueSection(
                        due = due,
                        onAdd = { vm.confirmRecurring(it) },
                        onSkip = { vm.skipRecurring(it) }
                    )
                }
            }
        }

        // Quick-add buttons
        item {
            Section(Modifier.tourTarget(tour, TourTarget.QUICK_ADD)) {
                Text("Quick add", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (quick.isEmpty()) Text("No buttons yet. Add some in Settings.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { b ->
                        val icon = categories.firstOrNull { it.id == b.categoryId }?.icon.orEmpty()
                        AssistChip(
                            onClick = { vm.addExpense(b.amount, b.categoryId, b.label, b.accountId) },
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
            Section(Modifier.tourTarget(tour, TourTarget.CUSTOM_AMOUNT)) {
                Text("Custom amount", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Column {
                    CategoryChips(categories, cat) { cat = it }
                    AccountChips(accounts, chosenAccount) {
                        account = it
                        vm.rememberAccount(it)
                    }
                }
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
                                vm.addExpense(it, cat, note.trim(), chosenAccount)
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
            ExpenseRow(
                expense = e,
                categoryText = categories.firstOrNull { it.id == e.categoryId }?.display(),
                detailText = formatTime(e.timestamp),
                account = accounts.firstOrNull { it.id == e.accountId },
                selection = selection,
                onEdit = { editing = e },
                onDelete = { onDelete(e) }
            )
        }
    }

    editing?.let { e ->
        EditExpenseDialog(
            expense = e,
            categories = categories,
            accounts = accounts,
            onDismiss = { editing = null },
            onSave = { vm.updateExpense(it); editing = null }
        )
    }
}