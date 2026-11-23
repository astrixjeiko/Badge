package com.astrix.badge

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val AUTO_WORK = "auto_backup"

// Call whenever the setting, the account or the app start changes
fun scheduleAutoBackup(context: Context) {
    val prefs = context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)
    val on = prefs.getString("auto_backup", "off") == "daily"
    val email = prefs.getString("google_email", null)
    val manager = WorkManager.getInstance(context)
    if (!on || email.isNullOrBlank()) {
        manager.cancelUniqueWork(AUTO_WORK)
        return
    }
    val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
        .setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        )
        .build()
    manager.enqueueUniquePeriodicWork(AUTO_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
}

// One backup to Drive: replaces the previous copy
suspend fun runBackup(context: Context, token: String) {
    CurrencyStore.load(context)
    val json = exportBackupJson(context)
    DriveBackup.upload(token, json)
    DriveBackup.prune(token)
    saveLastBackupTime(context, System.currentTimeMillis())
}

class AutoBackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)
        val email = prefs.getString("google_email", null)
        if (email.isNullOrBlank()) return Result.success()
        return try {
            val token = silentDriveToken(context, email)
            if (token == null) {
                prefs.edit { putString("auto_backup_error", "needs_permission") }
                return Result.success()
            }
            runBackup(context, token)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w("BadgeBackup", "Automatic backup will retry", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e("BadgeBackup", "Automatic backup failed", e)
            Result.success() // try again at the next scheduled run
        }
    }
}