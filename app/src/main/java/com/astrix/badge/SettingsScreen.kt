package com.astrix.badge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import android.util.Log
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.core.net.toUri

private fun groupOf(key: Any): String? {
    val k = key.toString()
    return when {
        k.startsWith("cat-") -> "cat"
        k.startsWith("acc-") -> "acc"
        k.startsWith("btn-") -> "btn"
        else -> null
    }
}

private fun <T> List<T>.moved(fromKey: String, toKey: String, keyOf: (T) -> String): List<T> {
    val from = indexOfFirst { keyOf(it) == fromKey }
    val to = indexOfFirst { keyOf(it) == toKey }
    if (from < 0 || to < 0 || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

@Composable
fun SettingsScreen(
    vm: BadgeViewModel,
    onDeleteCategory: (Category) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    onDeleteButton: (QuickButton) -> Unit
) {
    val tour = LocalTourTargets.current
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val buttons by vm.quickButtons.collectAsState()
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    val accountById = remember(accounts) { accounts.associateBy { it.id } }

    var catOrder by remember(categories) { mutableStateOf(categories) }
    var accOrder by remember(accounts) { mutableStateOf(accounts) }
    var btnOrder by remember(buttons) { mutableStateOf(buttons) }

    var editingCat by remember { mutableStateOf<Category?>(null) }
    var editingAcc by remember { mutableStateOf<Account?>(null) }
    var editingBtn by remember { mutableStateOf<QuickButton?>(null) }

    val listState = rememberLazyListState()
    val reorder = rememberReorderState(
        listState = listState,
        sameGroup = { a, b -> groupOf(a) != null && groupOf(a) == groupOf(b) },
        onMove = { from, to ->
            when (groupOf(from)) {
                "cat" -> {
                    val moved = catOrder.moved(from.toString(), to.toString()) { "cat-" + it.id }
                    val changed = moved !== catOrder
                    catOrder = moved
                    changed
                }
                "acc" -> {
                    val moved = accOrder.moved(from.toString(), to.toString()) { "acc-" + it.id }
                    val changed = moved !== accOrder
                    accOrder = moved
                    changed
                }
                "btn" -> {
                    val moved = btnOrder.moved(from.toString(), to.toString()) { "btn-" + it.id }
                    val changed = moved !== btnOrder
                    btnOrder = moved
                    changed
                }
                else -> false
            }
        },
        onDrop = {
            val catIds = catOrder.map { it.id }
            if (catIds != categories.map { it.id }) vm.reorderCategories(catIds)
            val accIds = accOrder.map { it.id }
            if (accIds != accounts.map { it.id }) vm.reorderAccounts(accIds)
            val btnIds = btnOrder.map { it.id }
            if (btnIds != buttons.map { it.id }) vm.reorderQuickButtons(btnIds)
        }
    )

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Currency
        item(key = "currency-title") {
            Text("Currency", style = MaterialTheme.typography.titleLarge)
        }
        item(key = "currency") { CurrencyPicker() }
        item(key = "gap-anim") { Spacer(Modifier.height(8.dp)) }
        item(key = "animations") { AnimationToggle() }
        item(key = "gap-alerts") { Spacer(Modifier.height(8.dp)) }
        item(key = "alerts") { BudgetAlertsToggle(vm) }
        item(key = "gap-auto") { Spacer(Modifier.height(8.dp)) }
        item(key = "auto-backup") { AutoBackupToggle(vm) }
        item(key = "gap-1") { Spacer(Modifier.height(16.dp)) }

        // Categories
        item(key = "categories-title") {
            Text(
                "Categories",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.tourTarget(tour, TourTarget.SETTINGS_CATEGORIES)
            )
        }
        items(catOrder, key = { "cat-" + it.id }, contentType = { "category" }) { c ->
            ReorderRow(reorder, "cat-" + c.id) { dragHandle ->
                CategoryRow(
                    category = c,
                    dragHandle = dragHandle,
                    onEdit = { editingCat = c },
                    onDelete = { onDeleteCategory(c) }
                )
            }
        }
        item(key = "add-category") {
            AddCategoryRow(onAdd = { name, icon -> vm.addCategory(name, icon) })
        }
        item(key = "gap-2") { Spacer(Modifier.height(16.dp)) }

        // Accounts
        item(key = "accounts-title") {
            Text("Accounts", style = MaterialTheme.typography.titleLarge)
        }
        items(accOrder, key = { "acc-" + it.id }, contentType = { "account" }) { a ->
            ReorderRow(reorder, "acc-" + a.id) { dragHandle ->
                AccountRow(
                    account = a,
                    dragHandle = dragHandle,
                    onEdit = { editingAcc = a },
                    onDelete = { onDeleteAccount(a) }
                )
            }
        }
        item(key = "add-account") {
            AddAccountRow(onAdd = { name, color -> vm.addAccount(name, color) })
        }
        item(key = "gap-3") { Spacer(Modifier.height(16.dp)) }

        // Quick buttons
        item(key = "buttons-title") {
            Text(
                "Quick buttons (" + buttons.size + "/" + MAX_QUICK_BUTTONS + ")",
                style = MaterialTheme.typography.titleLarge
            )
        }
        items(btnOrder, key = { "btn-" + it.id }, contentType = { "button" }) { b ->
            ReorderRow(reorder, "btn-" + b.id) { dragHandle ->
                QuickButtonRow(
                    button = b,
                    categoryText = categoryById[b.categoryId]?.display() ?: "No category",
                    account = accountById[b.accountId],
                    dragHandle = dragHandle,
                    onEdit = { editingBtn = b },
                    onDelete = { onDeleteButton(b) }
                )
            }
        }
        item(key = "add-button") {
            AddQuickButtonForm(
                categories = categories,
                accounts = accounts,
                atLimit = buttons.size >= MAX_QUICK_BUTTONS,
                onAdd = { label, amount, cat, acc -> vm.addQuickButton(label, amount, cat, acc) }
            )
        }
        item(key = "google") {
            Spacer(Modifier.height(8.dp))
            GoogleAccountCard()
            Spacer(Modifier.height(8.dp))
        }
        item(key = "export") { ExportButton() }
        item(key = "tutorial") {
            OutlinedButton(
                onClick = { vm.startTour() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Replay tutorial") }
        }
    }

    editingCat?.let { c ->
        EditCategoryDialog(
            category = c,
            onDismiss = { editingCat = null },
            onSave = { vm.updateCategory(it); editingCat = null }
        )
    }
    editingAcc?.let { a ->
        EditAccountDialog(
            account = a,
            onDismiss = { editingAcc = null },
            onSave = { vm.updateAccount(it); editingAcc = null }
        )
    }
    editingBtn?.let { b ->
        EditQuickButtonDialog(
            button = b,
            categories = categories,
            accounts = accounts,
            onDismiss = { editingBtn = null },
            onSave = { vm.updateQuickButton(it); editingBtn = null }
        )
    }
}

@Composable
private fun CurrencyPicker() {
    val context = LocalContext.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CurrencyStore.options.forEach { s ->
            FilterChip(
                selected = CurrencyStore.symbol == s,
                onClick = { CurrencyStore.set(context, s) },
                label = { Text(s) }
            )
        }
    }
}

@Composable
private fun AnimationToggle() {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text("Animations", style = MaterialTheme.typography.titleMedium)
            Text(
                "Liquid card and animated insights on Home.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = AnimationStore.enabled,
            onCheckedChange = { AnimationStore.set(context, it) }
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    dragHandle: @Composable () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dragHandle()
        Text(category.display(), modifier = Modifier.weight(1f))
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

@Composable
private fun AccountRow(
    account: Account,
    dragHandle: @Composable () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dragHandle()
        Box(Modifier.weight(1f)) { AccountPill(account) }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

@Composable
private fun AddCategoryRow(onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.Bottom) {
        IconPickerButton(icon) { showPicker = true }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("New category") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onAdd(name.trim(), icon)
                    name = ""
                    icon = ""
                }
            }) { Text("Add") }
        }
    }

    if (showPicker) {
        EmojiPickerDialog(
            onDismiss = { showPicker = false },
            onPick = { icon = it; showPicker = false }
        )
    }
}

