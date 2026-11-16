package com.astrix.badge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll
import androidx.core.content.edit

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

    // Nudged when the app returns to the screen, so "today" rolls over at midnight
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

    private val _filter = MutableStateFlow(HistoryFilter())
    val filter: StateFlow<HistoryFilter> = _filter

    fun updateFilter(change: (HistoryFilter) -> HistoryFilter) {
        _filter.update(change)
    }

    // The filter plus its resolved date range; recomputed when the app resumes so "Today" rolls over
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

    // Used by each budget card to get how much has been spent
    fun spentFlow(period: String, categoryId: Long?): Flow<Double> =
        tick.map { Periods.startOf(period) }
            .distinctUntilChanged()
            .flatMapLatest { start -> dao.observeSpent(start, categoryId) }

    val categories = dao.observeCategories().asState(emptyList())
    val quickButtons = dao.observeQuickButtons().asState(emptyList())
    val budgets = dao.observeBudgets().asState(emptyList())

    init {
        // First launch only: add starter categories and one quick button
        viewModelScope.launch {
            val prefs = getApplication<Application>()
                .getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("seeded", false)) {
                // Anyone who already has categories (an update, not a new install) is skipped
                if (dao.countCategories() == 0) {
                    val ids = mutableMapOf<String, Long>()
                    listOf(
                        "🍔" to "Food",
                        "🚗" to "Transport",
                        "🛍️" to "Shopping",
                        "🧾" to "Bills",
                        "📦" to "Misc"
                    ).forEach { (icon, name) ->
                        ids[name] = dao.insertCategory(Category(name = name, icon = icon))
                    }
                    dao.insertQuickButton(
                        QuickButton(label = "Commute", amount = 20.0, categoryId = ids["Transport"])
                    )
                }
                prefs.edit { putBoolean("seeded", true) }
            }
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
        dao.insertCategory(Category(name = name, icon = icon))
    }
    fun deleteCategory(c: Category) = viewModelScope.launch { dao.deleteCategory(c) }

    fun addQuickButton(label: String, amount: Double, categoryId: Long?) = viewModelScope.launch {
        if (quickButtons.value.size >= MAX_QUICK_BUTTONS) return@launch
        dao.insertQuickButton(QuickButton(label = label, amount = amount, categoryId = categoryId))
        refreshWidget()
    }
    fun deleteQuickButton(b: QuickButton) = viewModelScope.launch { dao.deleteQuickButton(b)
        refreshWidget()
    }

    fun addBudget(name: String, period: String, categoryId: Long?, limit: Double) = viewModelScope.launch {
        dao.insertBudget(Budget(name = name, period = period, limitAmount = limit, categoryId = categoryId))
    }

    fun updateBudget(b: Budget) = viewModelScope.launch { dao.updateBudget(b) }
    fun deleteBudget(b: Budget) = viewModelScope.launch { dao.deleteBudget(b) }

    fun updateExpense(e: Expense) = viewModelScope.launch {
        dao.updateExpense(e)
        refreshWidget()
    }

    fun updateCategory(c: Category) = viewModelScope.launch { dao.updateCategory(c) }

    fun updateQuickButton(b: QuickButton) = viewModelScope.launch {
        dao.updateQuickButton(b)
        refreshWidget()
    }
}