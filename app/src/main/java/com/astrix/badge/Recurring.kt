package com.astrix.badge

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

const val RECURRING_HOUR = 9

private fun dueIn(year: Int, month: Int, dayOfMonth: Int): Long {
    val c = Calendar.getInstance()
    c.clear()
    c.set(year, month, 1, RECURRING_HOUR, 0, 0)
    c.set(Calendar.DAY_OF_MONTH, minOf(dayOfMonth, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
    return c.timeInMillis
}

fun firstDueFrom(dayOfMonth: Int, from: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = from }
    var year = c.get(Calendar.YEAR)
    var month = c.get(Calendar.MONTH)
    var due = dueIn(year, month, dayOfMonth)
    if (due < from) {
        month += 1
        if (month > 11) { month = 0; year += 1 }
        due = dueIn(year, month, dayOfMonth)
    }
    return due
}

fun dueAfter(dayOfMonth: Int, current: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = current }
    var year = c.get(Calendar.YEAR)
    var month = c.get(Calendar.MONTH) + 1
    if (month > 11) { month = 0; year += 1 }
    return dueIn(year, month, dayOfMonth)
}

fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) {
        "th"
    } else {
        when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    }
    return n.toString() + suffix
}

suspend fun confirmDueRecurring(context: Context, id: Long, alertBudgets: Boolean = false) {
    val dao = BadgeDatabase.get(context).dao()
    val r = dao.getRecurringById(id) ?: return
    if (r.nextDue > System.currentTimeMillis()) return
    val expense = Expense(
        amount = r.amount,
        categoryId = r.categoryId,
        note = r.name,
        timestamp = r.nextDue,
        accountId = r.accountId
    )
    dao.insertExpense(expense)
    dao.updateRecurring(r.copy(nextDue = dueAfter(r.dayOfMonth, r.nextDue)))
    NotificationManagerCompat.from(context).cancel(r.id.toInt())
    refreshAllWidgets(context)
    if (alertBudgets) notifyIfOverBudget(context, expense)
}

suspend fun skipDueRecurring(context: Context, id: Long) {
    val dao = BadgeDatabase.get(context).dao()
    val r = dao.getRecurringById(id) ?: return
    if (r.nextDue > System.currentTimeMillis()) return
    dao.updateRecurring(r.copy(nextDue = dueAfter(r.dayOfMonth, r.nextDue)))
    NotificationManagerCompat.from(context).cancel(r.id.toInt())
}

object RecurringNotifications {
    private const val CHANNEL_ID = "recurring"
    const val ACTION_ADD = "com.astrix.badge.RECURRING_ADD"
    const val ACTION_SKIP = "com.astrix.badge.RECURRING_SKIP"
    const val EXTRA_ID = "recurring_id"

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Recurring expenses", NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "Reminders to add your monthly bills and subscriptions"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun actionIntent(context: Context, action: String, id: Long, slot: Int): PendingIntent {
        val intent = Intent(context, RecurringActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context, (id * 10 + slot).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, r: Recurring): Boolean {
        if (!canPost(context)) return false
        CurrencyStore.load(context)
        ensureChannel(context)

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(r.name + " is due")
            .setContentText(money(r.amount) + ". Add it to your expenses?")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .addAction(0, "Add", actionIntent(context, ACTION_ADD, r.id, 1))
            .addAction(0, "Skip", actionIntent(context, ACTION_SKIP, r.id, 2))
            .build()

        NotificationManagerCompat.from(context).notify(r.id.toInt(), notification)
        return true
    }
}

class RecurringActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(RecurringNotifications.EXTRA_ID, -1L)
        if (id < 0) return
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    RecurringNotifications.ACTION_ADD -> confirmDueRecurring(context, id, alertBudgets = true)
                    RecurringNotifications.ACTION_SKIP -> skipDueRecurring(context, id)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

// Hourly check
class RecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dao = BadgeDatabase.get(applicationContext).dao()
        val now = System.currentTimeMillis()
        dao.getRecurring()
            .filter { it.enabled && it.nextDue <= now && it.notifiedFor != it.nextDue }
            .forEach { r ->
                if (RecurringNotifications.show(applicationContext, r)) {
                    dao.updateRecurring(r.copy(notifiedFor = r.nextDue))
                }
            }
        return Result.success()
    }
}

fun scheduleRecurringChecks(context: Context) {
    val request = PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.HOURS).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "recurring-check", ExistingPeriodicWorkPolicy.KEEP, request
    )
}