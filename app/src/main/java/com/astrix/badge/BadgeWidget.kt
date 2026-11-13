package com.astrix.badge

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

val ButtonIdKey = ActionParameters.Key<Long>("buttonId")

class BadgeWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        CurrencyStore.load(context)
        val dao = BadgeDatabase.get(context).dao()
        provideContent {
            val allButtons by dao.observeQuickButtons().collectAsState(initial = emptyList())
            val expenses by dao.observeExpenses().collectAsState(initial = emptyList())
            val start = Periods.startOf("DAY")
            val today = expenses.filter { it.timestamp >= start }.sumOf { it.amount }
            GlanceTheme { WidgetContent(allButtons.take(4), today) }
        }
    }
}

@Composable
fun WidgetContent(buttons: List<QuickButton>, today: Double) {
    Column(
        GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp)
    ) {
        Text(
            "Today: " + money(today),
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onSurface
            )
        )
        Spacer(GlanceModifier.height(8.dp))
        buttons.chunked(2).forEach { pair ->
            Row(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                pair.forEach { b ->
                    Button(
                        text = b.label + " " + money(b.amount),
                        onClick = actionRunCallback<QuickAddAction>(
                            actionParametersOf(ButtonIdKey to b.id)
                        ),
                        modifier = GlanceModifier.defaultWeight().padding(end = 4.dp)
                    )
                }
            }
        }
        Button(
            text = "Other amount",
            onClick = actionStartActivity<QuickAddActivity>(),
            modifier = GlanceModifier.fillMaxWidth()
        )
    }
}

// Runs when a quick button on the widget is tapped
class QuickAddAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ButtonIdKey] ?: return
        val dao = BadgeDatabase.get(context).dao()
        val b = dao.getQuickButtons().firstOrNull { it.id == id } ?: return
        dao.insertExpense(Expense(amount = b.amount, categoryId = b.categoryId, note = b.label))
        BadgeWidget().updateAll(context)
    }
}

class BadgeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BadgeWidget()
}