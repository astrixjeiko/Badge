package com.astrix.badge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.astrix.badge.ui.theme.BadgeTheme
import kotlinx.coroutines.launch

class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = BadgeDatabase.get(this).dao()
        setContent {
            BadgeTheme {
                var text by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("Add expense") },
                    text = { AmountField(text, { text = it }) },
                    confirmButton = {
                        TextButton(onClick = {
                            val a = text.toDoubleOrNull()
                            if (a != null && a > 0) {
                                lifecycleScope.launch {
                                    dao.insertExpense(Expense(amount = a, categoryId = null))
                                    BadgeWidget().updateAll(applicationContext)
                                    finish()
                                }
                            }
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { finish() }) { Text("Cancel") } }
                )
            }
        }
    }
}