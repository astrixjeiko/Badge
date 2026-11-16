package com.astrix.badge

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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

    CompositionLocalProvider(LocalTourTargets provides tourTargets) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
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
                        2 -> BudgetScreen(vm)
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