package com.astrix.badge

import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import android.view.Surface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

private const val TWO_PI = 6.2831855f
private const val MAX_TILT = 0.5f
private const val FULL_LEVEL = 1.15f
private const val FULL_WATER_FADE = 0.6f

// The Animations on/off setting

object AnimationStore {
    var enabled by mutableStateOf(true)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)
        enabled = if (prefs.contains("animations")) {
            prefs.getBoolean("animations", true)
        } else {
            // Not chosen yet: start off if the phone's own animations are turned off
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) != 0f
        }
    }

    fun set(context: Context, value: Boolean) {
        context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)
            .edit { putBoolean("animations", value) }
        enabled = value
    }
}

// Tilt: the angle of the phone in the screen plane, in radians (0 = upright)

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is LifecycleOwner) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun rememberTilt(enabled: Boolean): State<Float> {
    val context = LocalContext.current
    val view = LocalView.current
    val tilt = remember { mutableFloatStateOf(0f) }
    val owner = remember(context) { context.findLifecycleOwner() }

    DisposableEffect(enabled, owner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (!enabled || owner == null || sensorManager == null || sensor == null) {
            tilt.floatValue = 0f
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val ax = event.values[0]
                    val ay = event.values[1]
                    // Turn the phone's axes into the screen's axes (landscape modes too)
                    val sx: Float
                    val sy: Float
                    when (view.display?.rotation) {
                        Surface.ROTATION_90 -> { sx = -ay; sy = ax }
                        Surface.ROTATION_180 -> { sx = -ax; sy = -ay }
                        Surface.ROTATION_270 -> { sx = ay; sy = -ax }
                        else -> { sx = ax; sy = ay }
                    }
                    // Lying flat on a table: there's no clear "down", so stay level
                    tilt.floatValue = if (hypot(sx, sy) < 2.5f) 0f else atan2(-sx, sy)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            var registered = false
            fun start() {
                if (!registered) {
                    sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                    registered = true
                }
            }
            fun stop() {
                if (registered) {
                    sensorManager.unregisterListener(listener)
                    registered = false
                }
            }

            // Listen only while the app is on screen
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> start()
                    Lifecycle.Event.ON_PAUSE -> stop()
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)

            onDispose {
                owner.lifecycle.removeObserver(observer)
                stop()
            }
        }
    }
    return tilt
}

// The liquid

@Composable
fun LiquidBackground(level: Float, full: Boolean, modifier: Modifier = Modifier) {
    val animated = AnimationStore.enabled
    val tilt = rememberTilt(animated)
    val liquidColor = MaterialTheme.colorScheme.primary

    val targetLevel = if (full) FULL_LEVEL else 0.05f + 0.85f * level.coerceIn(0f, 1f)
    val shownLevel = animateFloatAsState(
        targetValue = targetLevel,
        animationSpec = if (animated) {
            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow)
        } else {
            snap()
        },
        label = "liquid-level"
    )

    val calm = animateFloatAsState(
        targetValue = if (full) 1f else 0f,
        animationSpec = if (animated) tween(900) else snap(),
        label = "liquid-calm"
    )

    val shownTilt = remember { Animatable(0f) }
    LaunchedEffect(animated) {
        if (!animated) {
            shownTilt.snapTo(0f)
        } else {
            snapshotFlow { tilt.value }.collectLatest { raw ->
                shownTilt.animateTo(
                    raw.coerceIn(-MAX_TILT, MAX_TILT),
                    spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow)
                )
            }
        }
    }

    val phaseFront: State<Float>
    val phaseBack: State<Float>
    if (animated) {
        val transition = rememberInfiniteTransition(label = "liquid-waves")
        phaseFront = transition.animateFloat(
            initialValue = 0f,
            targetValue = TWO_PI,
            animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
            label = "wave-front"
        )
        phaseBack = transition.animateFloat(
            initialValue = TWO_PI,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(6300, easing = LinearEasing)),
            label = "wave-back"
        )
    } else {
        phaseFront = remember { mutableFloatStateOf(0f) }
        phaseBack = remember { mutableFloatStateOf(0f) }
    }

    Canvas(modifier) {
        val fill = shownLevel.value.coerceIn(0f, FULL_LEVEL + 0.15f)
        val settle = 1f - calm.value
        val angle = shownTilt.value * settle
        // Waves flatten out when the glass is nearly empty or full; none at all with animations off
        val amplitude = if (animated) {
            5.dp.toPx() * (fill / 0.2f).coerceIn(0.3f, 1f) * settle
        } else {
            0f
        }
        val fade = 1f - FULL_WATER_FADE * calm.value

        drawWave(liquidColor.copy(alpha = 0.22f * fade), fill + 0.02f, angle, phaseBack.value, amplitude * 1.3f, 1.6f)
        drawWave(liquidColor.copy(alpha = 0.38f * fade), fill, angle, phaseFront.value, amplitude, 1.1f)
    }
}

private fun DrawScope.drawWave(
    color: Color,
    fill: Float,
    tilt: Float,
    phase: Float,
    amplitude: Float,
    cycles: Float
) {
    val w = size.width
    val h = size.height
    val baseY = h * (1f - fill)
    val slope = tan(tilt)
    val step = 8.dp.toPx()

    val path = Path()
    path.moveTo(0f, h)
    var x = 0f
    while (x < w) {
        val y = baseY - slope * (x - w / 2f) + sin(x / w * cycles * TWO_PI + phase) * amplitude
        path.lineTo(x, y)
        x += step
    }
    val endY = baseY - slope * (w / 2f) + sin(cycles * TWO_PI + phase) * amplitude
    path.lineTo(w, endY)
    path.lineTo(w, h)
    path.close()
    drawPath(path, color)
}