package com.astrix.badge

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val BACKUP_FORMAT = 1

private fun JSONObject.longOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null

private fun JSONObject.putNullable(key: String, value: Long?): JSONObject =
    put(key, value ?: JSONObject.NULL)

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

private fun <T> List<T>.toJsonArray(item: (T) -> JSONObject): JSONArray {
    val array = JSONArray()
    forEach { array.put(item(it)) }
    return array
}

// Everything in the app, as one piece of text
suspend fun exportBackupJson(context: Context): String {
    val dao = BadgeDatabase.get(context).dao()
    val categories = dao.allCategories()
    val accounts = dao.allAccounts()
    val expenses = dao.allExpenses()
    val buttons = dao.getQuickButtons()
    val budgets = dao.getBudgets()
    val recurring = dao.getRecurring()
    val currency = CurrencyStore.symbol

    return withContext(Dispatchers.Default) {
        JSONObject()
            .put("app", "badge")
            .put("format", BACKUP_FORMAT)
            .put("createdAt", System.currentTimeMillis())
            .put("currency", currency)
            .put("categories", categories.toJsonArray {
                JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon).put("position", it.position)
            })
            .put("accounts", accounts.toJsonArray {
                JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("position", it.position)
            })
            .put("expenses", expenses.toJsonArray {
                JSONObject().put("id", it.id).put("amount", it.amount).putNullable("categoryId", it.categoryId)
                    .put("note", it.note).put("timestamp", it.timestamp).putNullable("accountId", it.accountId)
            })
            .put("quickButtons", buttons.toJsonArray {
                JSONObject().put("id", it.id).put("label", it.label).put("amount", it.amount)
                    .putNullable("categoryId", it.categoryId).put("position", it.position)
                    .putNullable("accountId", it.accountId)
            })
            .put("budgets", budgets.toJsonArray {
                JSONObject().put("id", it.id).put("period", it.period).put("limitAmount", it.limitAmount)
                    .putNullable("categoryId", it.categoryId).put("name", it.name)
            })
            .put("recurring", recurring.toJsonArray {
                JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount)
                    .putNullable("categoryId", it.categoryId).putNullable("accountId", it.accountId)
                    .put("dayOfMonth", it.dayOfMonth).put("nextDue", it.nextDue)
                    .put("enabled", it.enabled).put("notifiedFor", it.notifiedFor)
            })
            .toString()
    }
}

private class ParsedBackup(
    val currency: String,
    val categories: List<Category>,
    val accounts: List<Account>,
    val expenses: List<Expense>,
    val buttons: List<QuickButton>,
    val budgets: List<Budget>,
    val recurring: List<Recurring>
)

private fun parseBackup(json: String): ParsedBackup {
    val root = JSONObject(json)
    require(root.optString("app") == "badge") { "That file isn't a Badge backup." }
    require(root.optInt("format", 0) <= BACKUP_FORMAT) {
        "This backup was made by a newer version of Badge. Update the app first."
    }
    return ParsedBackup(
        currency = root.optString("currency", ""),
        categories = root.getJSONArray("categories").objects().map {
            Category(it.getLong("id"), it.getString("name"), it.optString("icon", ""), it.optInt("position", 0))
        },
        accounts = root.getJSONArray("accounts").objects().map {
            Account(it.getLong("id"), it.getString("name"), it.getInt("color"), it.optInt("position", 0))
        },
        expenses = root.getJSONArray("expenses").objects().map {
            Expense(
                id = it.getLong("id"), amount = it.getDouble("amount"),
                categoryId = it.longOrNull("categoryId"), note = it.optString("note", ""),
                timestamp = it.getLong("timestamp"), accountId = it.longOrNull("accountId")
            )
        },
        buttons = root.getJSONArray("quickButtons").objects().map {
            QuickButton(
                id = it.getLong("id"), label = it.getString("label"), amount = it.getDouble("amount"),
                categoryId = it.longOrNull("categoryId"), position = it.optInt("position", 0),
                accountId = it.longOrNull("accountId")
            )
        },
        budgets = root.getJSONArray("budgets").objects().map {
            Budget(
                id = it.getLong("id"), period = it.getString("period"),
                limitAmount = it.getDouble("limitAmount"), categoryId = it.longOrNull("categoryId"),
                name = it.optString("name", "")
            )
        },
        recurring = root.getJSONArray("recurring").objects().map {
            Recurring(
                id = it.getLong("id"), name = it.getString("name"), amount = it.getDouble("amount"),
                categoryId = it.longOrNull("categoryId"), accountId = it.longOrNull("accountId"),
                dayOfMonth = it.getInt("dayOfMonth"), nextDue = it.getLong("nextDue"),
                enabled = it.optBoolean("enabled", true), notifiedFor = it.optLong("notifiedFor", 0L)
            )
        }
    )
}

// Replaces everything on the device with the backup
suspend fun restoreFromJson(context: Context, json: String) {
    val backup = withContext(Dispatchers.Default) { parseBackup(json) }
    val db = BadgeDatabase.get(context)
    val dao = db.dao()
    db.withTransaction {
        dao.deleteAllExpenses()
        dao.deleteAllCategories()
        dao.deleteAllAccounts()
        dao.deleteAllQuickButtons()
        dao.deleteAllBudgets()
        dao.deleteAllRecurring()
        dao.insertCategories(backup.categories)
        dao.insertAccounts(backup.accounts)
        dao.insertExpenses(backup.expenses)
        dao.insertQuickButtons(backup.buttons)
        dao.insertBudgets(backup.budgets)
        dao.insertRecurringList(backup.recurring)
    }
    if (backup.currency.isNotBlank()) CurrencyStore.set(context, backup.currency)
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { refreshAllWidgets(context) }
}