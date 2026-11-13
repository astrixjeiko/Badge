package com.astrix.badge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkColors = darkColorScheme(
    primary = BadgeTeal,
    onPrimary = Color(0xFF06221D),
    primaryContainer = Color(0xFF0F4A41),
    onPrimaryContainer = Color(0xFFBFF2E9),
    secondary = Color(0xFF7FCDC4),
    onSecondary = Color(0xFF0B2B27),
    secondaryContainer = Color(0xFF1F3A37),
    onSecondaryContainer = Color(0xFFD3EEEA),
    tertiary = Color(0xFFA8DDD6),
    background = BadgeBlack,
    onBackground = Color(0xFFE8F3F2),
    surface = BadgeBlack,
    onSurface = Color(0xFFE8F3F2),
    surfaceVariant = Color(0xFF1E2625),
    onSurfaceVariant = Color(0xFFB5C9C6),
    outline = Color(0xFF6F8582)
)

val LightColors = lightColorScheme(
    primary = Color(0xFF0F8F7B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F4EE),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4A6561),
    onSecondary = Color.White,
    secondaryContainer = BadgeMint,
    onSecondaryContainer = Color(0xFF05201C),
    background = Color(0xFFF5FBFA),
    onBackground = Color(0xFF141D1C),
    surface = Color(0xFFF5FBFA),
    onSurface = Color(0xFF141D1C)
)

@Composable
fun BadgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}