package com.astrix.badge

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

// Redraws both widgets; call this whenever an expense changes
suspend fun refreshAllWidgets(context: Context) {
    BadgeWidget().updateAll(context)
    BadgeMiniWidget().updateAll(context)
}

// The small 2x1 widget: today's total and a button for custom amounts
class BadgeMiniWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        CurrencyStore.load(context)
        val dao = BadgeDatabase.get(context).dao()
        provideContent {
            val today by dao.observeTotalSince(Periods.startOf("DAY")).collectAsState(initial = 0.0)
            GlanceTheme(colors = BadgeWidgetColors) {
                MiniWidgetContent(today)
            }
        }
    }
}

class BadgeMiniWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BadgeMiniWidget()
}

@Composable
fun MiniWidgetContent(today: Double) {
    val context = LocalContext.current
    // On launchers where a one-row widget is short, the bottom line is dropped
    val showHint = LocalSize.current.height >= 66.dp

    // Bigger totals get a smaller font, so they never get cut off
    val totalText = money(today)
    val totalSize = when {
        totalText.length <= 8 -> 22.sp
        totalText.length <= 10 -> 19.sp
        else -> 16.sp
    }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(24.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                "TODAY",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
            Text(
                totalText,
                maxLines = 1,
                style = TextStyle(
                    fontSize = totalSize,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.primary
                )
            )
            if (showHint) {
                Text(
                    "Tap + to add",
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        }

        Spacer(GlanceModifier.width(6.dp))

        Box(
            modifier = GlanceModifier
                .size(40.dp)
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(20.dp)
                .clickable(actionStartActivity(Intent(context, QuickAddActivity::class.java))),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_add),
                contentDescription = "Add expense",
                modifier = GlanceModifier.size(22.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
            )
        }
    }
}