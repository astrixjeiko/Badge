package com.astrix.badge

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val categoryId: Long?,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// A customizable one-tap button, e.g. "Commute" = 20
@Entity(tableName = "quick_buttons")
data class QuickButton(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val amount: Double,
    val categoryId: Long?
)

// period is "DAY", "WEEK", "MONTH" or "YEAR"; categoryId null = all spending
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val period: String,
    val limitAmount: Double,
    val categoryId: Long?
)

@Dao
interface BadgeDao {
    @Insert suspend fun insertExpense(e: Expense): Long
    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Long)
    @Delete suspend fun deleteExpense(e: Expense)
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun observeExpenses(): Flow<List<Expense>>
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    suspend fun getExpenses(): List<Expense>

    @Insert suspend fun insertCategory(c: Category)
    @Delete suspend fun deleteCategory(c: Category)
    @Query("SELECT * FROM categories")
    fun observeCategories(): Flow<List<Category>>
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countCategories(): Int

    @Insert suspend fun insertQuickButton(b: QuickButton)
    @Delete suspend fun deleteQuickButton(b: QuickButton)
    @Query("SELECT * FROM quick_buttons")
    fun observeQuickButtons(): Flow<List<QuickButton>>
    @Query("SELECT * FROM quick_buttons")
    suspend fun getQuickButtons(): List<QuickButton>

    @Insert suspend fun insertBudget(b: Budget)
    @Delete suspend fun deleteBudget(b: Budget)
    @Query("SELECT * FROM budgets")
    fun observeBudgets(): Flow<List<Budget>>
}

@Database(
    entities = [Expense::class, Category::class, QuickButton::class, Budget::class],
    version = 1,
    exportSchema = false
)
abstract class BadgeDatabase : RoomDatabase() {
    abstract fun dao(): BadgeDao

    companion object {
        @Volatile private var INSTANCE: BadgeDatabase? = null
        fun get(context: Context): BadgeDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, BadgeDatabase::class.java, "badge.db"
            ).build().also { INSTANCE = it }
        }
    }
}