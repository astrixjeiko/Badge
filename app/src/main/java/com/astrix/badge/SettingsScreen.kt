package com.astrix.badge

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import android.util.Log
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import androidx.compose.runtime.collectAsState

private const val MAX_CATEGORIES = 20
private const val MAX_ACCOUNTS = 10

@Composable
fun SettingsScreen(
    vm: BadgeViewModel,
    onDeleteCategory: (Category) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    onDeleteButton: (QuickButton) -> Unit,
    onSignedIn: (String) -> Unit,
    restoring: Boolean,
    restoreStatus: String
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

    var catsOpen by rememberSaveable { mutableStateOf(false) }
    var accsOpen by rememberSaveable { mutableStateOf(false) }
    var btnsOpen by rememberSaveable { mutableStateOf(false) }
    val toggleCats = {
        val open = !catsOpen
        catsOpen = open
        if (open) { accsOpen = false; btnsOpen = false }
    }
    val toggleAccs = {
        val open = !accsOpen
        accsOpen = open
        if (open) { catsOpen = false; btnsOpen = false }
    }
    val toggleBtns = {
        val open = !btnsOpen
        btnsOpen = open
        if (open) { catsOpen = false; accsOpen = false }
    }

    val saveCatOrder = {
        val ids = catOrder.map { it.id }
        if (ids != categories.map { it.id }) vm.reorderCategories(ids)
    }
    val saveAccOrder = {
        val ids = accOrder.map { it.id }
        if (ids != accounts.map { it.id }) vm.reorderAccounts(ids)
    }
    val saveBtnOrder = {
        val ids = btnOrder.map { it.id }
        if (ids != buttons.map { it.id }) vm.reorderQuickButtons(ids)
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (vm.showTour.collectAsState().value) 8 else 0
    )

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Account
        item(key = "label-account") { SectionLabel("Account", first = true) }
        item(key = "google") { GoogleAccountCard(onSignedIn, restoring, restoreStatus) }
        item(key = "auto-backup") { AutoBackupToggle(vm) }

        // Preferences
        item(key = "label-prefs") { SectionLabel("Preferences") }
        item(key = "currency-title") {
            Text("Currency", style = MaterialTheme.typography.titleMedium)
        }
        item(key = "currency") { CurrencyPicker() }
        item(key = "animations") { AnimationToggle() }
        item(key = "alerts") { BudgetAlertsToggle(vm) }

        // Customize
        item(key = "label-customize") { SectionLabel("Customize") }

        item(key = "customize") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .tourTarget(tour, TourTarget.SETTINGS_CATEGORIES),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExpandableCard(
                    title = "Categories",
                    expanded = catsOpen,
                    onToggle = toggleCats
                ) {
                    ReorderColumn(
                        items = catOrder,
                        keyOf = { it.id },
                        onMove = { from, to -> catOrder = catOrder.reordered(from, to) },
                        onDrop = saveCatOrder
                    ) { c, handle ->
                        CategoryRow(
                            category = c,
                            dragHandle = handle,
                            onEdit = { editingCat = c },
                            onDelete = { onDeleteCategory(c) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    AddCategoryRow(
                        atLimit = categories.size >= MAX_CATEGORIES,
                        onAdd = { name, icon -> vm.addCategory(name, icon) }
                    )
                }

                ExpandableCard("Accounts", accsOpen, toggleAccs) {
                    ReorderColumn(
                        items = accOrder,
                        keyOf = { it.id },
                        onMove = { from, to -> accOrder = accOrder.reordered(from, to) },
                        onDrop = saveAccOrder
                    ) { a, handle ->
                        AccountRow(
                            account = a,
                            dragHandle = handle,
                            onEdit = { editingAcc = a },
                            onDelete = { onDeleteAccount(a) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    AddAccountRow(
                        atLimit = accounts.size >= MAX_ACCOUNTS,
                        onAdd = { name, color -> vm.addAccount(name, color) }
                    )
                }

                ExpandableCard("Quick buttons", btnsOpen, toggleBtns) {
                    ReorderColumn(
                        items = btnOrder,
                        keyOf = { it.id },
                        onMove = { from, to -> btnOrder = btnOrder.reordered(from, to) },
                        onDrop = saveBtnOrder
                    ) { b, handle ->
                        QuickButtonRow(
                            button = b,
                            categoryText = categoryById[b.categoryId]?.display() ?: "No category",
                            account = accountById[b.accountId],
                            dragHandle = handle,
                            onEdit = { editingBtn = b },
                            onDelete = { onDeleteButton(b) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    AddQuickButtonForm(
                        categories = categories,
                        accounts = accounts,
                        atLimit = buttons.size >= MAX_QUICK_BUTTONS,
                        onAdd = { label, amount, cat, acc -> vm.addQuickButton(label, amount, cat, acc) }
                    )
                }
            }
        }

        // Data
        item(key = "label-data") { SectionLabel("Data") }
        item(key = "export") { ExportButton() }

        // About
        item(key = "label-about") { SectionLabel("About") }
        item(key = "update-check") { UpdateCheckToggle() }
        item(key = "updates") { UpdateRow() }
        item(key = "tutorial") {
            OutlinedButton(
                onClick = { vm.startTour() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Replay tutorial") }
        }
        item(key = "about") { AboutSection() }
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
private fun AddCategoryRow(atLimit: Boolean, onAdd: (String, String) -> Unit) {
    if (atLimit) {
        Text(
            "You've reached the limit of $MAX_CATEGORIES categories. Delete one to add another.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
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
private fun AddAccountRow(atLimit: Boolean, onAdd: (String, Int) -> Unit) {
    if (atLimit) {
        Text(
            "You've reached the limit of $MAX_ACCOUNTS accounts. Delete one to add another.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
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
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
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
        Column {
            CategoryChips(categories, cat) { cat = it }
            AccountChips(accounts, acc) { acc = it }
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Button name (e.g. Jeepney)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                AmountField(amount, { amount = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                    Button(onClick = {
                        val a = amount.toDoubleOrNull()
                        if (label.isNotBlank() && a != null && a > 0) {
                            onAdd(label.trim(), a, cat, acc)
                            label = ""
                            amount = ""
                        }
                    }) { Text("Add") }
                }
            }
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
private fun GoogleAccountCard(onSignedIn: (String) -> Unit, restoring: Boolean, restoreStatus: String) {
    val account = GoogleAccountStore.account

    Column {
        if (account == null) {
            LinkGoogleButton(onSignedIn)
        } else {
            // Rebuilt when a restore starts or ends, so "Last backup" refreshes
            key(restoring) { LinkedAccountCard(account, restoring) }
        }
        if (restoring) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(
                restoreStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LinkGoogleButton(onSignedIn: (String) -> Unit) {
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
                    // Start the restore first: saving the account swaps this button for the card
                    onSignedIn(it.email)
                    GoogleAccountStore.save(context, it)
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
private fun LinkedAccountCard(account: LinkedAccount, restoring: Boolean) {
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

    LaunchedEffect(account.email) {
        if (lastBackup == 0L && !restoring) {
            try {
                val token = silentDriveToken(context, account.email)
                val remote = token?.let { DriveBackup.find(it) }
                if (remote != null) {
                    val time = parseDriveTime(remote.modifiedTime)
                    saveLastBackupTime(context, time)
                    lastBackup = time
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("BadgeBackup", "Couldn't read the latest backup time", e)
            }
        }
    }

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
                    else "Sign in with Google account to enable.",
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

@Composable
private fun UpdateCheckToggle() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("auto_update_check", true)) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text("Update checks", style = MaterialTheme.typography.titleMedium)
            Text(
                "Look for new versions on GitHub when the app opens.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                prefs.edit { putBoolean("auto_update_check", it) }
            }
        )
    }
}

@Composable
private fun UpdateRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    val info = Updates.available

    Column {
        OutlinedButton(
            onClick = {
                if (info != null) {
                    showDialog = true
                } else {
                    scope.launch {
                        Updates.check(context, manual = true)
                        if (Updates.available != null) showDialog = true
                    }
                }
            },
            enabled = !Updates.checking,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    Updates.checking -> "Checking…"
                    info != null -> "Update available: " + info.version
                    else -> "Check for updates"
                }
            )
        }
        Updates.message?.let {
            if (!showDialog) {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    val current = Updates.available
    if (showDialog && current != null) {
        UpdateDialog(current) { showDialog = false }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://github.com/$GITHUB_OWNER/$GITHUB_REPO".toUri()
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("View on GitHub") }
        Text(
            "Badge v" + Updates.currentVersion(context) + " · ©2026 " + CREDITS_NAME,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SectionLabel(text: String, first: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = if (first) 0.dp else 16.dp)
    )
}

private fun <T> List<T>.reordered(from: Int, to: Int): List<T> =
    toMutableList().apply { add(to, removeAt(from)) }

@Composable
private fun ExpandableCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val animate = AnimationStore.enabled
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (animate) tween(250) else snap(),
        label = "chevron"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(rotation)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (animate) expandVertically(tween(250)) + fadeIn(tween(250)) else EnterTransition.None,
            exit = if (animate) shrinkVertically(tween(200)) + fadeOut(tween(150)) else ExitTransition.None
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) { content() }
        }
    }
}

@Composable
private fun <T> ReorderColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: () -> Unit,
    row: @Composable (item: T, dragHandle: @Composable () -> Unit) -> Unit
) {
    var dragged by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    val latestItems by rememberUpdatedState(items)
    val latestMove by rememberUpdatedState(onMove)
    val latestDrop by rememberUpdatedState(onDrop)
    val gapPx = with(LocalDensity.current) { 8.dp.toPx() }
    val shape = RoundedCornerShape(12.dp)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val k = keyOf(item)
            key(k) {
                val isDragged = dragged == k
                val handle: @Composable () -> Unit = {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Reorder",
                        modifier = Modifier
                            .pointerInput(k) {
                                detectDragGestures(
                                    onDragStart = { dragged = k; offset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val list = latestItems
                                        val index = list.indexOfFirst { keyOf(it) == k }
                                        if (index >= 0) {
                                            fun step(i: Int): Float =
                                                (heights[keyOf(list[i])] ?: 0) + gapPx
                                            val above = (0 until index).fold(0f) { acc, i -> acc + step(i) }
                                            val below = (index + 1 until list.size).fold(0f) { acc, i -> acc + step(i) }
                                            offset = (offset + amount.y).coerceIn(-above, below)

                                            val next = list.getOrNull(index + 1)
                                            val prev = list.getOrNull(index - 1)
                                            val nextStep = if (next != null) step(index + 1) else 0f
                                            val prevStep = if (prev != null) step(index - 1) else 0f
                                            if (next != null && offset > nextStep / 2f) {
                                                offset -= nextStep
                                                latestMove(index, index + 1)
                                            } else if (prev != null && offset < -prevStep / 2f) {
                                                offset += prevStep
                                                latestMove(index, index - 1)
                                            }
                                        }
                                    },
                                    onDragEnd = { dragged = null; offset = 0f; latestDrop() },
                                    onDragCancel = { dragged = null; offset = 0f; latestDrop() }
                                )
                            }
                            .padding(12.dp)
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) offset else 0f }
                        .shadow(if (isDragged) 8.dp else 0.dp, shape)
                        .background(MaterialTheme.colorScheme.surface, shape)
                        .onSizeChanged { heights[k] = it.height }
                        .padding(horizontal = 4.dp)
                ) { row(item, handle) }
            }
        }
    }
}