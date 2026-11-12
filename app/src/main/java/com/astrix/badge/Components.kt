package com.astrix.badge

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

// A text box that ONLY accepts numbers (digits and one decimal point, max 2 decimals)
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
            FilterChip(selected == c.id, { onSelect(c.id) }, { Text(c.name) })
        }
    }
}