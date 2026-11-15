package com.astrix.badge

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.TextAlign
import com.astrix.badge.ui.theme.DarkColors
import com.astrix.badge.ui.theme.LightColors
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

val ButtonIdKey = ActionParameters.Key<Long>("buttonId")

// Saved per widget: which tile is selected, and the message under the total
val SelectedKey = longPreferencesKey("selected_button")
val MessageKey = stringPreferencesKey("message")
val MessageStampKey = longPreferencesKey("message_stamp")

val BadgeWidgetColors = ColorProviders(light = LightColors, dark = DarkColors)

class BadgeWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 250.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        CurrencyStore.load(context)
        val dao = BadgeDatabase.get(context).dao()
        provideContent {
            val prefs = currentState<Preferences>()
            val selectedId = prefs[SelectedKey]
            val message = prefs[MessageKey]
            val allButtons by dao.observeQuickButtons().collectAsState(initial = emptyList())
            val today by dao.observeTotalSince(Periods.startOf("DAY")).collectAsState(initial = 0.0)
            GlanceTheme(colors = BadgeWidgetColors) {
                WidgetContent(allButtons, today, selectedId, message)
            }
        }
    }
}

@Composable
fun WidgetContent(
    buttons: List<QuickButton>,
    today: Double,
    selectedId: Long?,
    message: String?
) {
    val context = LocalContext.current
    val compact = LocalSize.current.height < 160.dp
    val shown = buttons.take(MAX_QUICK_BUTTONS)
    val pad = if (compact) 10.dp else 14.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(24.dp)
            .padding(pad)
    ) {
        // Header: total on the left, Confirm (when something is selected) and "+" on the right
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    "TODAY",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
                Text(
                    money(today),
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = if (compact) 20.sp else 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary
                    )
                )
            }

            if (selectedId != null) {
                Box(
                    modifier = GlanceModifier
                        .height(42.dp)
                        .background(GlanceTheme.colors.primary)
                        .cornerRadius(21.dp)
                        .padding(horizontal = 12.dp)
                        .clickable(actionRunCallback<ConfirmAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Confirm",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onPrimary
                        )
                    )
                }
                Spacer(GlanceModifier.width(8.dp))
            }

            Box(
                modifier = GlanceModifier
                    .size(42.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .cornerRadius(21.dp)
                    .clickable(actionStartActivity(Intent(context, QuickAddActivity::class.java))),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_add),
                    contentDescription = "Add expense",
                    modifier = GlanceModifier.size(24.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
                )
            }
        }

        // Message line, directly under the total
        Spacer(GlanceModifier.height(2.dp))
        Text(
            message ?: if (buttons.isEmpty()) "Use + to add an expense" else "Tap an item, then confirm",
            maxLines = 1,
            style = TextStyle(
                fontSize = 12.sp,
                color = if (message != null) GlanceTheme.colors.primary
                else GlanceTheme.colors.onSurfaceVariant
            )
        )

        Spacer(GlanceModifier.height(if (compact) 6.dp else 10.dp))

        if (shown.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No quick buttons yet.\nTap here to open Badge and add one.",
                    style = TextStyle(
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        }

        shown.chunked(2).forEachIndexed { rowIndex, pair ->
            if (rowIndex > 0) Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                pair.forEachIndexed { i, b ->
                    if (i > 0) Spacer(GlanceModifier.width(6.dp))
                    QuickTile(b, b.id == selectedId, compact, GlanceModifier.defaultWeight())
                }
                if (pair.size == 1) {
                    Spacer(GlanceModifier.width(6.dp))
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
fun QuickTile(b: QuickButton, selected: Boolean, compact: Boolean, modifier: GlanceModifier) {
    val bg = if (selected) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant
    val titleColor = if (selected) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurface
    val amountColor = if (selected) GlanceTheme.colors.onPrimary else GlanceTheme.colors.primary

    Column(
        modifier = modifier
            .background(bg)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 9.dp)
            .clickable(actionRunCallback<SelectAction>(actionParametersOf(ButtonIdKey to b.id)))
    ) {
        Text(
            b.label,
            maxLines = 1,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
        )
        Text(
            money(b.amount),
            maxLines = 1,
            style = TextStyle(fontSize = 12.sp, color = amountColor)
        )
    }
}

// Tapping a tile only selects it (tapping it again deselects it)
class SelectAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ButtonIdKey] ?: return
        CurrencyStore.load(context)
        val b = BadgeDatabase.get(context).dao().getQuickButtons().firstOrNull { it.id == id } ?: return
        val current = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[SelectedKey]

        updateAppWidgetState(context, glanceId) { prefs ->
            if (current == id) {
                prefs.remove(SelectedKey)
                prefs.remove(MessageKey)
            } else {
                prefs[SelectedKey] = id
                prefs[MessageKey] = "Selected: " + b.label + " " + money(b.amount)
            }
            prefs[MessageStampKey] = System.currentTimeMillis()
        }
        BadgeWidget().update(context, glanceId)
    }
}

// Tapping Confirm saves the selected tile as an expense
class ConfirmAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        CurrencyStore.load(context)
        val id = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[SelectedKey] ?: return
        val dao = BadgeDatabase.get(context).dao()
        val b = dao.getQuickButtons().firstOrNull { it.id == id }

        if (b == null) {
            // The button was deleted in the app: just clear the selection
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs.remove(SelectedKey)
                prefs.remove(MessageKey)
            }
            BadgeWidget().update(context, glanceId)
            return
        }

        dao.insertExpense(Expense(amount = b.amount, categoryId = b.categoryId, note = b.label))

        val stamp = System.currentTimeMillis()
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.remove(SelectedKey)
            prefs[MessageKey] = "Added: " + b.label + " " + money(b.amount)
            prefs[MessageStampKey] = stamp
        }
        BadgeWidget().update(context, glanceId)

        // Clear the "Added" message after a few seconds, unless something newer happened
        delay(3.seconds)
        if (getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[MessageStampKey] == stamp) {
            updateAppWidgetState(context, glanceId) { prefs -> prefs.remove(MessageKey) }
            BadgeWidget().update(context, glanceId)
        }
    }
}

class BadgeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BadgeWidget()
}