package com.astrix.badge

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val WARN_START = 0.85f
private enum class SummaryPeriod(
    val label: String,
    val title: String,
    val days: Double,
    val budgetKey: String
) {
    TODAY("Today", "Spent today", 1.0, "DAY"),
    WEEK("This week", "Spent this week", 7.0, "WEEK"),
    MONTH("This month", "Spent this month", 30.0, "MONTH")
}

// Overall budgets of a period if there are any
private fun periodBudget(budgets: List<Budget>, key: String): Double {
    val ofPeriod = budgets.filter { it.period == key }
    val overall = ofPeriod.filter { it.categoryId == null }
    return (overall.ifEmpty { ofPeriod }).sumOf { it.limitAmount }
}

// The amount the card fills toward for a period
private fun budgetFor(budgets: List<Budget>, period: SummaryPeriod, fallbackDaily: Double): Double {
    val exact = periodBudget(budgets, period.budgetKey)
    if (exact > 0) return exact
    val perDay = listOf(
        periodBudget(budgets, "DAY"),
        periodBudget(budgets, "WEEK") / 7.0,
        periodBudget(budgets, "MONTH") / 30.0,
        periodBudget(budgets, "YEAR") / 365.0
    ).firstOrNull { it > 0 }
    return (perDay ?: fallbackDaily) * period.days
}

@Composable
private fun PeriodLink(
    label: String,
    amount: Double,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val active = hovered || pressed
    val base = LocalContentColor.current
    val color by animateColorAsState(
        targetValue = base.copy(alpha = if (active) 0.6f else 1f),
        animationSpec = if (AnimationStore.enabled) tween(150) else snap(),
        label = "period-link"
    )
    val decoration = if (active) TextDecoration.Underline else null
    Column(
        modifier
            .hoverable(source)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Text(
            "$label:",
            color = color,
            textDecoration = decoration,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            money(amount),
            color = color,
            textDecoration = decoration,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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
fun DashboardScreen(
    vm: BadgeViewModel,
    onSeeAll: () -> Unit,
    onOpenStats: () -> Unit,
    onDelete: (Expense) -> Unit
) {
    val recent by vm.recent.collectAsState()
    val today by vm.todayTotal.collectAsState()
    val week by vm.weekTotal.collectAsState()
    val month by vm.monthTotal.collectAsState()
    val categories by vm.categories.collectAsState()
    val quick by vm.quickButtons.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val due by vm.dueRecurring.collectAsState()
    val insights by vm.insights.collectAsState()
    val insightPeriod by vm.insightPeriod.collectAsState()
    val dueOffset = if (due.isNotEmpty()) 1 else 0
    var account by remember { mutableStateOf(vm.lastAccountId()) }
    val chosenAccount = account?.takeIf { id -> accounts.any { it.id == id } }
    val reference by vm.dailyReference.collectAsState()

    val budgets by vm.budgets.collectAsState()
    var summaryIndex by rememberSaveable { mutableIntStateOf(0) }
    val summary = SummaryPeriod.entries[summaryIndex]
    val totals = listOf(today, week, month)
    val spent = totals[summaryIndex]
    val limit = budgetFor(budgets, summary, reference.amount)

    val ratio = if (limit > 0) (spent / limit).toFloat() else 0f
    val full = limit > 0 && ratio >= 1f
    val level = when {
        limit > 0 -> ratio.coerceIn(0f, 1f)
        spent > 0 -> 0.4f
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
            TourTarget.INSIGHTS -> listState.animateScrollToItem(3 + dueOffset)
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
                            AnimatedContent(
                                targetState = summaryIndex,
                                modifier = Modifier.fillMaxWidth(),
                                transitionSpec = {
                                    if (!AnimationStore.enabled) {
                                        EnterTransition.None togetherWith ExitTransition.None
                                    } else {
                                        val dir = if (targetState > initialState) 1 else -1
                                        (slideInHorizontally(tween(300)) { it / 6 * dir } + fadeIn(tween(300))) togetherWith
                                                (slideOutHorizontally(tween(300)) { -it / 6 * dir } + fadeOut(tween(200)))
                                    }
                                },
                                label = "summary-period"
                            ) { index ->
                                Column(Modifier.fillMaxWidth()) {
                                    Text(
                                        SummaryPeriod.entries[index].title,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        money(totals[index]),
                                        style = MaterialTheme.typography.displayMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                                    ) {
                                        SummaryPeriod.entries.forEachIndexed { i, other ->
                                            if (i != index) {
                                                PeriodLink(
                                                    other.label,
                                                    totals[i],
                                                    Modifier.weight(1f, fill = false)
                                                ) { summaryIndex = i }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(4.dp))
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

        // Insights
        item(key = "insights") {
            Section {
                Spacer(Modifier.height(8.dp))
                InsightsSection(
                    period = insightPeriod,
                    state = insights,
                    categories = categories,
                    tour = tour,
                    onPeriod = { vm.setInsightPeriod(it) },
                    onSeeAll = onOpenStats,
                    onCategoryClick = { categoryId ->
                        vm.updateFilter {
                            HistoryFilter(
                                sort = it.sort,
                                categoryId = categoryId,
                                range = DateRange.CUSTOM,
                                customFrom = insights.range.start,
                                customTo = insights.range.end
                            )
                        }
                        onSeeAll()
                    }
                )
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