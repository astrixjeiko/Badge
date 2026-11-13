package com.astrix.badge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                var note by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("Add expense") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AmountField(text, { text = it })
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text("Label (e.g. Lunch)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val a = text.toDoubleOrNull()
                            if (a != null && a > 0) {
                                lifecycleScope.launch {
                                    dao.insertExpense(
                                        Expense(amount = a, categoryId = null, note = note.trim())
                                    )
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