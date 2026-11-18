package com.astrix.badge

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

// Extra numbers for the Statistics screen
data class StatsExtras(
    val range: InsightRange,
    val byMonth: Boolean,
    val buckets: List<DayTotal>,
    val accounts: List<AccountTotal>,
    val untagged: UntaggedCounts,
    val spendDays: Int
)

@Composable
fun StatisticsScreen(vm: BadgeViewModel, onBack: () -> Unit, onOpenHistory: () -> Unit) {
    val state by vm.insights.collectAsState()
    val extras by vm.statsExtras.collectAsState()
    val period by vm.insightPeriod.collectAsState()
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val recurring by vm.recurring.collectAsState()

    val range = state.range
    val stats = state.stats
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    val accountById = remember(accounts) { accounts.associateBy { it.id } }
    val rows = remember(state.categories, categoryById) { buildRows(state.categories, categoryById) }
    val bars = remember(extras, period) { buildStatBars(period, extras.range, extras.byMonth, extras.buckets) }

    var selectedSlice by remember(rows) { mutableStateOf<Int?>(null) }
    var selectedBar by remember(extras) { mutableStateOf<String?>(null) }

    // The headline counts up the first time the screen appears
    var appeared by remember { mutableStateOf(!AnimationStore.enabled) }
    LaunchedEffect(Unit) { appeared = true }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Statistics", style = MaterialTheme.typography.titleLarge)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InsightPeriod.entries.forEach { p ->
                            FilterChip(
                                selected = period == p,
                                onClick = { vm.setInsightPeriod(p) },
                                label = { Text(p.label) }
                            )
                        }
                    }
                }

                if (stats.expenseCount == 0) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Text(
                                "No expenses " + range.name + ".",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    // Overview
                    item {
                        StatSection("Overview") {
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
                        }
                    }

                    item {
                        StatSection("By category") {
                            val picked = selectedSlice?.let { rows.getOrNull(it) }
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                StatDonut(
                                    rows = rows,
                                    total = stats.total,
                                    selected = selectedSlice,
                                    onSelect = { selectedSlice = it }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            val sum = rows.sumOf { it.amount }.coerceAtLeast(0.01)
                            val neutral = MaterialTheme.colorScheme.onSurfaceVariant
                            rows.forEachIndexed { index, row ->
                                DonutLegendRow(
                                    label = row.label,
                                    color = donutColor(index, row, neutral),
                                    amount = row.amount,
                                    percent = (row.amount / sum * 100).roundToInt(),
                                    selected = selectedSlice == index,
                                    onClick = { selectedSlice = if (selectedSlice == index) null else index }
                                )
                            }
                            val categoryId = picked?.categoryId
                            if (categoryId != null) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        vm.updateFilter {
                                            HistoryFilter(
                                                sort = it.sort,
                                                categoryId = categoryId,
                                                range = DateRange.CUSTOM,
                                                customFrom = range.start,
                                                customTo = range.end
                                            )
                                        }
                                        onOpenHistory()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("See expenses in History") }
                            }
                        }
                    }

                    // Daily (or monthly) bars
                    item {
                        StatSection(if (extras.byMonth) "Monthly spending" else "Daily spending") {
                            val selected = bars.firstOrNull { it.key == selectedBar }
                            Text(
                                if (selected != null) {
                                    selected.title + " · " + money(selected.total)
                                } else {
                                    "Tap a bar for details"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            StatChart(
                                bars = bars,
                                average = if (extras.byMonth) null else stats.total / range.days,
                                selectedKey = selectedBar,
                                onSelect = { selectedBar = it }
                            )
                            if (!extras.byMonth) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Line: average of " + money(stats.total / range.days) + " per day",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // By account
                    item {
                        StatSection("By account") {
                            StatAccounts(extras.accounts, accountById)
                        }
                    }

                    // Projection
                    if (period != InsightPeriod.LAST_MONTH) {
                        item {
                            StatSection("Projection") {
                                StatProjection(period, state, budgets)
                            }
                        }
                    }

                    // Habits
                    item {
                        StatSection("Habits") {
                            val noSpend = (range.days - extras.spendDays).coerceAtLeast(0)
                            val active = recurring.filter { it.enabled }
                            val tiles = buildList {
                                add(
                                    StatsTileData(
                                        "No-spend days",
                                        noSpend.toString(),
                                        "of " + plural(range.days, "day")
                                    )
                                )
                                if (active.isNotEmpty()) {
                                    add(
                                        StatsTileData(
                                            "Recurring bills",
                                            money(active.sumOf { it.amount }),
                                            "per month · " + plural(active.size, "item")
                                        )
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                tiles.forEach { StatsTile(it, Modifier.weight(1f)) }
                                if (tiles.size == 1) Spacer(Modifier.weight(1f))
                            }

                            val u = extras.untagged
                            val notes = mutableListOf<String>()
                            if (u.noCategory > 0) notes.add(plural(u.noCategory, "expense") + " without a category")
                            if (u.noAccount > 0) notes.add(plural(u.noAccount, "expense") + " without an account")
                            if (notes.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    notes.joinToString(" · ") + ".",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (u.noAccount > 0) {
                                    Text(
                                        "Tip: in History, select expenses and tap the tag icon to set an account for all of them at once.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// A titled card
@Composable
private fun StatSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

// Donut chart
private val DonutPalette = listOf(
    Color(0xFF2EC4A9), Color(0xFF5B8DEF), Color(0xFFB583E6),
    Color(0xFFF2B84B), Color(0xFFEF7A9B), Color(0xFF7BC96F)
)

// "Other" and "No category" are gray; real categories get a color from the palette
private fun donutColor(index: Int, row: BarRow, neutral: Color): Color =
    if (row.categoryId == null) neutral.copy(alpha = 0.6f) else DonutPalette[index % DonutPalette.size]

@Composable
private fun StatDonut(
    rows: List<BarRow>,
    total: Double,
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    var visible by remember { mutableStateOf(!AnimationStore.enabled) }
    LaunchedEffect(Unit) { visible = true }
    val progress by animatedFloat(if (visible) 1f else 0f, 0, "donut")

    val scheme = MaterialTheme.colorScheme
    val colors = rows.mapIndexed { index, row -> donutColor(index, row, scheme.onSurfaceVariant) }
    val sum = rows.sumOf { it.amount }.coerceAtLeast(0.01)
    val ringWidth = 28.dp
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)

    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(rows) {
                    detectTapGestures { offset ->
                        val ringPx = ringWidth.toPx()
                        val dx = offset.x - size.width / 2f
                        val dy = offset.y - size.height / 2f
                        val outer = minOf(size.width, size.height) / 2f
                        if (hypot(dx, dy) in (outer - ringPx)..outer) {
                            var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f
                            if (angle < 0f) angle += 360f
                            var start = 0f
                            var hit: Int? = null
                            rows.forEachIndexed { i, row ->
                                val sweep = (row.amount / sum * 360.0).toFloat()
                                if (hit == null && angle >= start && angle < start + sweep) hit = i
                                start += sweep
                            }
                            currentOnSelect(if (hit == currentSelected) null else hit)
                        } else {
                            currentOnSelect(null)
                        }
                    }
                }
        ) {
            val stroke = ringWidth.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val gap = if (rows.size > 1) 2f else 0f
            var start = -90f
            rows.forEachIndexed { i, row ->
                val sweep = (row.amount / sum * 360.0).toFloat()
                // The ring sweeps in clockwise as `progress` goes from 0 to 1
                val drawn = (progress * 360f - (start + 90f)).coerceIn(0f, sweep)
                val alpha = if (currentSelected == null || currentSelected == i) 1f else 0.35f
                if (drawn > gap) {
                    drawArc(
                        color = colors[i].copy(alpha = alpha),
                        startAngle = start + gap / 2f,
                        sweepAngle = drawn - gap,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke)
                    )
                }
                start += sweep
            }
        }

        // The middle: the total, or the selected category
        val picked = selected?.let { rows.getOrNull(it) }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 120.dp)
        ) {
            if (picked != null) {
                Text(
                    picked.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    money(picked.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    (picked.amount / sum * 100).roundToInt().toString() + "%",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Total",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant
                )
                Text(
                    money(total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DonutLegendRow(
    label: String,
    color: Color,
    amount: Double,
    percent: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) scheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            money(amount),
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
}

// Daily (or monthly) bars
private class StatBar(
    val key: String,
    val axis: String,
    val title: String,
    val total: Double,
    val isNow: Boolean,
    val isFuture: Boolean
)

private fun dateOf(ms: Long): LocalDate =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

private fun buildStatBars(
    period: InsightPeriod,
    range: InsightRange,
    byMonth: Boolean,
    buckets: List<DayTotal>
): List<StatBar> {
    val totals = buckets.associate { it.day to it.total }
    val start = dateOf(range.start)
    val end = dateOf(range.end)
    val today = LocalDate.now()
    val bars = mutableListOf<StatBar>()

    if (byMonth) {
        var month = YearMonth.from(start)
        val last = YearMonth.from(end.minusDays(1))
        val thisMonth = YearMonth.from(today)
        while (!month.isAfter(last)) {
            val key = month.toString()
            bars.add(
                StatBar(
                    key = key,
                    axis = month.month.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                    title = month.month.getDisplayName(JavaTextStyle.FULL, Locale.getDefault()) + " " + month.year,
                    total = totals[key] ?: 0.0,
                    isNow = month == thisMonth,
                    isFuture = month.isAfter(thisMonth)
                )
            )
            month = month.plusMonths(1)
        }
    } else {
        val format = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
        var day = start
        while (day.isBefore(end)) {
            val key = day.toString()
            bars.add(
                StatBar(
                    key = key,
                    axis = if (period == InsightPeriod.WEEK) {
                        day.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault())
                    } else {
                        day.dayOfMonth.toString()
                    },
                    title = day.format(format),
                    total = totals[key] ?: 0.0,
                    isNow = day == today,
                    isFuture = day.isAfter(today)
                )
            )
            day = day.plusDays(1)
        }
    }
    return bars
}

@Composable
private fun StatChart(
    bars: List<StatBar>,
    average: Double?,
    selectedKey: String?,
    onSelect: (String?) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val maxTotal = max(bars.maxOfOrNull { it.total } ?: 0.0, 0.01)
    val chartHeight = 140.dp
    val gap = if (bars.size > 16) 2.dp else 6.dp

    Box(Modifier.fillMaxWidth().height(chartHeight)) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEachIndexed { index, bar ->
                key(bar.key) {
                    StatBarColumn(
                        bar = bar,
                        index = index,
                        fraction = (bar.total / maxTotal).toFloat(),
                        selected = selectedKey == bar.key,
                        anySelected = selectedKey != null,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(if (selectedKey == bar.key) null else bar.key)
                        }
                    )
                }
            }
        }

        if (average != null && average > 0) {
            val fraction = (average / maxTotal).toFloat().coerceIn(0f, 1f)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .offset(y = -(chartHeight * fraction))
                    .height(1.dp)
                    .background(scheme.onSurface.copy(alpha = 0.35f))
            )
        }
    }

    // Labels under the bars: one per bar when there are few, otherwise just the first, middle, and last
    Spacer(Modifier.height(4.dp))
    if (bars.size <= 12) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            bars.forEach {
                Text(
                    it.axis,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(bars.first(), bars[bars.size / 2], bars.last()).forEach {
                Text(
                    it.axis,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatBarColumn(
    bar: StatBar,
    index: Int,
    fraction: Float,
    selected: Boolean,
    anySelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    var visible by remember { mutableStateOf(!AnimationStore.enabled) }
    LaunchedEffect(Unit) { visible = true }
    val shown by animatedFloat(if (visible && !bar.isFuture) fraction else 0f, index * 12, "stat-bar")

    val scheme = MaterialTheme.colorScheme
    val alpha = when {
        selected -> 1f
        anySelected -> 0.3f
        bar.isNow -> 0.85f
        else -> 0.5f
    }

    Box(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(Modifier.fillMaxSize().background(scheme.onSurface.copy(alpha = 0.04f)))
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(shown.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(scheme.primary.copy(alpha = alpha))
        )
    }
}

// By account
private class AccountSlice(val account: Account?, val total: Double)

@Composable
private fun StatAccounts(
    accounts: List<AccountTotal>,
    accountById: Map<Long, Account>
) {
    val scheme = MaterialTheme.colorScheme
    val slices = remember(accounts, accountById) {
        accounts.filter { it.total > 0 }.map { AccountSlice(it.accountId?.let { id -> accountById[id] }, it.total) }
    }

    if (slices.all { it.account == null }) {
        Text(
            "Tag your expenses with an account to see how your spending splits between cash, e-wallets, and cards.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        return
    }

    var visible by remember { mutableStateOf(!AnimationStore.enabled) }
    LaunchedEffect(Unit) { visible = true }
    val progress by animatedFloat(if (visible) 1f else 0f, 0, "accounts")
    val sum = slices.sumOf { it.total }.coerceAtLeast(0.01)

    Box(
        Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.onSurface.copy(alpha = 0.08f))
    ) {
        Row(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight()) {
            slices.forEach { slice ->
                Box(
                    Modifier
                        .weight(max(slice.total.toFloat(), 0.001f))
                        .fillMaxHeight()
                        .background(slice.account?.let { Color(it.color) } ?: scheme.onSurfaceVariant.copy(alpha = 0.5f))
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    slices.forEach { slice ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (slice.account != null) {
                    AccountPill(slice.account)
                } else {
                    Text("No account", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                money(slice.total),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                (slice.total / sum * 100).roundToInt().toString() + "%",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(44.dp)
            )
        }
    }
}

// Projection
@Composable
private fun StatProjection(period: InsightPeriod, state: InsightsState, budgets: List<Budget>) {
    val scheme = MaterialTheme.colorScheme
    val range = state.range
    val total = state.stats.total

    val minDays = when (period) {
        InsightPeriod.WEEK -> 2
        InsightPeriod.MONTH -> 3
        InsightPeriod.YEAR -> 14
        InsightPeriod.LAST_MONTH -> Int.MAX_VALUE
    }
    if (range.days < minDays) {
        Text(
            "Not enough days yet for a projection.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        return
    }

    val totalDays = ((range.end - range.start) / 86_400_000.0).roundToInt().coerceAtLeast(1)
    val projected = total / range.days * totalDays
    val unit = when (period) {
        InsightPeriod.WEEK -> "week"
        InsightPeriod.YEAR -> "year"
        else -> "month"
    }
    val periodKey = when (period) {
        InsightPeriod.WEEK -> "WEEK"
        InsightPeriod.YEAR -> "YEAR"
        else -> "MONTH"
    }
    val limit = budgets
        .filter { it.period == periodKey && it.categoryId == null }
        .minOfOrNull { it.limitAmount }

    var appeared by remember { mutableStateOf(!AnimationStore.enabled) }
    LaunchedEffect(Unit) { appeared = true }

    Text(
        "At this pace, you'll spend",
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant
    )
    Text(
        money(animatedAmount(projected, appeared, 0)),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        "by the end of the $unit.",
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant
    )

    if (limit != null && limit > 0) {
        val ratio = (projected / limit).toFloat()
        val over = ratio > 1f
        val color = if (over) scheme.error else scheme.primary
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = color
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Projected " + (ratio * 100).roundToInt() + "% of your " + unit + " budget of " + money(limit) + ".",
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

// Tiles
private class StatsTileData(val label: String, val value: String, val sub: String)

@Composable
private fun StatsTile(tile: StatsTileData, modifier: Modifier) {
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