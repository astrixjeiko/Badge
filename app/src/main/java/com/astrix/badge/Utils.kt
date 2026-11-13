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