package com.astrix.badge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
                val categories by dao.observeCategories().collectAsState(initial = emptyList())
                var cat by remember { mutableStateOf<Long?>(null) }
                var note by remember { mutableStateOf("") }
                var text by remember { mutableStateOf("") }

                AlertDialog(
                    onDismissRequest = { finishAndRemoveTask() },
                    title = { Text("Add expense") },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            CategoryChips(categories, cat) { cat = it }
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text("Name (e.g. Lunch)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            AmountField(text, { text = it }, Modifier.fillMaxWidth())
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val a = text.toDoubleOrNull()
                            if (a != null && a > 0) {
                                lifecycleScope.launch {
                                    dao.insertExpense(
                                        Expense(amount = a, categoryId = cat, note = note.trim())
                                    )
                                    BadgeWidget().updateAll(applicationContext)
                                    finishAndRemoveTask()
                                }
                            }
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { finishAndRemoveTask() }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}