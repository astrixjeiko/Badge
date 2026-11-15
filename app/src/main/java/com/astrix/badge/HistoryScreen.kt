package com.astrix.badge

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.runtime.withFrameNanos
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun HistoryScreen(vm: BadgeViewModel, onDelete: (Expense) -> Unit) {
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

    // After Undo, scroll to the restored expense once it shows up in the list
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
                // Only scroll up: skip it if the expense is already fully on screen or below
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
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                count.toString() + (if (count == 1) " expense" else " expenses") + " · " + money(total),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (hasFilters) {
                TextButton(onClick = {
                    queryText = ""
                    vm.updateFilter { HistoryFilter(sort = it.sort) }
                }) { Text("Clear filters") }
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
                            val catName = categories.firstOrNull { it.id == e.categoryId }?.name
                            val whenText = if (showDates) formatTime(e.timestamp) else formatClock(e.timestamp)
                            ListItem(
                                headlineContent = { Text(money(e.amount)) },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(catName, e.note.ifBlank { null }, whenText)
                                            .joinToString(" · ")
                                    )
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
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis ?: return@TextButton
                    val end = state.selectedEndDateMillis ?: start
                    val from = pickerDayStart(start)
                    val to = nextDayStart(pickerDayStart(end))
                    onConfirm(from, to)
                }
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(state = state, modifier = Modifier.height(500.dp))
    }
}