@Composable
private fun AddAccountRow(onAdd: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(5) }
    var color by remember { mutableIntStateOf(ACCOUNT_COLORS[5]) }
    var showPicker by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.Bottom) {
        ColorPickerButton(color) { showPicker = true }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("New account") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onAdd(name.trim(), color)
                    name = ""
                    colorIndex = (colorIndex + 1) % ACCOUNT_COLORS.size
                    color = ACCOUNT_COLORS[colorIndex]
                }
            }) { Text("Add") }
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            initial = color,
            previewName = name,
            onDismiss = { showPicker = false },
            onPick = { color = it; showPicker = false }
        )
    }
}

@Composable
private fun QuickButtonRow(
    button: QuickButton,
    categoryText: String,
    account: Account?,
    dragHandle: @Composable () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dragHandle()
        Column(Modifier.weight(1f)) {
            Text(button.label + ": " + money(button.amount))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    categoryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (account != null) {
                    Spacer(Modifier.width(6.dp))
                    AccountPill(account)
                }
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

@Composable
private fun AddQuickButtonForm(
    categories: List<Category>,
    accounts: List<Account>,
    atLimit: Boolean,
    onAdd: (String, Double, Long?, Long?) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<Long?>(null) }
    var acc by remember { mutableStateOf<Long?>(null) }

    if (atLimit) {
        Text(
            "You've reached the limit of " + MAX_QUICK_BUTTONS +
                    " quick buttons. Delete one to add another.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Button name (e.g. Jeepney)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            AmountField(amount, { amount = it }, Modifier.fillMaxWidth())
            Column {
                CategoryChips(categories, cat) { cat = it }
                AccountChips(accounts, acc) { acc = it }
            }
            Button(onClick = {
                val a = amount.toDoubleOrNull()
                if (label.isNotBlank() && a != null && a > 0) {
                    onAdd(label.trim(), a, cat, acc)
                    label = ""
                    amount = ""
                }
            }) { Text("Add quick button") }
        }
    }
}

@Composable
private fun BudgetAlertsToggle(vm: BadgeViewModel) {
    val context = LocalContext.current
    val resumes by vm.resumes.collectAsState()
    val prefs = remember { context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("budget_alerts", true)) }

    val notificationsOn = remember(resumes) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val turnOn = rememberNotificationEnabler { vm.refreshPeriods() }

    Column {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Budget alerts", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Get a notification when an expense you add from the widget puts you over a budget",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    prefs.edit { putBoolean("budget_alerts", checked) }
                    if (checked && !notificationsOn) turnOn()
                }
            )
        }
        if (enabled && !notificationsOn) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Notifications are off for Badge, so alerts can't appear.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = turnOn) { Text("Turn on notifications") }
        }
    }
}

