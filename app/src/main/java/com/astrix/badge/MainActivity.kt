package com.astrix.badge

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val start = SystemClock.uptimeMillis()
            splash.setKeepOnScreenCondition { SystemClock.uptimeMillis() - start < 700 }
        }

        enableEdgeToEdge()
        CurrencyStore.load(this)
        setContent { BadgeTheme { BadgeApp() } }
    }
}

@Composable
fun BadgeApp(vm: BadgeViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Deletes an expense, then offers Undo for a few seconds
    val deleteWithUndo: (Expense) -> Unit = { e ->
        vm.deleteExpense(e)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = "Expense deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) vm.restoreExpense(e)
        }
    }

    val tabs = listOf(
        "Home" to Icons.Default.Home,
        "History" to Icons.Default.History,
        "Budgets" to Icons.Default.AccountBalanceWallet,
        "Settings" to Icons.Default.Settings
    )
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> DashboardScreen(vm, onSeeAll = { tab = 1 }, onDelete = deleteWithUndo)
                1 -> HistoryScreen(vm, onDelete = deleteWithUndo)
                2 -> BudgetScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}