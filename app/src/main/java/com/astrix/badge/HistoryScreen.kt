package com.astrix.badge

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
fun HistoryScreen(vm: BadgeViewModel, onDelete: (Expense) -> Unit) {
    val tour = LocalTourTargets.current
    val selection = LocalSelection.current
    val rows = vm.history.collectAsLazyPagingItems()
    val categories by vm.categories.collectAsState()
    val filter by vm.filter.collectAsState()
    val total by vm.historyTotal.collectAsState()
    val count by vm.historyCount.collectAsState()
    val dayTotals by vm.dayTotals.collectAsState()

    var editing by remember { mutableStateOf<Expense?>(null) }
    var queryText by remember { mutableStateOf(filter.query) }
    var sortMenu by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Search runs 250 ms after you stop typing
    LaunchedEffect(queryText) {
        delay(250.milliseconds)
        vm.updateFilter { it.copy(query = queryText) }
    }
    // Jump back to the top whenever the filter or sort changes
    LaunchedEffect(filter) { listState.scrollToItem(0) }

    // After Undo, scroll up to the restored expense if it is above what is on screen
    LaunchedEffect(Unit) {
        vm.restored.collect { id ->
            val index = withTimeoutOrNull(2.seconds) {
                snapshotFlow {
                    rows.itemSnapshotList.indexOfFirst {
                        (it as? HistoryItem.Entry)?.expense?.id == id
                    }
                }.first { it >= 0 }
            }
            if (index != null) {
                // Let the list finish redrawing before measuring where we are
                withFrameNanos { }
                withFrameNanos { }
                // Scroll one row higher so the day header stays visible
                val target = (index - 1).coerceAtLeast(0)
                val above = target < listState.firstVisibleItemIndex ||
                        (target == listState.firstVisibleItemIndex && listState.firstVisibleItemScrollOffset > 0)
                if (above) listState.animateScrollToItem(target)
            }
        }
    }

    val hasFilters = queryText.isNotBlank() ||
            filter.categoryId != null ||
            filter.range != DateRange.ALL
    val showDates = filter.sort == SortOrder.HIGHEST || filter.sort == SortOrder.LOWEST

    Column(Modifier.fillMaxSize()) {
        // Search with the sort button inside, full width
        OutlinedTextField(
            value = queryText,
            onValueChange = { queryText = it },
            placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (queryText.isNotEmpty()) {
                        IconButton(onClick = { queryText = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortOrder.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text((if (filter.sort == s) "✓ " else "") + s.label) },
                                    onClick = {
                                        vm.updateFilter { it.copy(sort = s) }
                                        sortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                .tourTarget(tour, TourTarget.SEARCH)
        )

        Spacer(Modifier.height(8.dp))

        // Date range chips
        Box(Modifier.padding(horizontal = 16.dp)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateRange.entries.forEach { r ->
                    val label = if (r == DateRange.CUSTOM && filter.range == DateRange.CUSTOM) {
                        rangeLabel(filter.customFrom, filter.customTo)
                    } else {
                        r.label
                    }
                    FilterChip(
                        selected = filter.range == r,
                        onClick = {
                            if (r == DateRange.CUSTOM) showRangePicker = true
                            else vm.updateFilter { it.copy(range = r) }
                        },
                        label = { Text(label) }
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Category chips
        Box(Modifier.padding(horizontal = 16.dp)) {
            CategoryChips(categories, filter.categoryId, anyLabel = "All") { id ->
                vm.updateFilter { it.copy(categoryId = id) }
            }
        }

        // Summary
        Row(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                count.toString() + (if (count == 1) " expense" else " expenses") + " · " + money(total),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (hasFilters) {
                Text(
                    "Clear filters",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            queryText = ""
                            vm.updateFilter { HistoryFilter(sort = it.sort) }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // List
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    count = rows.itemCount,
                    key = rows.itemKey { item ->
                        when (item) {
                            is HistoryItem.Header -> "h" + item.dayStart
                            is HistoryItem.Entry -> "e" + item.expense.id
                        }
                    }
                ) { index ->
                    when (val item = rows[index]) {
                        is HistoryItem.Header -> Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                dayLabel(item.dayStart),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                dayTotals[dayKey(item.dayStart)]?.let { money(it) } ?: "",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is HistoryItem.Entry -> {
                            val e = item.expense
                            ExpenseRow(
                                expense = e,
                                categoryText = categories.firstOrNull { it.id == e.categoryId }?.display(),
                                detailText = if (showDates) formatTime(e.timestamp) else formatClock(e.timestamp),
                                selection = selection,
                                onEdit = { editing = e },
                                onDelete = { onDelete(e) }
                            )
                        }
                        null -> Spacer(Modifier.height(1.dp))
                    }
                }
            }

            if (rows.itemCount == 0 && rows.loadState.refresh is LoadState.NotLoading) {
                Text(
                    if (hasFilters) "No matching expenses." else "No expenses yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    editing?.let { e ->
        EditExpenseDialog(
            expense = e,
            categories = categories,
            onDismiss = { editing = null },
            onSave = { vm.updateExpense(it); editing = null }
        )
    }

    if (showRangePicker) {
        DateRangeDialog(
            onDismiss = { showRangePicker = false },
            onConfirm = { from, to ->
                vm.updateFilter { it.copy(range = DateRange.CUSTOM, customFrom = from, customTo = to) }
                showRangePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(onDismiss: () -> Unit, onConfirm: (Long, Long) -> Unit) {
    val state = rememberDateRangePickerState()
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis

    val headlineText = when {
        start == null -> "Choose a start date"
        end == null -> "Choose an end date"
        else -> rangeLabel(pickerDayStart(start), nextDayStart(pickerDayStart(end)))
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = start != null,
                onClick = {
                    val s = start ?: return@TextButton
                    val e = end ?: s
                    onConfirm(pickerDayStart(s), nextDayStart(pickerDayStart(e)))
                }
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.height(500.dp),
            title = {
                Text(
                    "Select dates",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)
                )
            },
            headline = {
                Text(
                    headlineText,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp)
                )
            },
            showModeToggle = false
        )
    }
}