@Composable
private fun GoogleAccountCard() {
    val account = GoogleAccountStore.account
    var restoreEmail by remember { mutableStateOf<String?>(null) }

    Column {
        if (account == null) {
            LinkGoogleButton(onLinked = { restoreEmail = it })
        } else {
            key(restoreEmail) { LinkedAccountCard(account) }
        }
        restoreEmail?.let { email ->
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            AutoRestore(email) { restoreEmail = null }
        }
    }
}

@Composable
private fun LinkGoogleButton(onLinked: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    GoogleSignInButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        onClick = {
            busy = true
            scope.launch {
                val result = linkGoogleAccount(context)
                result.onSuccess {
                    GoogleAccountStore.save(context, it)
                    onLinked(it.email)
                }
                result.onFailure { error ->
                    googleErrorMessage(error)?.let {
                        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                    }
                    busy = false
                }
            }
        }
    )
}

private class RestoreOffer(val token: String, val remote: DriveBackup.RemoteBackup)

@Composable
private fun LinkedAccountCard(account: LinkedAccount) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var lastBackup by remember(account.email) { mutableLongStateOf(lastBackupTime(context)) }
    var offer by remember { mutableStateOf<RestoreOffer?>(null) }
    var choices by remember { mutableStateOf<Pair<String, List<DriveBackup.RemoteBackup>>?>(null) }
    var showAccountMenu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val initial = account.name.ifBlank { account.email }.take(1).uppercase()

    fun say(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    val runDrive = rememberDriveRunner(account.email, onBusy = { busy = it }, onError = { say(it) })

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    .clickable(enabled = !busy, role = Role.Button) { showAccountMenu = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccountAvatar(initial, 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        account.name.ifBlank { "Google account" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        account.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Change account",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        runDrive { token ->
                            val json = exportBackupJson(context)
                            DriveBackup.upload(token, json)
                            DriveBackup.prune(token)
                            saveLastBackupTime(context, System.currentTimeMillis())
                            lastBackup = lastBackupTime(context)
                            say("Backed up to your Google Drive")
                        }
                    }
                ) { Text("Back up now") }
                OutlinedButton(
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        runDrive { token ->
                            val backups = DriveBackup.list(token)
                            if (backups.isEmpty()) say("No backup found in your Google Drive yet.")
                            else if (backups.size == 1) offer = RestoreOffer(token, backups[0])
                            else choices = token to backups
                        }
                    }
                ) { Text("Restore") }
            }

            if (busy) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (lastBackup > 0) "Last backup: " + formatBackupTime(lastBackup)
                else "No backup yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Account menu
    if (showAccountMenu) {
        AlertDialog(
            onDismissRequest = { showAccountMenu = false },
            title = { Text("Google account") },
            text = {
                Column {
                    AccountMenuRow(
                        title = account.name.ifBlank { "Google account" },
                        subtitle = account.email,
                        leading = { AccountAvatar(initial, 32) },
                        trailing = {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Current account",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = { showAccountMenu = false }
                    )
                    AccountMenuRow(
                        title = "Choose another Google account",
                        subtitle = null,
                        leading = { MenuIcon(Icons.Default.Add) },
                        onClick = {
                            showAccountMenu = false
                            scope.launch {
                                busy = true
                                val result = linkGoogleAccount(context)
                                result.onSuccess {
                                    if (!it.email.equals(account.email, ignoreCase = true)) {
                                        saveLastBackupTime(context, 0L)
                                    }
                                    GoogleAccountStore.save(context, it)
                                }
                                result.onFailure { error -> googleErrorMessage(error)?.let { say(it) } }
                                busy = false
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    AccountMenuRow(
                        title = "Sign out",
                        subtitle = null,
                        leading = { MenuIcon(Icons.AutoMirrored.Filled.ExitToApp) },
                        onClick = {
                            showAccountMenu = false
                            scope.launch {
                                unlinkGoogleAccount(context)
                                saveLastBackupTime(context, 0L)
                                context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)
                                    .edit { putString("auto_backup", "off") }
                                scheduleAutoBackup(context)
                            }
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAccountMenu = false }) { Text("Cancel") } }
        )
    }

    choices?.let { (token, list) ->
        AlertDialog(
            onDismissRequest = { choices = null },
            title = { Text("Choose a backup") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    list.forEachIndexed { i, b ->
                        AccountMenuRow(
                            title = formatBackupTime(parseDriveTime(b.modifiedTime)),
                            subtitle = (if (i == 0) "Latest · " else "") + formatSize(b.size),
                            leading = { MenuIcon(Icons.Default.History) },
                            onClick = {
                                choices = null
                                offer = RestoreOffer(token, b)
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choices = null }) { Text("Cancel") } }
        )
    }

    offer?.let { o ->
        AlertDialog(
            onDismissRequest = { offer = null },
            title = { Text("Restore backup?") },
            text = {
                Text(
                    "This replaces everything on this device with your backup from " +
                            formatBackupTime(parseDriveTime(o.remote.modifiedTime)) + ". It can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    offer = null
                    scope.launch {
                        busy = true
                        try {
                            val json = DriveBackup.download(o.token, o.remote.id)
                            restoreFromJson(context, json)
                            saveLastBackupTime(context, parseDriveTime(o.remote.modifiedTime))
                            lastBackup = lastBackupTime(context)
                            say("Backup restored")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e("BadgeBackup", "Restore failed", e)
                            say(driveErrorMessage(e))
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { offer = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AccountAvatar(label: String, size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun MenuIcon(icon: ImageVector) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AccountMenuRow(
    title: String,
    subtitle: String?,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun AutoBackupToggle(vm: BadgeViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE) }
    val account = GoogleAccountStore.account
    val linked = account != null
    val resumes by vm.resumes.collectAsState()
    var on by remember(account) { mutableStateOf(prefs.getString("auto_backup", "off") == "daily") }

    fun setOn(value: Boolean) {
        on = value
        prefs.edit { putString("auto_backup", if (value) "daily" else "off") }
        scheduleAutoBackup(context)
    }

    val runDrive = rememberDriveRunner(
        account?.email ?: "",
        onBusy = { },
        onError = {
            setOn(false)
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    )
    val needsPermission = remember(resumes, on) {
        on && prefs.getString("auto_backup_error", null) != null
    }

    Column {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Automatic backup", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (linked) "Back up to your Google Drive once a day."
                    else "Link a Google account below to use this.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = on && linked,
                enabled = linked,
                onCheckedChange = { checked ->
                    setOn(checked)
                    if (checked) {
                        runDrive { token ->
                            runBackup(context, token)
                            Toast.makeText(context, "Automatic backup is on", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
        if (needsPermission) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Badge needs your permission again. Turn this off and on to allow it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun formatSize(bytes: Long): String =
    if (bytes < 1024) "$bytes B" else "${bytes / 1024} KB"

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
private fun ExportButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account = GoogleAccountStore.account
    var showDialog by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf("csv") }
    var busy by remember { mutableStateOf(false) }
    val today = LocalDate.now().toString()

    fun save(uri: Uri?, bytes: suspend () -> ByteArray) {
        if (uri == null) return
        scope.launch {
            busy = true
            try {
                val data = bytes()
                context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                Toast.makeText(context, "Export saved", Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BadgeExport", "Export failed", e)
                Toast.makeText(context, "Couldn't save the export. Please try again.", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        save(uri) { buildCsv(loadExportRows(context), bom = true).toByteArray(Charsets.UTF_8) }
    }
    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XLSX_MIME)
    ) { uri ->
        save(uri) { buildXlsx(loadExportRows(context)) }
    }
    val runDrive = rememberDriveRunner(
        account?.email ?: "",
        onBusy = { busy = it },
        onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
        scope = DRIVE_FILE_SCOPE
    )

    OutlinedButton(
        onClick = { showDialog = true },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Export expenses") }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Export expenses") },
            text = {
                Column {
                    ExportOption("csv", "CSV", "Opens in any spreadsheet app", true, format) { format = it }
                    ExportOption("xlsx", "Excel (.xlsx)", "Formatted dates and amounts", true, format) { format = it }
                    ExportOption(
                        "sheets", "Google Sheets",
                        if (account != null) "Creates a spreadsheet in your Drive"
                        else "Sign in with Google to use this",
                        account != null, format
                    ) { format = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    when (format) {
                        "csv" -> csvLauncher.launch("Badge-expenses-$today.csv")
                        "xlsx" -> xlsxLauncher.launch("Badge-expenses-$today.xlsx")
                        else -> runDrive { token ->
                            val csv = buildCsv(loadExportRows(context), bom = false)
                            val link = DriveBackup.createSheet(token, "Badge expenses $today", csv)
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, link.toUri())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }) { Text("Export") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ExportOption(
    key: String,
    title: String,
    subtitle: String,
    enabled: Boolean,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onSelect(key) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected == key && enabled, onClick = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}