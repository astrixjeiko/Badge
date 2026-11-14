package com.astrix.badge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll

class BadgeViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = BadgeDatabase.get(app).dao()

    private fun <T> Flow<T>.asState(initial: T) =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    private fun refreshWidget() = viewModelScope.launch {
        BadgeWidget().updateAll(getApplication<Application>())
    }

    val expenses = dao.observeExpenses().asState(emptyList())
    val categories = dao.observeCategories().asState(emptyList())
    val quickButtons = dao.observeQuickButtons().asState(emptyList())
    val budgets = dao.observeBudgets().asState(emptyList())

    init {
        // First launch: add starter data
        viewModelScope.launch {
            if (dao.countCategories() == 0) {
                listOf("Food", "Transport", "Misc").forEach { dao.insertCategory(Category(name = it)) }
                dao.insertQuickButton(QuickButton(label = "Commute", amount = 20.0, categoryId = null))
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

    fun addCategory(name: String) = viewModelScope.launch { dao.insertCategory(Category(name = name)) }
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