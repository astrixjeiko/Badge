package com.astrix.badge

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Targets: parts of the app the tour can spotlight
enum class TourTarget {
    SUMMARY, QUICK_ADD, CUSTOM_AMOUNT, NAV_HISTORY, SEARCH, NEW_BUDGET, SETTINGS_CATEGORIES
}

// Screens report where their targets are; the tour reads them
class TourTargets {
    val bounds = mutableStateMapOf<TourTarget, Rect>()
    var active by mutableStateOf<TourTarget?>(null)   // which target is being shown right now
}

val LocalTourTargets = compositionLocalOf { TourTargets() }

// Put this on any element the tour should be able to spotlight
fun Modifier.tourTarget(targets: TourTargets, target: TourTarget): Modifier =
    this.onGloballyPositioned { coordinates ->
        val rect = coordinates.boundsInRoot()
        if (targets.bounds[target] != rect) targets.bounds[target] = rect
    }

// ---------------------------------------------------------------------------
// The steps (edit the wording here)
// ---------------------------------------------------------------------------

data class TourStep(val target: TourTarget?, val tab: Int, val title: String, val text: String)

private val TOUR_STEPS = listOf(
    TourStep(
        null, 0, "Welcome to Badge",
        "Badge logs your spending in seconds, even from your home screen. Here's a quick tour."
    ),
    TourStep(
        TourTarget.SUMMARY, 0, "Your spending at a glance",
        "Today's total is the big number. Your week and month totals sit right below it."
    ),
    TourStep(
        TourTarget.QUICK_ADD, 0, "Quick add",
        "Tap a button to log a common expense in one tap, like your commute fare. You can change these buttons in Settings."
    ),
    TourStep(
        TourTarget.CUSTOM_AMOUNT, 0, "Custom amount",
        "For anything else, pick a category, give it a name, and type the amount. The keyboard only accepts numbers."
    ),
    TourStep(
        TourTarget.NAV_HISTORY, 1, "History",
        "This tab shows every expense, grouped by day with a daily total. You can edit or delete any of them."
    ),
    TourStep(
        TourTarget.SEARCH, 1, "Search, filter, and sort",
        "Find an expense by name, category, account, or amount. Use the chips below to filter by date, category, or account, and the sort icon to reorder the list."
    ),
    TourStep(
        TourTarget.NEW_BUDGET, 2, "Budgets",
        "Set a spending limit for a day, week, month, or year, for everything or just one category. The bar turns red when you go over."
    ),
    TourStep(
        TourTarget.SETTINGS_CATEGORIES, 3, "Make it yours",
        "Create categories with emoji icons, and set up the quick buttons that appear on Home and on your widget."
    ),
    TourStep(
        null, 0, "Add the widget",
        "The fastest way to use Badge is from your home screen. Long-press an empty spot, tap Widgets, and find Badge. The large widget has quick buttons: tap an item, then press Confirm. The small one shows today's total with a + button for custom amounts."
    )
)

// The overlay

@Composable
fun TourOverlay(targets: TourTargets, onTab: (Int) -> Unit, onFinish: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = TOUR_STEPS[index]
    val last = index == TOUR_STEPS.lastIndex

    BackHandler(onBack = onFinish)

    // Switch to the step's tab, and tell the screens which part is being shown
    LaunchedEffect(index) {
        onTab(step.tab)
        targets.active = step.target
    }
    DisposableEffect(Unit) { onDispose { targets.active = null } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        val targetRect = step.target?.let { targets.bounds[it] }

        // With no target, the hole shrinks into the middle of the screen
        val rect by animateRectAsState(
            targetValue = targetRect ?: Rect(center = Offset(widthPx / 2f, heightPx / 2f), radius = 0f),
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "spotlight-rect"
        )
        val holeScale by animateFloatAsState(
            targetValue = if (targetRect != null) 1f else 0f,
            animationSpec = tween(350),
            label = "spotlight-scale"
        )
        val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulse-value"
        )
        val ringColor = MaterialTheme.colorScheme.primary

        // Dark overlay with a hole and a pulsing ring around the target
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .pointerInput(Unit) {
                    // Swallow every touch, so the app behind can't be used during the tour
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            drawRect(Color.Black.copy(alpha = 0.82f))

            val pad = 10.dp.toPx() * holeScale
            val corner = 16.dp.toPx()
            val topLeft = Offset(rect.left - pad, rect.top - pad)
            val holeSize = Size(rect.width + pad * 2, rect.height + pad * 2)

            drawRoundRect(
                color = Color.Black,
                topLeft = topLeft,
                size = holeSize,
                cornerRadius = CornerRadius(corner),
                blendMode = BlendMode.Clear
            )
            if (holeScale > 0.5f) {
                // Steady outline
                drawRoundRect(
                    color = ringColor.copy(alpha = 0.9f),
                    topLeft = topLeft,
                    size = holeSize,
                    cornerRadius = CornerRadius(corner),
                    style = Stroke(width = 2.dp.toPx())
                )
                // Pulse that grows outward and fades
                val grow = 14.dp.toPx() * pulse
                drawRoundRect(
                    color = ringColor.copy(alpha = (1f - pulse) * 0.8f),
                    topLeft = Offset(topLeft.x - grow, topLeft.y - grow),
                    size = Size(holeSize.width + grow * 2, holeSize.height + grow * 2),
                    cornerRadius = CornerRadius(corner + grow),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // The card goes below targets in the top half of the screen, above the rest
        val gap = 28.dp   // room for the hole edge and the pulsing ring
        val showBelow = targetRect != null && targetRect.center.y < heightPx / 2f
        val cardModifier = when {
            targetRect == null -> Modifier.align(Alignment.Center)
            showBelow -> Modifier
                .align(Alignment.TopCenter)
                .padding(top = with(density) { rect.bottom.toDp() } + gap)
            else -> Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = with(density) { (heightPx - rect.top).toDp() } + gap)
        }

        TourCard(
            index = index,
            last = last,
            modifier = cardModifier,
            onBack = { if (index > 0) index -= 1 },
            onNext = { if (last) onFinish() else index += 1 },
            onSkip = onFinish
        )
    }
}

@Composable
private fun TourCard(
    index: Int,
    last: Boolean,
    modifier: Modifier,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            // The text fades and slides in when the step changes
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 6 }) togetherWith
                            fadeOut(tween(120))
                },
                label = "tour-text"
            ) { i ->
                Column {
                    Text(
                        TOUR_STEPS[i].title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        TOUR_STEPS[i].text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TOUR_STEPS.indices.forEach { i -> TourDot(active = i == index) }
                Spacer(Modifier.weight(1f))
                Text(
                    (index + 1).toString() + " / " + TOUR_STEPS.size,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!last) TextButton(onClick = onSkip) { Text("Skip") }
                Spacer(Modifier.weight(1f))
                if (index > 0) TextButton(onClick = onBack) { Text("Back") }
                Button(onClick = onNext) { Text(if (last) "Get started" else "Next") }
            }
        }
    }
}

// Progress dot: the current one is a longer pill
@Composable
private fun TourDot(active: Boolean) {
    val width by animateDpAsState(if (active) 18.dp else 6.dp, label = "dot-width")
    val color = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    }
    Box(
        Modifier
            .padding(end = 4.dp)
            .height(6.dp)
            .width(width)
            .clip(CircleShape)
            .background(color)
    )
}