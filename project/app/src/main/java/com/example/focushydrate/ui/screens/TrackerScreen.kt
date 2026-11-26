package com.example.focushydrate.ui.screens

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.ui.res.stringResource
import com.example.focushydrate.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt



@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackerScreen(
    intake: Int,
    goal: Int,
    defaultAmount: Int = 250,
    canUndo: Boolean,
    lastAddedAmount: Int?,
    onAddWater: (Int) -> Unit,
    onDefaultAmountChange: (Int) -> Unit = {},
    onUndoWater: () -> Unit,
    onResetData: () -> Unit,
    onStatistics: () -> Unit,
    busy: Boolean = false
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val fill = if (goal > 0) (intake.toFloat() / goal).coerceIn(0f, 1f) else 0f
    var confirmReset by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    val addLabel = stringResource(R.string.add_water)
    val undoLabel = stringResource(R.string.undo_water)
    val customLabel = stringResource(R.string.custom_water)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.today), style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(enabled = !busy, onClick = { confirmReset = true }) {
                Icon(Icons.Outlined.Refresh, stringResource(R.string.reset_today))
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val bottleHeight = (maxHeight * 0.41f).coerceIn(112.dp, 220.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                WaterBottle(fill, Modifier.height(bottleHeight).aspectRatio(0.56f))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$intake", color = ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(" / $goal ml", Modifier.padding(bottom = 4.dp), color = Color(0xFF276F88),
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(stringResource(R.string.drunk_today), color = ink, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onAddWater(defaultAmount) },
                        enabled = !busy,
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .semantics { contentDescription = addLabel }
                    ) {
                        Icon(
                            Icons.Outlined.LocalDrink,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "+ $defaultAmount ml",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onUndoWater,
                            enabled = canUndo && !busy,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFCEEFFF),
                                contentColor = ink
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = undoLabel }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Undo,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (canUndo && lastAddedAmount != null) {
                                    stringResource(R.string.undo_amount, lastAddedAmount)
                                } else {
                                    stringResource(R.string.undo)
                                },
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = { showCustomDialog = true },
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = customLabel }
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.custom),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onStatistics) {
                    Icon(Icons.Outlined.BarChart, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.statistics))
                }
            }
        }
    }
    if (showCustomDialog) {
        var customText by remember { mutableStateOf("") }
        var saveAsDefault by remember { mutableStateOf(false) }
        val parsedAmount = customText.toIntOrNull()
        val isValid = parsedAmount != null && parsedAmount in 1..5000

        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text(stringResource(R.string.custom_water_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.custom_water_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.custom_water_label)) },
                        suffix = { Text("ml") },
                        singleLine = true,
                        colors = inputColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(100, 200, 250, 300, 330, 500).forEach { preset ->
                            FilterChip(
                                selected = parsedAmount == preset,
                                onClick = { customText = preset.toString() },
                                label = { Text("$preset ml") }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = saveAsDefault,
                                role = Role.Checkbox,
                                onValueChange = { saveAsDefault = it }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = saveAsDefault,
                            onCheckedChange = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.set_as_default_amount),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = isValid && !busy,
                    onClick = {
                        parsedAmount?.let { amount ->
                            if (saveAsDefault) {
                                onDefaultAmountChange(amount)
                            }
                            onAddWater(amount)
                        }
                        showCustomDialog = false
                    }
                ) {
                    Text(stringResource(R.string.add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    if (confirmReset) {
        AlertDialog(onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_body)) },
            confirmButton = { TextButton(onClick = { onResetData(); confirmReset = false }) { Text(stringResource(R.string.reset)) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun WaterBottle(fill: Float, modifier: Modifier = Modifier) {
    val animatedFill = animateFloatAsState(fill, tween(650), label = "Water level")
    val motion = rememberWaterMotion(fill > 0f)
    val bubbleCycle = if (fill > 0f) {
        rememberInfiniteTransition(label = "Bubbles").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(6400, easing = LinearEasing)),
            label = "Bubble rise"
        )
    } else null
    val settledTilt = motion.settledTilt()
    val verticalBounce = motion.verticalBounce()
    val description = stringResource(R.string.bottle_progress, (fill * 100).roundToInt())
    Canvas(modifier.semantics {
        contentDescription = description
        progressBarRangeInfo = ProgressBarRangeInfo(fill, 0f..1f)
    }.focusable()) {
        val waterFill = animatedFill.value
        val factor = minOf(size.width / 140f, size.height / 260f)
        scale(factor, factor, pivot = Offset.Zero) {
            val outline = Color(0xFF235D67)
            val bottle = Path().apply {
                moveTo(48f, 30f); lineTo(92f, 30f)
                quadraticTo(95f, 30f, 95f, 35f); lineTo(95f, 54f)
                cubicTo(95f, 72f, 124f, 82f, 124f, 108f)
                lineTo(124f, 232f); quadraticTo(124f, 253f, 105f, 253f)
                lineTo(35f, 253f); quadraticTo(16f, 253f, 16f, 232f)
                lineTo(16f, 108f); cubicTo(16f, 82f, 45f, 72f, 45f, 54f)
                lineTo(45f, 35f); quadraticTo(45f, 30f, 48f, 30f); close()
            }
            drawOval(Color(0xFF286478).copy(alpha = 0.15f), Offset(19f, 245f), Size(104f, 12f))
            drawRoundRect(Color(0xFF447E89), Offset(52f, 3f), Size(36f, 18f), CornerRadius(3f))
            drawRoundRect(outline, Offset(52f, 3f), Size(36f, 18f), CornerRadius(3f), style = Stroke(2f))
            for (x in listOf(59f, 67f, 75f, 83f))
                drawLine(Color(0xFFA7D8DE), Offset(x, 6f), Offset(x, 17f), 1.5f)
            drawRoundRect(Color(0xFF538D99), Offset(48f, 20f), Size(44f, 10f), CornerRadius(2f))
            drawRoundRect(outline, Offset(48f, 20f), Size(44f, 10f), CornerRadius(2f), style = Stroke(2f))
            drawPath(bottle, Color(0xFFDCF5FA).copy(alpha = 0.38f))
            clipPath(bottle) {
                val angle = settledTilt.value
                val waterCenter = Offset(70f, 141.5f)
                val bounceLimit = minOf(waterFill * 80f, 10f)
                val waterTop = waterCenter.y + liquidOffset(angle, 108f, 223f, waterFill * (215f / 223f)) +
                    verticalBounce.value.coerceIn(-bounceLimit, bounceLimit)
                if (waterFill > 0f) {
                    val shallowLimit = (waterFill * 10f).coerceIn(0f, 1f)
                    // At the daily goal the neck still contains air, with room for a smaller wave.
                    val neckLimit = ((1f - waterFill) * 7f + 0.36f).coerceIn(0f, 1f)
                    val edgeLimit = shallowLimit * neckLimit
                    val sway = motion.sway(settledTilt.value)
                    val amplitude = motion.amplitude(sway) * edgeLimit
                    val phase = sway * 0.035f
                    rotate(angle, pivot = waterCenter) {
                        fun surfaceY(x: Int) =
                            waterTop + amplitude * sin((x - 70) * 0.045f + phase)
                        val water = Path().apply {
                            moveTo(-110f, surfaceY(-110))
                            for (x in -108..250 step 2) lineTo(x.toFloat(), surfaceY(x))
                            lineTo(250f, 330f)
                            lineTo(-110f, 330f)
                            close()
                        }
                        val highlight = Path().apply {
                            moveTo(-110f, surfaceY(-110))
                            for (x in -108..250 step 2) lineTo(x.toFloat(), surfaceY(x))
                            for (x in 250 downTo -110 step 2) lineTo(x.toFloat(), surfaceY(x) + 4f)
                            close()
                        }
                        drawPath(water, Brush.verticalGradient(listOf(Color(0xFF32BDED), Color(0xFF0878A6)),
                            startY = waterTop - amplitude, endY = 253f))
                        drawPath(highlight, Color(0xFF8CE3FF).copy(alpha = 0.65f))
                        clipPath(water) {
                            val cycle = bubbleCycle?.value ?: 0f
                            for (index in 0..4) {
                                val radius = 1.8f + (index % 3) * 0.7f
                                val rise = (cycle * (1 + index % 2) + index * 0.19f) % 1f
                                val depth = 253f - waterTop
                                if (depth > 12f) {
                                    val drift = sin(rise * 6.283185f + index) * 5f
                                    val x = 49f + index * 10f + drift + sway * edgeLimit * 0.5f
                                    val y = 248f - rise * (depth - 10f) + motion.vertical * edgeLimit * 0.65f
                                    val fade = minOf(rise * 8f, (1f - rise) * 8f, 1f)
                                    drawCircle(Color(0xFFB2ECFF).copy(alpha = 0.65f * fade), radius, Offset(x, y))
                                }
                            }
                        }
                    }
                }
                drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent,
                    Color(0xFF004C6D).copy(alpha = 0.23f)), startX = 16f, endX = 124f), Offset(16f, 30f), Size(108f, 225f))
                drawLine(Color.White.copy(alpha = 0.65f), Offset(26f, 115f), Offset(26f, 224f), 3f)
                drawLine(Color.White.copy(alpha = 0.48f), Offset(30f, 88f), Offset(40f, 74f), 3f)
                for (y in listOf(142f, 197f))
                    drawLine(Color(0xFFB6ECF8).copy(alpha = 0.65f), Offset(104f, y), Offset(117f, y), 2f)
            }
            drawPath(bottle, outline, style = Stroke(3f))
        }
    }
}

