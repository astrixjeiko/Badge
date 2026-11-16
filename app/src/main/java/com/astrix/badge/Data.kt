package com.astrix.badge

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "''") val icon: String = "",
    @ColumnInfo(defaultValue = "0") val position: Int = 0
)

@Entity(
    tableName = "expenses",
    indices = [Index("timestamp"), Index("categoryId")]
)
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
    val categoryId: Long?,
    @ColumnInfo(defaultValue = "0") val position: Int = 0
)

// period is "DAY", "WEEK", "MONTH" or "YEAR"; categoryId null = all spending
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val period: String,
    val limitAmount: Double,
    val categoryId: Long?,
    @ColumnInfo(defaultValue = "''") val name: String = ""
)

data class DayTotal(val day: String, val total: Double)

data class SpendWindow(val total: Double, val firstTimestamp: Long?)

internal const val FILTER_WHERE =
    "(:categoryId IS NULL OR categoryId = :categoryId) " +
            "AND timestamp >= :startMs AND timestamp < :endMs " +
            "AND (:search = '' OR note LIKE '%' || :search || '%' " +
            "OR CAST(amount AS TEXT) LIKE '%' || :search || '%' " +
            "OR categoryId IN (SELECT id FROM categories WHERE name LIKE '%' || :search || '%'))"

@Dao
interface BadgeDao {
    // Expenses
    @Insert suspend fun insertExpense(e: Expense): Long
    @Update suspend fun updateExpense(e: Expense)
    @Delete suspend fun deleteExpense(e: Expense)
    @Insert suspend fun insertExpenses(list: List<Expense>)
    @Delete suspend fun deleteExpenses(list: List<Expense>)

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Expense>>

    @Query(
        "SELECT * FROM expenses WHERE " + FILTER_WHERE + " ORDER BY " +
                "CASE WHEN :sort = 2 THEN amount END DESC, " +
                "CASE WHEN :sort = 3 THEN amount END ASC, " +
                "CASE WHEN :sort = 1 THEN timestamp END ASC, " +
                "timestamp DESC"
    )
    fun pagedFiltered(
        search: String, categoryId: Long?, startMs: Long, endMs: Long, sort: Int
    ): PagingSource<Int, Expense>

    // Every expense matching the filter (used by "Select all" in History)
    @Query("SELECT * FROM expenses WHERE $FILTER_WHERE")
    suspend fun filteredExpenses(
        search: String, categoryId: Long?, startMs: Long, endMs: Long
    ): List<Expense>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE $FILTER_WHERE")
    fun observeFilteredTotal(
        search: String, categoryId: Long?, startMs: Long, endMs: Long
    ): Flow<Double>

    @Query("SELECT COUNT(*) FROM expenses WHERE $FILTER_WHERE")
    fun observeFilteredCount(
        search: String, categoryId: Long?, startMs: Long, endMs: Long
    ): Flow<Int>

    @Query(
        "SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day, " +
                "SUM(amount) AS total FROM expenses WHERE " + FILTER_WHERE + " GROUP BY day"
    )
    fun observeDayTotals(
        search: String, categoryId: Long?, startMs: Long, endMs: Long
    ): Flow<List<DayTotal>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE timestamp >= :start")
    fun observeTotalSince(start: Long): Flow<Double>

    // categoryId = null means "all spending"
    @Query(
        "SELECT COALESCE(SUM(amount), 0.0) FROM expenses " +
                "WHERE timestamp >= :start AND (:categoryId IS NULL OR categoryId = :categoryId)"
    )
    fun observeSpent(start: Long, categoryId: Long?): Flow<Double>

    @Query(
        "SELECT COALESCE(SUM(amount), 0.0) AS total, MIN(timestamp) AS firstTimestamp " +
                "FROM expenses WHERE timestamp >= :start AND timestamp < :end"
    )
    fun observeWindow(start: Long, end: Long): Flow<SpendWindow>

    // Categories
    @Insert suspend fun insertCategory(c: Category): Long
    @Update suspend fun updateCategory(c: Category)
    @Delete suspend fun deleteCategory(c: Category)

    @Query("SELECT * FROM categories ORDER BY position, id")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countCategories(): Int

    @Query("UPDATE categories SET position = :position WHERE id = :id")
    suspend fun setCategoryPosition(id: Long, position: Int)

    // Quick buttons
    @Insert suspend fun insertQuickButton(b: QuickButton)
    @Update suspend fun updateQuickButton(b: QuickButton)
    @Delete suspend fun deleteQuickButton(b: QuickButton)

    @Query("SELECT * FROM quick_buttons ORDER BY position, id")
    fun observeQuickButtons(): Flow<List<QuickButton>>

    @Query("SELECT * FROM quick_buttons ORDER BY position, id")
    suspend fun getQuickButtons(): List<QuickButton>

    @Query("UPDATE quick_buttons SET position = :position WHERE id = :id")
    suspend fun setQuickButtonPosition(id: Long, position: Int)

    // Budgets
    @Insert suspend fun insertBudget(b: Budget)
    @Update suspend fun updateBudget(b: Budget)
    @Insert suspend fun insertBudgets(list: List<Budget>)
    @Delete suspend fun deleteBudgets(list: List<Budget>)

    @Query("SELECT * FROM budgets")
    fun observeBudgets(): Flow<List<Budget>>
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE budgets ADD COLUMN name TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_timestamp ON expenses (timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_categoryId ON expenses (categoryId)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN icon TEXT NOT NULL DEFAULT ''")
    }
}

// New: a position for categories and quick buttons. Starting it at the id keeps the current order.
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE quick_buttons ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE categories SET position = id")
        db.execSQL("UPDATE quick_buttons SET position = id")
    }
}

@Database(
    entities = [Expense::class, Category::class, QuickButton::class, Budget::class],
    version = 5,
    exportSchema = false
)
abstract class BadgeDatabase : RoomDatabase() {
    abstract fun dao(): BadgeDao

    companion object {
        @Volatile private var INSTANCE: BadgeDatabase? = null
        fun get(context: Context): BadgeDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, BadgeDatabase::class.java, "badge.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also { INSTANCE = it }
        }
    }
}