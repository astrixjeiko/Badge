package com.astrix.badge

import java.util.Calendar
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

const val MAX_QUICK_BUTTONS = 8

object CurrencyStore {
    val options = listOf("₱", "$", "€", "£", "¥", "₩", "₹")

    var symbol by mutableStateOf("₱")
        private set

    fun load(context: Context) {
        symbol = context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)
            .getString("currency", "₱") ?: "₱"
    }

    fun set(context: Context, value: String) {
        context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)
            .edit { putString("currency", value) }
        symbol = value
    }
}

fun money(v: Double): String = CurrencyStore.symbol + "%,.2f".format(v)

fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))

object Periods {
    // Returns the start time (in ms) of the current day/week/month/year
    fun startOf(period: String): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        when (period) {
            "WEEK" -> c.set(Calendar.DAY_OF_WEEK, c.firstDayOfWeek)
            "MONTH" -> c.set(Calendar.DAY_OF_MONTH, 1)
            "YEAR" -> c.set(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }
}

// "20.00" style text for editing (always uses a dot, whatever the phone's language)
fun plainAmount(v: Double): String = String.format(Locale.US, "%.2f", v)

fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))

// The date picker works in UTC, so these two convert to and from your local time
fun toPickerMillis(timestamp: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = timestamp }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

fun fromPickerMillis(pickerMillis: Long, originalTimestamp: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickerMillis }
    return Calendar.getInstance().apply {
        timeInMillis = originalTimestamp
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

fun formatClock(timestamp: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

fun hourOf(timestamp: Long): Int =
    Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)

fun minuteOf(timestamp: Long): Int =
    Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.MINUTE)

fun withTime(timestamp: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }.timeInMillis