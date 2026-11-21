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

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    @ColumnInfo(defaultValue = "0") val position: Int = 0
)

@Entity(tableName = "recurring")
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val categoryId: Long?,
    val accountId: Long?,
    val dayOfMonth: Int,
    val nextDue: Long,
    val enabled: Boolean = true,
    val notifiedFor: Long = 0L
)

@Entity(
    tableName = "expenses",
    indices = [Index("timestamp"), Index("categoryId"), Index("accountId")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val categoryId: Long?,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val accountId: Long? = null
)

@Entity(tableName = "quick_buttons")
data class QuickButton(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val amount: Double,
    val categoryId: Long?,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
    val accountId: Long? = null
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val period: String,
    val limitAmount: Double,
    val categoryId: Long?,
    @ColumnInfo(defaultValue = "''") val name: String = ""
)

data class DayTotal(val day: String, val total: Double)
data class CategoryTotal(val categoryId: Long?, val total: Double)
data class AccountTotal(val accountId: Long?, val total: Double)
data class UntaggedCounts(val noCategory: Int, val noAccount: Int)
data class RangeStats(val total: Double, val expenseCount: Int)
data class WeekdayTotal(val weekday: Int, val total: Double)

data class SpendWindow(val total: Double, val firstTimestamp: Long?)

val STARTER_ACCOUNTS = listOf(
    "Cash" to 0xFFFFB300.toInt(),
    "E-Wallet" to 0xFF1E88E5.toInt(),
    "Card" to 0xFF8E24AA.toInt()
)

internal const val FILTER_WHERE =
    "(:categoryId IS NULL OR categoryId = :categoryId) " +
            "AND (:accountId IS NULL OR accountId = :accountId) " +
            "AND timestamp >= :startMs AND timestamp < :endMs " +
            "AND (:search = '' OR note LIKE '%' || :search || '%' " +
            "OR CAST(amount AS TEXT) LIKE '%' || :search || '%' " +
            "OR categoryId IN (SELECT id FROM categories WHERE name LIKE '%' || :search || '%') " +
            "OR accountId IN (SELECT id FROM accounts WHERE name LIKE '%' || :search || '%'))"

@Dao
interface BadgeDao {
    // Expenses
    @Insert suspend fun insertExpense(e: Expense): Long
    @Update suspend fun updateExpense(e: Expense)
    @Delete suspend fun deleteExpense(e: Expense)
    @Insert suspend fun insertExpenses(list: List<Expense>)
    @Delete suspend fun deleteExpenses(list: List<Expense>)
    @Update suspend fun updateExpenses(list: List<Expense>)

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
        search: String, categoryId: Long?, accountId: Long?, startMs: Long, endMs: Long, sort: Int
    ): PagingSource<Int, Expense>

    @Query("SELECT * FROM expenses WHERE $FILTER_WHERE")
    suspend fun filteredExpenses(
        search: String, categoryId: Long?, accountId: Long?, startMs: Long, endMs: Long
    ): List<Expense>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE $FILTER_WHERE")
    fun observeFilteredTotal(
        search: String, categoryId: Long?, accountId: Long?, startMs: Long, endMs: Long
    ): Flow<Double>

    @Query("SELECT COUNT(*) FROM expenses WHERE $FILTER_WHERE")
    fun observeFilteredCount(
        search: String, categoryId: Long?, accountId: Long?, startMs: Long, endMs: Long
    ): Flow<Int>

    @Query(
        "SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day, " +
                "SUM(amount) AS total FROM expenses WHERE " + FILTER_WHERE + " GROUP BY day"
    )
    fun observeDayTotals(
        search: String, categoryId: Long?, accountId: Long?, startMs: Long, endMs: Long
    ): Flow<List<DayTotal>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE timestamp >= :start")
    fun observeTotalSince(start: Long): Flow<Double>

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

    // Accounts
    @Insert suspend fun insertAccount(a: Account): Long
    @Update suspend fun updateAccount(a: Account)
    @Delete suspend fun deleteAccount(a: Account)

    @Query("SELECT * FROM accounts ORDER BY position, id")
    fun observeAccounts(): Flow<List<Account>>

    @Query("UPDATE accounts SET position = :position WHERE id = :id")
    suspend fun setAccountPosition(id: Long, position: Int)

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

    // Recurring expenses
    @Insert suspend fun insertRecurring(r: Recurring): Long
    @Update suspend fun updateRecurring(r: Recurring)
    @Delete suspend fun deleteRecurring(r: Recurring)

    @Query("SELECT * FROM recurring ORDER BY nextDue, id")
    fun observeRecurring(): Flow<List<Recurring>>

    @Query("SELECT * FROM recurring")
    suspend fun getRecurring(): List<Recurring>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun getRecurringById(id: Long): Recurring?

    // Insights
    @Query(
        "SELECT categoryId AS categoryId, SUM(amount) AS total FROM expenses " +
                "WHERE timestamp >= :start AND timestamp < :end GROUP BY categoryId ORDER BY total DESC"
    )
    fun observeCategoryTotals(start: Long, end: Long): Flow<List<CategoryTotal>>

    @Query(
        "SELECT COALESCE(SUM(amount), 0.0) AS total, COUNT(*) AS expenseCount FROM expenses " +
                "WHERE timestamp >= :start AND timestamp < :end"
    )
    fun observeRangeStats(start: Long, end: Long): Flow<RangeStats>

    @Query(
        "SELECT * FROM expenses WHERE timestamp >= :start AND timestamp < :end " +
                "ORDER BY amount DESC LIMIT 1"
    )
    fun observeBiggest(start: Long, end: Long): Flow<Expense?>

    @Query(
        "SELECT CAST(strftime('%w', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS weekday, " +
                "SUM(amount) AS total FROM expenses WHERE timestamp >= :start AND timestamp < :end " +
                "GROUP BY weekday ORDER BY total DESC LIMIT 1"
    )
    fun observeTopWeekday(start: Long, end: Long): Flow<WeekdayTotal?>

    // Statistics
    @Query(
        "SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') AS day, " +
                "SUM(amount) AS total FROM expenses WHERE timestamp >= :start AND timestamp < :end GROUP BY day"
    )
    fun observeMonthTotals(start: Long, end: Long): Flow<List<DayTotal>>

    @Query(
        "SELECT accountId AS accountId, SUM(amount) AS total FROM expenses " +
                "WHERE timestamp >= :start AND timestamp < :end GROUP BY accountId ORDER BY total DESC"
    )
    fun observeAccountTotals(start: Long, end: Long): Flow<List<AccountTotal>>

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN categoryId IS NULL THEN 1 ELSE 0 END), 0) AS noCategory, " +
                "COALESCE(SUM(CASE WHEN accountId IS NULL THEN 1 ELSE 0 END), 0) AS noAccount " +
                "FROM expenses WHERE timestamp >= :start AND timestamp < :end"
    )
    fun observeUntagged(start: Long, end: Long): Flow<UntaggedCounts>

    @Query(
        "SELECT COUNT(DISTINCT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime')) " +
                "FROM expenses WHERE timestamp >= :start AND timestamp < :end"
    )
    fun observeSpendDays(start: Long, end: Long): Flow<Int>

    // Budget alerts
    @Query("SELECT * FROM budgets")
    suspend fun getBudgets(): List<Budget>

    @Query(
        "SELECT COALESCE(SUM(amount), 0.0) FROM expenses " +
                "WHERE timestamp >= :start AND (:categoryId IS NULL OR categoryId = :categoryId)"
    )
    suspend fun getSpent(start: Long, categoryId: Long?): Double

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): Category?

    // Backup and restore
    @Query("SELECT * FROM expenses") suspend fun allExpenses(): List<Expense>
    @Query("SELECT * FROM categories") suspend fun allCategories(): List<Category>
    @Query("SELECT * FROM accounts") suspend fun allAccounts(): List<Account>

    @Insert suspend fun insertCategories(list: List<Category>)
    @Insert suspend fun insertAccounts(list: List<Account>)
    @Insert suspend fun insertQuickButtons(list: List<QuickButton>)
    @Insert suspend fun insertRecurringList(list: List<Recurring>)

    @Query("DELETE FROM expenses") suspend fun deleteAllExpenses()
    @Query("DELETE FROM categories") suspend fun deleteAllCategories()
    @Query("DELETE FROM accounts") suspend fun deleteAllAccounts()
    @Query("DELETE FROM quick_buttons") suspend fun deleteAllQuickButtons()
    @Query("DELETE FROM budgets") suspend fun deleteAllBudgets()
    @Query("DELETE FROM recurring") suspend fun deleteAllRecurring()
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

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE quick_buttons ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE categories SET position = id")
        db.execSQL("UPDATE quick_buttons SET position = id")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS accounts (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "color INTEGER NOT NULL, " +
                    "position INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("ALTER TABLE expenses ADD COLUMN accountId INTEGER")
        db.execSQL("ALTER TABLE quick_buttons ADD COLUMN accountId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_accountId ON expenses (accountId)")
        STARTER_ACCOUNTS.forEachIndexed { index, (name, color) ->
            db.execSQL(
                "INSERT INTO accounts (name, color, position) VALUES ('$name', $color, $index)"
            )
        }
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS recurring (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "amount REAL NOT NULL, " +
                    "categoryId INTEGER, " +
                    "accountId INTEGER, " +
                    "dayOfMonth INTEGER NOT NULL, " +
                    "nextDue INTEGER NOT NULL, " +
                    "enabled INTEGER NOT NULL, " +
                    "notifiedFor INTEGER NOT NULL)"
        )
    }
}

@Database(
    entities = [Expense::class, Category::class, QuickButton::class, Budget::class, Account::class, Recurring::class],
    version = 7,
    exportSchema = false
)
abstract class BadgeDatabase : RoomDatabase() {
    abstract fun dao(): BadgeDao

    companion object {
        @Volatile private var INSTANCE: BadgeDatabase? = null
        fun get(context: Context): BadgeDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, BadgeDatabase::class.java, "badge.db"
            ).addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
            ).build().also { INSTANCE = it }
        }
    }
}