package com.astrix.badge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrix.badge.ui.theme.BadgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CurrencyStore.load(this)
        setContent { BadgeTheme { BadgeApp() } }
    }
}

@Composable
fun BadgeApp(vm: BadgeViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Home" to Icons.Default.Home,
        "Budgets" to Icons.Default.AccountBalanceWallet,
        "Settings" to Icons.Default.Settings
    )
    Scaffold(bottomBar = {
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
    }) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> DashboardScreen(vm)
                1 -> BudgetScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}