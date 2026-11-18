package com.astrix.badge

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Periods
enum class InsightPeriod(val label: String) {
    WEEK("This week"),
    MONTH("This month"),
    LAST_MONTH("Last month"),
    YEAR("This year")
}

data class InsightRange(
    val start: Long,
    val end: Long,
    val prevStart: Long,
    val prevEnd: Long,
    val days: Int,
    val name: String,
    val prevName: String
)

// Everything the Insights card shows
data class InsightsState(
    val range: InsightRange,
    val categories: List<CategoryTotal>,
    val stats: RangeStats,
    val prevTotal: Double,
    val biggest: Expense?,
    val weekday: WeekdayTotal?
)

private class Spans(
    val start: LocalDate,
    val end: LocalDate,
    val prevStart: LocalDate,
    val name: String,
    val prevName: String
)

fun rangeFor(period: InsightPeriod, now: Long = System.currentTimeMillis()): InsightRange {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    val s = when (period) {
        InsightPeriod.WEEK -> {
            val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
            val start = today.with(TemporalAdjusters.previousOrSame(first))
            Spans(start, start.plusWeeks(1), start.minusWeeks(1), "this week", "the same days last week")
        }
        InsightPeriod.MONTH -> {
            val start = today.withDayOfMonth(1)
            Spans(start, start.plusMonths(1), start.minusMonths(1), "this month", "the same days last month")
        }
        InsightPeriod.LAST_MONTH -> {
            val start = today.withDayOfMonth(1).minusMonths(1)
            Spans(start, start.plusMonths(1), start.minusMonths(1), "last month", "the month before")
        }
        InsightPeriod.YEAR -> {
            val start = today.withDayOfYear(1)
            Spans(start, start.plusYears(1), start.minusYears(1), "this year", "the same days last year")
        }
    }

    val tomorrow = today.plusDays(1)
    val upTo = if (tomorrow.isBefore(s.end)) tomorrow else s.end
    val days = ChronoUnit.DAYS.between(s.start, upTo).toInt().coerceAtLeast(1)

    val sameStretchEnd = s.prevStart.plusDays(days.toLong())
    val prevEnd = if (sameStretchEnd.isBefore(s.start)) sameStretchEnd else s.start

    fun ms(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
    return InsightRange(ms(s.start), ms(s.end), ms(s.prevStart), ms(prevEnd), days, s.name, s.prevName)
}

// Animation helpers
@Composable
fun animatedFloat(target: Float, delayMs: Int, label: String): State<Float> {
    val spec: AnimationSpec<Float> = if (AnimationStore.enabled) {
        tween(durationMillis = 700, delayMillis = delayMs, easing = FastOutSlowInEasing)
    } else {
        snap()
    }
    return animateFloatAsState(targetValue = target, animationSpec = spec, label = label)
}

@Composable
fun animatedAmount(exact: Double, visible: Boolean, delayMs: Int): Double {
    val target = if (visible) exact.toFloat() else 0f
    val shown by animatedFloat(target, delayMs, "amount")
    return if (visible && shown == target) exact else shown.toDouble()
}

// The Insights card
class BarRow(
    val key: String,
    val label: String,
    val amount: Double,
    val categoryId: Long?,
    val rank: Int
)

fun buildRows(totals: List<CategoryTotal>, byId: Map<Long, Category>): List<BarRow> {
    val positive = totals.filter { it.total > 0 }
    val rows = positive.take(4).mapIndexed { index, t ->
        val category = t.categoryId?.let { byId[it] }
        BarRow(
            key = if (t.categoryId == null) "none" else "c" + t.categoryId,
            label = category?.display() ?: "No category",
            amount = t.total,
            categoryId = category?.id,
            rank = index
        )
    }.toMutableList()

    val rest = positive.drop(4)
    if (rest.isNotEmpty()) {
        rows.add(BarRow("other", "Other (" + rest.size + ")", rest.sumOf { it.total }, null, 4))
    }
    return rows
}

private class Tile(val label: String, val value: String, val sub: String)

private fun weekdayName(sqliteWeekday: Int): String {
    val day = if (sqliteWeekday == 0) DayOfWeek.SUNDAY else DayOfWeek.of(sqliteWeekday)
    return day.getDisplayName(JavaTextStyle.FULL, Locale.getDefault()) + "s"
}

@Composable
fun InsightsSection(
    period: InsightPeriod,
    state: InsightsState,
    categories: List<Category>,
    tour: TourTargets,
    onPeriod: (InsightPeriod) -> Unit,
    onCategoryClick: (Long) -> Unit,
    onSeeAll: () -> Unit
) {
    val byId = remember(categories) { categories.associateBy { it.id } }
    val rows = remember(state.categories, byId) { buildRows(state.categories, byId) }
    val stats = state.stats
    val range = state.range

    var appeared by remember { mutableStateOf(!AnimationStore.enabled) }
    LaunchedEffect(Unit) { appeared = true }

    // Heading and period switch (the tutorial points here)
    Column(Modifier.tourTarget(tour, TourTarget.INSIGHTS)) {
        Text("Insights", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InsightPeriod.entries.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { onPeriod(p) },
                    label = { Text(p.label) }
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (stats.expenseCount == 0) {
                Text(
                    "No expenses " + range.name + ".",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val shownTotal = animatedAmount(stats.total, appeared, 0)
                Text(
                    "Spent " + range.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    money(shownTotal),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                CompareLine(stats.total, state.prevTotal, range.prevName)

                // Bars
                Spacer(Modifier.height(16.dp))
                Text(
                    "By category",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val maxAmount = rows.maxOfOrNull { it.amount } ?: 0.0
                rows.forEachIndexed { index, row ->
                    key(row.key) {
                        val id = row.categoryId
                        InsightBarRow(
                            row = row,
                            maxAmount = maxAmount,
                            total = stats.total,
                            index = index,
                            onClick = if (id != null) ({ onCategoryClick(id) }) else null
                        )
                    }
                }

                // Tiles
                Spacer(Modifier.height(8.dp))
                val biggest = state.biggest
                val weekday = state.weekday
                val tiles = buildList {
                    add(Tile("Per day", money(stats.total / range.days), "over " + plural(range.days, "day")))
                    add(
                        Tile(
                            "Expenses",
                            stats.expenseCount.toString(),
                            "avg " + money(stats.total / stats.expenseCount)
                        )
                    )
                    if (biggest != null) {
                        val category = biggest.categoryId?.let { byId[it] }
                        add(
                            Tile(
                                "Biggest",
                                money(biggest.amount),
                                biggest.note.ifBlank { category?.name ?: "Expense" }
                            )
                        )
                    }
                    if (period != InsightPeriod.WEEK && stats.expenseCount >= 10 && weekday != null) {
                        add(Tile("Busiest day", weekdayName(weekday.weekday), money(weekday.total)))
                    }
                }
                tiles.chunked(2).forEachIndexed { rowIndex, pair ->
                    if (rowIndex > 0) Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { StatTile(it, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onSeeAll,
                modifier = Modifier.align(Alignment.End)
            ) { Text("See all statistics") }
        }
    }
}

@Composable
fun CompareLine(total: Double, prevTotal: Double, prevName: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val diff = total - prevTotal
    when {
        prevTotal <= 0.0 -> Text(
            "Nothing to compare with $prevName.",
            style = MaterialTheme.typography.bodySmall,
            color = muted
        )
        abs(diff) < 0.005 -> Text(
            "Same as $prevName.",
            style = MaterialTheme.typography.bodySmall,
            color = muted
        )
        else -> {
            val less = diff < 0
            val percent = (abs(diff) / prevTotal * 100).roundToInt()
            // Spending less is good news (teal), spending more is a warning (red)
            val color = if (less) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (less) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    (if (percent == 0) "<1%" else "$percent%") +
                            (if (less) " less" else " more") + " than " + prevName,
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun InsightBarRow(
    row: BarRow,
    maxAmount: Double,
    total: Double,
    index: Int,
    onClick: (() -> Unit)?
) {
    var visible by remember { mutableStateOf(!AnimationStore.enabled) }
    LaunchedEffect(Unit) { visible = true }

    val delay = index * 80
    val shownAmount = animatedAmount(row.amount, visible, delay)
    val barTarget = if (visible && maxAmount > 0) (row.amount / maxAmount).toFloat() else 0f
    val bar by animatedFloat(barTarget, delay, "bar")
    val percent = if (total > 0) (shownAmount / total * 100).roundToInt() else 0

    val scheme = MaterialTheme.colorScheme
    val base = if (row.categoryId == null) scheme.onSurfaceVariant else scheme.primary
    val color = base.copy(alpha = (1f - row.rank * 0.18f).coerceAtLeast(0.35f))

    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                money(shownAmount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "$percent%",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(44.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(scheme.onSurface.copy(alpha = 0.08f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(bar.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun StatTile(tile: Tile, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(12.dp)
    ) {
        Text(
            tile.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            tile.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            tile.sub,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}