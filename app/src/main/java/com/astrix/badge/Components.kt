package com.astrix.badge

import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun AmountField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Amount"
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.matches(Regex("[0-9]{0,9}[.]?[0-9]{0,2}"))) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
fun CategoryChips(
    categories: List<Category>,
    selected: Long?,
    anyLabel: String = "None",
    onSelect: (Long?) -> Unit
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected == null, { onSelect(null) }, { Text(anyLabel) })
        categories.forEach { c ->
            FilterChip(selected == c.id, { onSelect(c.id) }, { Text(c.display()) })
        }
    }
}

@Composable
fun ExpenseHeadline(e: Expense) {
    val amountColor = MaterialTheme.colorScheme.onSurface
    val softColor = MaterialTheme.colorScheme.onSurfaceVariant
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = amountColor)) {
            append(money(e.amount))
        }
        if (e.note.isNotBlank()) {
            withStyle(SpanStyle(color = softColor)) { append(" · " + e.note) }
        }
    }
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@SuppressLint("UnnecessaryComposedModifier")
fun Modifier.clearFocusOnTap(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    pointerInput(Unit) {
        detectTapGestures(onTap = { focusManager.clearFocus() })
    }
}