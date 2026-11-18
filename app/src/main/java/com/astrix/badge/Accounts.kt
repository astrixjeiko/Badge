package com.astrix.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

val ACCOUNT_COLORS: List<Int> = listOf(
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1, 0xFF3949AB, 0xFF1E88E5,
    0xFF039BE5, 0xFF00ACC1, 0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFC0CA33,
    0xFFFDD835, 0xFFFFB300, 0xFFFB8C00, 0xFFF4511E, 0xFF6D4C41, 0xFF757575,
    0xFF546E7A, 0xFF212121, 0xFFF48FB1, 0xFFB39DDB, 0xFF80CBC4, 0xFFFFAB91
).map { it.toInt() }

@Composable
fun AccountPill(account: Account, modifier: Modifier = Modifier) {
    val background = Color(account.color)
    val textColor = if (background.luminance() > 0.5f) Color.Black else Color.White
    Text(
        account.name,
        color = textColor,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = 96.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun AccountChips(
    accounts: List<Account>,
    selected: Long?,
    anyLabel: String = "None",
    onSelect: (Long?) -> Unit
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(anyLabel) }
        )
        accounts.forEach { a ->
            FilterChip(
                selected = selected == a.id,
                onClick = { onSelect(a.id) },
                label = { Text(a.name) },
                leadingIcon = {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(a.color)))
                }
            )
        }
    }
}

@Composable
fun ColorPickerButton(color: Int, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(color)))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    initial: Int,
    previewName: String,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    val start = remember(initial) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) }
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2]) }
    var preset by remember { mutableStateOf(initial.takeIf { it in ACCOUNT_COLORS }) }
    val current = Color.hsv(hue, saturation, brightness)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a color") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                AccountPill(
                    Account(
                        name = previewName.ifBlank { "Account" },
                        color = (preset ?: current.toArgb())
                    )
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ACCOUNT_COLORS.forEach { argb ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .then(
                                    if (preset == argb) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(argb, hsv)
                                    hue = hsv[0]
                                    saturation = hsv[1]
                                    brightness = hsv[2]
                                    preset = argb
                                }
                        )
                    }
                }

                ColorSlider("Hue", hue, 0f..360f) { hue = it; preset = null }
                ColorSlider("Saturation", saturation, 0f..1f) { saturation = it; preset = null }
                ColorSlider("Brightness", brightness, 0f..1f) { brightness = it; preset = null }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(preset ?: current.toArgb()) }) { Text("Select") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun EditAccountDialog(
    account: Account,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit
) {
    var name by remember { mutableStateOf(account.name) }
    var color by remember { mutableIntStateOf(account.color) }
    var showColors by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.clearFocusOnTap(),
        title = { Text("Edit account") },
        text = {
            Row(verticalAlignment = Alignment.Bottom) {
                ColorPickerButton(color) { showColors = true }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(account.copy(name = name.trim(), color = color))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showColors) {
        ColorPickerDialog(
            initial = color,
            previewName = name,
            onDismiss = { showColors = false },
            onPick = { color = it; showColors = false }
        )
    }
}

@Composable
fun SetAccountDialog(
    count: Int,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set account") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "For " + plural(count, "expense"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                AccountChoiceRow(account = null) { onPick(null) }
                accounts.forEach { a ->
                    AccountChoiceRow(account = a) { onPick(a.id) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AccountChoiceRow(account: Account?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (account == null) Text("No account") else AccountPill(account)
    }
}