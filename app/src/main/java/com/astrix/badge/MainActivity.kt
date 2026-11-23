package com.astrix.badge

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.astrix.badge.ui.theme.BadgeTheme
import androidx.core.content.edit

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
        GoogleAccountStore.load(this)
        scheduleRecurringChecks(this)
        scheduleAutoBackup(this)
        // Temp
        androidx.work.WorkManager.getInstance(this).enqueue(androidx.work.OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
        val startTab = intent.getIntExtra(BudgetAlerts.EXTRA_OPEN_TAB, 0).coerceIn(0, 3)
        val prefs = getSharedPreferences("badge_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("welcome_done", false) && prefs.getBoolean("tour_done", false)) {
            prefs.edit { putBoolean("welcome_done", true) } // existing user: skip welcome
        }
        val showWelcome = !prefs.getBoolean("welcome_done", false)
        setContent { BadgeTheme { BadgeApp(startTab = startTab, welcome = showWelcome) } }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPeriods()
    }
}

@Composable
fun BadgeApp(startTab: Int = 0, welcome: Boolean = false, vm: BadgeViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showWelcome by rememberSaveable { mutableStateOf(welcome) }
    var signingIn by remember { mutableStateOf(false) }
    var restoreEmail by remember { mutableStateOf<String?>(null) }
    val finishWelcome: () -> Unit = {
        context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)
            .edit { putBoolean("welcome_done", true) }
        showWelcome = false
    }
    BackHandler(enabled = showWelcome) { }
    val showTour by vm.showTour.collectAsState()
    val tourTargets = remember { TourTargets() }
    val selection = remember { SelectionState() }
    val accounts by vm.accounts.collectAsState()
    var showSetAccount by remember { mutableStateOf(false) }
    var showStats by rememberSaveable { mutableStateOf(false) }

    val lastCount = remember { mutableIntStateOf(0) }
    if (selection.count > 0) lastCount.intValue = selection.count

    LaunchedEffect(tab) { selection.clear() }
    BackHandler(enabled = selection.active) { selection.clear() }
    BackHandler(enabled = showStats) { showStats = false }

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

    val deleteCategoryWithUndo: (Category) -> Unit = { c ->
        vm.deleteCategory(c)
        showUndo("Category deleted") { vm.restoreCategory(c) }
    }

    val deleteButtonWithUndo: (QuickButton) -> Unit = { b ->
        vm.deleteQuickButton(b)
        showUndo("Quick button deleted") { vm.restoreQuickButton(b) }
    }

    val deleteAccountWithUndo: (Account) -> Unit = { a ->
        vm.deleteAccount(a)
        showUndo("Account deleted") { vm.restoreAccount(a) }
    }

    val deleteRecurringWithUndo: (Recurring) -> Unit = { r ->
        vm.deleteRecurring(r)
        showUndo("Recurring expense deleted") { vm.restoreRecurring(r) }
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

    val setAccountForSelected: (Long?) -> Unit = { accountId ->
        val list = selection.expenses.values.toList()
        selection.clear()
        showSetAccount = false
        vm.setAccount(list, accountId)
        val name = accounts.firstOrNull { it.id == accountId }?.name
        val message = if (name != null) {
            plural(list.size, "expense") + " set to " + name
        } else {
            "Account cleared on " + plural(list.size, "expense")
        }
        showUndo(message) { vm.restoreAccounts(list) }
    }

    val onSetAccount: (() -> Unit)? =
        if (selection.expenses.isNotEmpty()) ({ showSetAccount = true }) else null

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
        Box(Modifier.fillMaxSize().clearFocusOnTap()) {
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
                            onSetAccount = onSetAccount,
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
                                onClick = { tab = i; showStats = false },
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
                        0 -> DashboardScreen(
                            vm,
                            onSeeAll = { tab = 1 },
                            onOpenStats = { showStats = true },
                            onDelete = deleteWithUndo
                        )
                        1 -> HistoryScreen(vm, onDelete = deleteWithUndo)
                        2 -> PlanScreen(
                            vm,
                            onDeleteBudget = deleteBudgetWithUndo,
                            onDeleteRecurring = deleteRecurringWithUndo
                        )
                        else -> SettingsScreen(
                            vm,
                            onDeleteCategory = deleteCategoryWithUndo,
                            onDeleteAccount = deleteAccountWithUndo,
                            onDeleteButton = deleteButtonWithUndo
                        )
                    }

                    AnimatedVisibility(
                        visible = showStats,
                        modifier = Modifier.fillMaxSize(),
                        enter = slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)),
                        exit = slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(200))
                    ) {
                        StatisticsScreen(
                            vm = vm,
                            onBack = { showStats = false },
                            onOpenHistory = {
                                showStats = false
                                tab = 1
                            }
                        )
                    }
                }
            }
            if (showSetAccount && selection.expenses.isNotEmpty()) {
                SetAccountDialog(
                    count = selection.expenses.size,
                    accounts = accounts,
                    onDismiss = { showSetAccount = false },
                    onPick = setAccountForSelected
                )
            }
            AnimatedVisibility(
                visible = showTour && !showWelcome,
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
            AnimatedVisibility(
                visible = showWelcome,
                modifier = Modifier.fillMaxSize(),
                exit = fadeOut(tween(300))
            ) {
                WelcomeScreen(
                    animations = AnimationStore.enabled,
                    busy = signingIn,
                    signInButton = {
                        GoogleSignInButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !signingIn,
                            onClick = {
                                signingIn = true
                                scope.launch {
                                    val result = linkGoogleAccount(context)
                                    result.onSuccess {
                                        GoogleAccountStore.save(context, it)
                                        restoreEmail = it.email
                                    }
                                    result.onFailure { error ->
                                        googleErrorMessage(error)?.let {
                                            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                                        }
                                        signingIn = false
                                    }
                                }
                            }
                        )
                    },
                    onGuest = finishWelcome
                )
            }
            restoreEmail?.let { email ->
                AutoRestore(email) { restored ->
                    if (restored) vm.finishTour() // existing user: no tutorial
                    finishWelcome()
                    restoreEmail = null
                    signingIn = false
                }
            }
        }
    }
}

@Composable
fun AutoRestore(email: String, onFinished: (restored: Boolean) -> Unit) {
    val context = LocalContext.current
    val runDrive = rememberDriveRunner(
        email,
        onBusy = { },
        onError = {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            onFinished(false)
        }
    )
    LaunchedEffect(Unit) {
        runDrive { token ->
            val remote = DriveBackup.find(token)
            if (remote == null) {
                onFinished(false) // new user: continue to the tutorial
            } else {
                val json = DriveBackup.download(token, remote.id)
                restoreFromJson(context, json)
                onFinished(true)
            }
        }
    }
}