package com.astrix.badge

import android.app.Application
import androidx.core.content.edit
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import androidx.room.withTransaction
import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)

class BadgeViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = BadgeDatabase.get(app).dao()

    private fun <T> Flow<T>.asState(initial: T) =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    private fun refreshWidget() = viewModelScope.launch {
        BadgeWidget().updateAll(getApplication())
    }

    val recent = dao.observeRecent(RECENT_LIMIT).asState(emptyList())

    private fun dayOf(item: HistoryItem?): Long? =
        (item as? HistoryItem.Entry)?.let { startOfDay(it.expense.timestamp) }

    private val tick = MutableStateFlow(0)
    fun refreshPeriods() { tick.value += 1 }

    private val _restored = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val restored: SharedFlow<Long> = _restored

    private fun totalFor(period: String) =
        tick.map { Periods.startOf(period) }
            .distinctUntilChanged()
            .flatMapLatest { start -> dao.observeTotalSince(start) }
            .asState(0.0)

    val todayTotal = totalFor("DAY")
    val weekTotal = totalFor("WEEK")
    val monthTotal = totalFor("MONTH")

    val categories = dao.observeCategories().asState(emptyList())
    val quickButtons = dao.observeQuickButtons().asState(emptyList())
    val budgets = dao.observeBudgets().asState(emptyList())

    val dailyBudget: StateFlow<Double?> = budgets
        .map { list ->
            list.filter { it.period == "DAY" && it.categoryId == null }.minOfOrNull { it.limitAmount }
        }
        .asState(null)

    val averageDaily: StateFlow<Double> = tick
        .flatMapLatest {
            val todayStart = Periods.startOf("DAY")
            dao.observeWindow(startOfDaysAgo(30), todayStart).map { w ->
                val first = w.firstTimestamp
                if (first == null || w.total <= 0.0) {
                    0.0
                } else {
                    val days = ((todayStart - startOfDay(first)) / 86_400_000.0).roundToInt()
                        .coerceIn(1, 30)
                    w.total / days
                }
            }
        }
        .asState(0.0)

    val dailyReference: StateFlow<DailyReference> = combine(dailyBudget, averageDaily) { budget, average ->
        when {
            budget != null && budget > 0.0 -> DailyReference(budget, true)
            average > 0.0 -> DailyReference(average, false)
            else -> DailyReference(0.0, false)
        }
    }.asState(DailyReference(0.0, false))

    private val _filter = MutableStateFlow(HistoryFilter())
    val filter: StateFlow<HistoryFilter> = _filter

    fun updateFilter(change: (HistoryFilter) -> HistoryFilter) {
        _filter.update(change)
    }

    private data class Resolved(val f: HistoryFilter, val startMs: Long, val endMs: Long)

    private val resolved = combine(_filter, tick) { f, _ ->
        val (start, end) = f.bounds()
        Resolved(f, start, end)
    }.distinctUntilChanged()

    val history: Flow<PagingData<HistoryItem>> = resolved.flatMapLatest { r ->
        Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
            dao.pagedFiltered(r.f.query.trim(), r.f.categoryId, r.startMs, r.endMs, r.f.sort.code)
        }.flow.map { paging ->
            val items = paging.map<Expense, HistoryItem> { HistoryItem.Entry(it) }
            if (r.f.sort == SortOrder.NEWEST || r.f.sort == SortOrder.OLDEST) {
                items.insertSeparators { before, after ->
                    val afterDay = dayOf(after)
                    if (afterDay != null && afterDay != dayOf(before)) HistoryItem.Header(afterDay) else null
                }
            } else {
                items
            }
        }
    }.cachedIn(viewModelScope)

    val historyTotal: StateFlow<Double> = resolved.flatMapLatest { r ->
        dao.observeFilteredTotal(r.f.query.trim(), r.f.categoryId, r.startMs, r.endMs)
    }.asState(0.0)

    val historyCount: StateFlow<Int> = resolved.flatMapLatest { r ->
        dao.observeFilteredCount(r.f.query.trim(), r.f.categoryId, r.startMs, r.endMs)
    }.asState(0)

    val dayTotals: StateFlow<Map<String, Double>> = resolved.flatMapLatest { r ->
        dao.observeDayTotals(r.f.query.trim(), r.f.categoryId, r.startMs, r.endMs)
            .map { list -> list.associate { it.day to it.total } }
    }.asState(emptyMap())

    fun spentFlow(period: String, categoryId: Long?): Flow<Double> =
        tick.map { Periods.startOf(period) }
            .distinctUntilChanged()
            .flatMapLatest { start -> dao.observeSpent(start, categoryId) }

    private val prefs = app.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)

    // True while the tutorial is on screen
    private val _showTour = MutableStateFlow(false)
    val showTour: StateFlow<Boolean> = _showTour

    fun startTour() { _showTour.value = true }

    fun finishTour() {
        prefs.edit { putBoolean("tour_done", true) }
        _showTour.value = false
    }

    init {
        viewModelScope.launch {
            // First launch only: add starter categories and one quick button
            if (!prefs.getBoolean("seeded", false)) {
                if (dao.countCategories() == 0) {
                    val ids = mutableMapOf<String, Long>()
                    listOf(
                        "🍔" to "Food",
                        "🚗" to "Transport",
                        "🛍️" to "Shopping",
                        "🧾" to "Bills",
                        "📦" to "Misc"
                    ).forEachIndexed { index, (icon, name) ->
                        ids[name] = dao.insertCategory(Category(name = name, icon = icon, position = index))
                    }
                    dao.insertQuickButton(
                        QuickButton(label = "Commute", amount = 20.0, categoryId = ids["Transport"])
                    )
                } else {
                    // An existing install updating to this version: no tutorial needed
                    prefs.edit { putBoolean("tour_done", true) }
                }
                prefs.edit { putBoolean("seeded", true) }
            }
            _showTour.value = !prefs.getBoolean("tour_done", false)
        }
    }

    fun addExpense(amount: Double, categoryId: Long?, note: String = "") = viewModelScope.launch {
        dao.insertExpense(Expense(amount = amount, categoryId = categoryId, note = note))
        refreshWidget()
    }
    fun deleteExpense(e: Expense) = viewModelScope.launch { dao.deleteExpense(e)
        refreshWidget()
    }
    fun restoreExpense(e: Expense) = viewModelScope.launch {
        dao.insertExpense(e)
        refreshWidget()
        _restored.emit(e.id)
    }

    fun addCategory(name: String, icon: String) = viewModelScope.launch {
        val next = (categories.value.maxOfOrNull { it.position } ?: -1) + 1
        dao.insertCategory(Category(name = name, icon = icon, position = next))
    }
    fun deleteCategory(c: Category) = viewModelScope.launch { dao.deleteCategory(c) }

    fun addQuickButton(label: String, amount: Double, categoryId: Long?) = viewModelScope.launch {
        if (quickButtons.value.size >= MAX_QUICK_BUTTONS) return@launch
        val next = (quickButtons.value.maxOfOrNull { it.position } ?: -1) + 1
        dao.insertQuickButton(
            QuickButton(label = label, amount = amount, categoryId = categoryId, position = next)
        )
        refreshWidget()
    }
    fun deleteQuickButton(b: QuickButton) = viewModelScope.launch { dao.deleteQuickButton(b)
        refreshWidget()
    }

    fun addBudget(name: String, period: String, categoryId: Long?, limit: Double) = viewModelScope.launch {
        dao.insertBudget(Budget(name = name, period = period, limitAmount = limit, categoryId = categoryId))
    }

    fun updateBudget(b: Budget) = viewModelScope.launch { dao.updateBudget(b) }
    fun updateExpense(e: Expense) = viewModelScope.launch {
        dao.updateExpense(e)
        refreshWidget()
    }

    fun updateCategory(c: Category) = viewModelScope.launch { dao.updateCategory(c) }

    fun updateQuickButton(b: QuickButton) = viewModelScope.launch {
        dao.updateQuickButton(b)
        refreshWidget()
    }

    fun deleteExpenses(list: List<Expense>) = viewModelScope.launch {
        dao.deleteExpenses(list)
        refreshWidget()
    }

    fun restoreExpenses(list: List<Expense>) = viewModelScope.launch {
        dao.insertExpenses(list)
        refreshWidget()
        list.maxByOrNull { it.timestamp }?.let { _restored.emit(it.id) }
    }

    fun deleteBudgets(list: List<Budget>) = viewModelScope.launch { dao.deleteBudgets(list) }
    fun restoreBudgets(list: List<Budget>) = viewModelScope.launch { dao.insertBudgets(list) }

    suspend fun filteredExpenses(): List<Expense> {
        val f = _filter.value
        val (start, end) = f.bounds()
        return dao.filteredExpenses(f.query.trim(), f.categoryId, start, end)
    }

    fun reorderCategories(ids: List<Long>) = viewModelScope.launch {
        BadgeDatabase.get(getApplication()).withTransaction {
            ids.forEachIndexed { index, id -> dao.setCategoryPosition(id, index) }
        }
    }

    fun reorderQuickButtons(ids: List<Long>) = viewModelScope.launch {
        BadgeDatabase.get(getApplication()).withTransaction {
            ids.forEachIndexed { index, id -> dao.setQuickButtonPosition(id, index) }
        }
        refreshWidget()
    }
}