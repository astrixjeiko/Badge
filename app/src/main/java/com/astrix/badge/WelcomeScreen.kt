package com.astrix.badge

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.astrix.badge.ui.theme.DarkColors
import com.astrix.badge.ui.theme.Typography
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(
    animations: Boolean,
    busy: Boolean,
    status: String? = null,
    signInButton: @Composable () -> Unit,
    onGuest: () -> Unit
) {
    BackHandler(enabled = true) { } // block back while the welcome screen shows

    MaterialTheme(colorScheme = DarkColors, typography = Typography) {
        val lift = remember { Animatable(if (animations) 0f else 1f) }
        val show = remember { Animatable(if (animations) 0f else 1f) }
        var contentPx by remember { mutableIntStateOf(0) }
        val gap = 24.dp
        val gapPx = with(LocalDensity.current) { gap.toPx() }

        LaunchedEffect(Unit) {
            if (animations) {
                delay(350.milliseconds)
                launch {
                    lift.animateTo(
                        1f,
                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                    )
                }
                delay(250.milliseconds)
                show.animateTo(1f, tween(500))
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo starts at the exact screen center, then moves up to its place
                Image(
                    painter = painterResource(R.drawable.ic_splash_logo),
                    contentDescription = "Badge",
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer {
                            translationY = (1f - lift.value) * (contentPx + gapPx) / 2f
                        }
                )
                Spacer(Modifier.height(gap))

                // Title, line and buttons sit right below the logo
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { contentPx = it.height }
                        .graphicsLayer {
                            alpha = show.value
                            translationY = (1f - show.value) * 24.dp.toPx()
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Welcome to Badge",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Log expenses in a tap, right from your home screen.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(32.dp))
                    signInButton()
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onGuest,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text(if (busy) "Skip" else "Continue as guest") }
                    Box(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.Center) {
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Text(
                        if (busy) (status ?: "Signing in…")
                        else "You can link a Google account later in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}