internal class WaterMotion {
    @Composable
    fun settledTilt() = animateFloatAsState(
        tilt + horizontal * 4f,
        spring(dampingRatio = 0.48f, stiffness = 75f),
        label = "Water sway"
    )

    fun sway(settledTilt: Float) = (tilt - settledTilt).coerceIn(-18f, 18f)

    @Composable
    fun verticalBounce() = animateFloatAsState(
        (-vertical * 2.5f).coerceIn(-10f, 10f),
        spring(dampingRatio = 0.42f, stiffness = 180f),
        label = "Water bounce"
    )

    fun amplitude(sway: Float) =
        3f + abs(sway) * 0.55f + abs(vertical) * 0.65f + abs(depth) * 0.45f

    var tilt by mutableFloatStateOf(0f)
    var horizontal by mutableFloatStateOf(0f)
    var vertical by mutableFloatStateOf(0f)
    var depth by mutableFloatStateOf(0f)

    fun reset() {
        tilt = 0f
        horizontal = 0f
        vertical = 0f
        depth = 0f
    }
}

@Composable
internal fun rememberWaterMotion(enabled: Boolean): WaterMotion {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val motion = remember { WaterMotion() }
    DisposableEffect(context, view, lifecycle, enabled) {
        val manager = context.getSystemService(SensorManager::class.java)
        val gravity = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val linearAcceleration = manager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gravityEstimate = FloatArray(3)
        var initialized = false
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

            private fun screenAxes(x: Float, y: Float): Pair<Float, Float> =
                when (view.display?.rotation) {
                    Surface.ROTATION_90 -> -y to x
                    Surface.ROTATION_180 -> -x to -y
                    Surface.ROTATION_270 -> y to -x
                    else -> x to y
                }

            private fun updateTilt(x: Float, y: Float) {
                val (horizontal, vertical) = screenAxes(x, y)
                if (horizontal * horizontal + vertical * vertical < 2f) return
                val target = Math.toDegrees(atan2(horizontal, vertical).toDouble()).toFloat()
                // Follow the shortest arc across the ±180-degree boundary.
                val delta = ((target - motion.tilt) % 360f + 540f) % 360f - 180f
                motion.tilt += delta * 0.16f
            }

            private fun updateAcceleration(x: Float, y: Float, z: Float) {
                val (horizontal, vertical) = screenAxes(x, y)
                motion.horizontal += (horizontal.coerceIn(-8f, 8f) - motion.horizontal) * 0.24f
                motion.vertical += (vertical.coerceIn(-8f, 8f) - motion.vertical) * 0.24f
                motion.depth += (z.coerceIn(-8f, 8f) - motion.depth) * 0.24f
            }

            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                when (event.sensor.type) {
                    Sensor.TYPE_GRAVITY -> updateTilt(x, y)
                    Sensor.TYPE_LINEAR_ACCELERATION -> updateAcceleration(x, y, z)
                    Sensor.TYPE_ACCELEROMETER -> {
                        // Device Pose drives this raw stream on the emulator. Separate its
                        // steady tilt from the short-lived acceleration caused by movement.
                        for (axis in 0..2) {
                            val value = event.values[axis]
                            gravityEstimate[axis] = if (initialized) {
                                gravityEstimate[axis] + (value - gravityEstimate[axis]) * 0.045f
                            } else value
                        }
                        initialized = true
                        updateTilt(gravityEstimate[0], gravityEstimate[1])
                        updateAcceleration(x - gravityEstimate[0], y - gravityEstimate[1], z - gravityEstimate[2])
                    }
                }
            }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && enabled && manager != null) {
                val sensors = if (accelerometer != null) listOf(accelerometer)
                    else listOfNotNull(gravity, linearAcceleration)
                sensors.forEach { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                manager?.unregisterListener(listener)
                initialized = false
                motion.reset()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            manager?.unregisterListener(listener)
            motion.reset()
        }
    }
    return motion
}

// Quantile of the projected rectangle: the sum of two uniform coordinates
// has triangular tails and a linear center. This keeps tile fill area stable.
internal fun liquidOffset(angle: Float, width: Float, height: Float, fill: Float): Float {
    val radians = Math.toRadians(angle.toDouble())
    val x = width * abs(sin(radians)).toFloat()
    val y = height * abs(cos(radians)).toFloat()
    val major = maxOf(x, y)
    val minor = minOf(x, y)
    val tail = minOf(fill, 1f - fill)
    val edge = if (tail * major < minor / 2f) sqrt(2f * tail * major * minor)
        else tail * major + minor / 2f
    val offset = (major + minor) / 2f - edge
    return if (fill <= 0.5f) offset else -offset
}