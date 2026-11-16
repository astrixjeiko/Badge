package com.astrix.badge

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

// What is selected right now (expenses on Home/History, or budgets on Budgets)
class SelectionState {
    var expenses by mutableStateOf<Map<Long, Expense>>(emptyMap())
        private set
    var budgets by mutableStateOf<Map<Long, Budget>>(emptyMap())
        private set

    val count: Int get() = expenses.size + budgets.size
    val active: Boolean get() = count > 0

    fun isSelected(e: Expense) = e.id in expenses
    fun isSelected(b: Budget) = b.id in budgets

    fun toggle(e: Expense) {
        expenses = if (e.id in expenses) expenses - e.id else expenses + (e.id to e)
    }

    fun toggle(b: Budget) {
        budgets = if (b.id in budgets) budgets - b.id else budgets + (b.id to b)
    }

    fun selectExpenses(list: List<Expense>) { expenses = list.associateBy { it.id } }
    fun selectBudgets(list: List<Budget>) { budgets = list.associateBy { it.id } }

    fun clear() {
        expenses = emptyMap()
        budgets = emptyMap()
    }
}

val LocalSelection = compositionLocalOf { SelectionState() }

fun plural(n: Int, word: String): String = n.toString() + " " + word + if (n == 1) "" else "s"

// The bar at the top while selecting
@Composable
fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: (() -> Unit)?,
    onDelete: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
            }
            Text(
                "$count selected",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (onSelectAll != null) {
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete selected")
            }
        }
    }
}

// An expense row used on Home (Recent) and in History
@Composable
fun ExpenseRow(
    expense: Expense,
    categoryText: String?,
    detailText: String,
    selection: SelectionState,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val selected = selection.isSelected(expense)
    val selecting = selection.active
    val haptics = LocalHapticFeedback.current

    ListItem(
        modifier = Modifier.pointerInput(expense, selecting) {
            detectTapGestures(
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    selection.toggle(expense)
                },
                onTap = { if (selecting) selection.toggle(expense) }
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
        ),
        headlineContent = { ExpenseHeadline(expense) },
        supportingContent = {
            Text(listOfNotNull(categoryText, detailText).joinToString(" · "))
        },
        trailingContent = {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = null)
            } else {
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
    )
}