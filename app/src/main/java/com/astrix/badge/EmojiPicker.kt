package com.astrix.badge

import android.view.ContextThemeWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.emoji2.emojipicker.EmojiPickerView

@Composable
fun IconPickerButton(icon: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        if (icon.isBlank()) {
            Icon(Icons.Default.Add, contentDescription = "Choose icon")
        } else {
            Text(icon, fontSize = 24.sp)
        }
    }
}

@Composable
fun EmojiPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { searchEmojis(query) }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val themeRes = if (isDark) android.R.style.Theme_Material else android.R.style.Theme_Material_Light

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Choose an icon", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                Box(Modifier.fillMaxWidth().height(320.dp)) {
                    if (query.isBlank()) {
                        AndroidView(
                            factory = { ctx ->
                                EmojiPickerView(ContextThemeWrapper(ctx, themeRes)).apply {
                                    emojiGridColumns = 8
                                    setOnEmojiPickedListener { item -> onPick(item.emoji) }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (results.isEmpty()) {
                        Text(
                            "No match. Clear the search to browse all emojis.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 48.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(results) { emoji ->
                                Box(
                                    Modifier.size(48.dp).clickable { onPick(emoji) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 28.sp)
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Cancel") }
            }
        }
    }
}