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
const val RECENT_LIMIT = 5

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

// "20.00" style text for editing (always uses a dot, whatever the device's language)
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

sealed class HistoryItem {
    data class Header(val dayStart: Long) : HistoryItem()
    data class Entry(val expense: Expense) : HistoryItem()
}

fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun dayLabel(dayStart: Long): String {
    val today = startOfDay(System.currentTimeMillis())
    val yesterday = startOfDay(today - 3_600_000L)
    return when (dayStart) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(dayStart))
    }
}

enum class SortOrder(val label: String, val code: Int) {
    NEWEST("Newest first", 0),
    OLDEST("Oldest first", 1),
    HIGHEST("Highest amount", 2),
    LOWEST("Lowest amount", 3)
}

enum class DateRange(val label: String) {
    ALL("All time"),
    TODAY("Today"),
    WEEK("This week"),
    MONTH("This month"),
    CUSTOM("Custom")
}

data class HistoryFilter(
    val query: String = "",
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val range: DateRange = DateRange.ALL,
    val customFrom: Long = 0L,
    val customTo: Long = Long.MAX_VALUE,
    val sort: SortOrder = SortOrder.NEWEST
)

// Start (inclusive) and end (exclusive) of the chosen date range, in milliseconds
fun HistoryFilter.bounds(): Pair<Long, Long> = when (range) {
    DateRange.ALL -> 0L to Long.MAX_VALUE
    DateRange.TODAY -> Periods.startOf("DAY") to Long.MAX_VALUE
    DateRange.WEEK -> Periods.startOf("WEEK") to Long.MAX_VALUE
    DateRange.MONTH -> Periods.startOf("MONTH") to Long.MAX_VALUE
    DateRange.CUSTOM -> customFrom to customTo
}

// The range picker returns UTC dates; these turn them into local day boundaries
fun pickerDayStart(pickerMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickerMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

fun nextDayStart(timestamp: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = timestamp
        add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

fun rangeLabel(from: Long, to: Long): String {
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    val a = fmt.format(Date(from))
    val b = fmt.format(Date(to - 1))
    return if (a == b) a else "$a – $b"
}

// Matches the "day" text produced by the database query, e.g. "2026-11-14"
fun dayKey(dayStart: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dayStart))

fun Category.display(): String = if (icon.isBlank()) name else "$icon $name"

// Shortens long text with "…" (counts emoji as one character)
fun String.ellipsize(maxChars: Int): String {
    if (codePointCount(0, length) <= maxChars) return this
    return substring(0, offsetByCodePoints(0, maxChars - 1)).trimEnd() + "…"
}

fun startOfDaysAgo(days: Int): Long = Calendar.getInstance().apply {
    timeInMillis = Periods.startOf("DAY")
    add(Calendar.DAY_OF_YEAR, -days)
}.timeInMillis

// What a "full" glass means on Home: the daily budget, or else the usual daily spending
data class DailyReference(val amount: Double, val isBudget: Boolean)