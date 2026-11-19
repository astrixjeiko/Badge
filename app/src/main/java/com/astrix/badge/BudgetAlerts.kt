package com.astrix.badge

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit

object BudgetAlerts {
    private const val CHANNEL_ID = "budget_alerts"
    private const val ID_BASE = 1_000_000
    const val EXTRA_OPEN_TAB = "open_tab"

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "Tells you when an expense puts you over a budget"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, budget: Budget, name: String, spent: Double) {
        if (!canPost(context)) return
        CurrencyStore.load(context)
        ensureChannel(context)

        val whenText = when (budget.period) {
            "DAY" -> "today"
            "WEEK" -> "this week"
            "YEAR" -> "this year"
            else -> "this month"
        }

        // Tapping the notification opens the app on the Budgets tab
        val openBudgets = PendingIntent.getActivity(
            context, ID_BASE + budget.id.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_TAB, 2)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Over budget: $name")
            .setContentText(
                "You've spent " + money(spent) + " of your " + money(budget.limitAmount) +
                        " limit " + whenText + "."
            )
            .setContentIntent(openBudgets)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(ID_BASE + budget.id.toInt(), notification)
    }
}

// Call this right after an expense is added OUTSIDE the app (widget, pop-up, notification button).
// It notifies for every budget that this expense pushed from under its limit to over it.
suspend fun notifyIfOverBudget(context: Context, expense: Expense) {
    val prefs = context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)
    if (!prefs.getBoolean("budget_alerts", true)) return

    val dao = BadgeDatabase.get(context).dao()
    for (b in dao.getBudgets()) {
        if (b.limitAmount <= 0) continue

        // Does this expense count toward the budget (right category, inside the current period)?
        val periodStart = Periods.startOf(b.period)
        val counts = (b.categoryId == null || b.categoryId == expense.categoryId) &&
                expense.timestamp >= periodStart
        if (!counts) continue

        val after = dao.getSpent(periodStart, b.categoryId)
        val before = after - expense.amount
        if (b.limitAmount in before..<after) {
            val category = b.categoryId?.let { dao.getCategoryById(it) }
            val name = b.name.ifBlank { category?.display() ?: "All spending" }
            BudgetAlerts.show(context, b, name, after)
        }
    }
}

// Returns a function that turns notifications on: the first call asks for permission (Android 13+),
// later calls open the phone's notification settings for Badge
@Composable
fun rememberNotificationEnabler(onResult: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult() }

    return {
        val prefs = context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("asked_notifications", false)) {
            prefs.edit { putBoolean("asked_notifications", true) }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }
}