package com.astrix.badge

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.astrix.badge.ui.theme.BadgeTheme

class MainActivity : ComponentActivity() {
    private val vm: BadgeViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val start = SystemClock.uptimeMillis()
            splash.setKeepOnScreenCondition { SystemClock.uptimeMillis() - start < 700 }
        }

        enableEdgeToEdge()
        CurrencyStore.load(this)
        AnimationStore.load(this)
        setContent { BadgeTheme { BadgeApp() } }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPeriods()
    }
}

@Composable
fun BadgeApp(vm: BadgeViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showTour by vm.showTour.collectAsState()
    val tourTargets = remember { TourTargets() }
    val selection = remember { SelectionState() }

    // Keeps the last count so the bar doesn't show "0 selected" while it slides away
    val lastCount = remember { mutableIntStateOf(0) }
    if (selection.count > 0) lastCount.intValue = selection.count

    // Selecting ends when you switch tabs or press back
    LaunchedEffect(tab) { selection.clear() }
    BackHandler(enabled = selection.active) { selection.clear() }

    // Shows a message with Undo for a few seconds
    val showUndo: (String, () -> Unit) -> Unit = { message, onUndo ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    val deleteWithUndo: (Expense) -> Unit = { e ->
        vm.deleteExpense(e)
        showUndo("Expense deleted") { vm.restoreExpense(e) }
    }

    val deleteBudgetWithUndo: (Budget) -> Unit = { b ->
        vm.deleteBudgets(listOf(b))
        showUndo("Budget deleted") { vm.restoreBudgets(listOf(b)) }
    }

    val deleteSelected: () -> Unit = {
        val expenses = selection.expenses.values.toList()
        val budgets = selection.budgets.values.toList()
        selection.clear()
        if (expenses.isNotEmpty()) {
            vm.deleteExpenses(expenses)
            showUndo(plural(expenses.size, "expense") + " deleted") { vm.restoreExpenses(expenses) }
        } else if (budgets.isNotEmpty()) {
            vm.deleteBudgets(budgets)
            showUndo(plural(budgets.size, "budget") + " deleted") { vm.restoreBudgets(budgets) }
        }
    }

    // History selects everything matching the current filter; Budgets selects every budget
    val selectAllExpenses: () -> Unit = {
        scope.launch { selection.selectExpenses(vm.filteredExpenses()) }
    }
    val selectAllBudgets: () -> Unit = { selection.selectBudgets(vm.budgets.value) }
    val onSelectAll: (() -> Unit)? = when (tab) {
        1 -> selectAllExpenses
        2 -> selectAllBudgets
        else -> null
    }

    val tabs = listOf(
        "Home" to Icons.Default.Home,
        "History" to Icons.Default.History,
        "Budgets" to Icons.Default.AccountBalanceWallet,
        "Settings" to Icons.Default.Settings
    )

    CompositionLocalProvider(
        LocalTourTargets provides tourTargets,
        LocalSelection provides selection
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    AnimatedVisibility(
                        visible = selection.active,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        SelectionBar(
                            count = lastCount.intValue,
                            onClose = { selection.clear() },
                            onSelectAll = onSelectAll,
                            onDelete = deleteSelected
                        )
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    NavigationBar {
                        tabs.forEachIndexed { i, (label, icon) ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                modifier = if (label == "History") {
                                    Modifier.tourTarget(tourTargets, TourTarget.NAV_HISTORY)
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    when (tab) {
                        0 -> DashboardScreen(vm, onSeeAll = { tab = 1 }, onDelete = deleteWithUndo)
                        1 -> HistoryScreen(vm, onDelete = deleteWithUndo)
                        2 -> BudgetScreen(vm, onDelete = deleteBudgetWithUndo)
                        else -> SettingsScreen(vm)
                    }
                }
            }

            // The tutorial sits on top of everything
            AnimatedVisibility(
                visible = showTour,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(250))
            ) {
                TourOverlay(
                    targets = tourTargets,
                    onTab = { tab = it },
                    onFinish = {
                        tab = 0
                        vm.finishTour()
                    }
                )
            }
        }
    }
}