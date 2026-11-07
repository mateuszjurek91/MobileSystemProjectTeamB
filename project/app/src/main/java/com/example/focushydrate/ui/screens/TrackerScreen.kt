package com.example.focushydrate.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.BarChart
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF145368)

@Composable
fun TrackerScreen(intake: Int, goal: Int, onAddWater: () -> Unit,
    onRemoveWater: () -> Unit, onResetData: () -> Unit, onStatistics: () -> Unit, busy: Boolean = false) {
    val fill = if (goal > 0) (intake.toFloat() / goal).coerceIn(0f, 1f) else 0f
    var confirmReset by remember { mutableStateOf(false) }
    val addLabel = stringResource(R.string.add_water)
    val removeLabel = stringResource(R.string.remove_water)

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 64.dp)) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(7.dp)) { drawCircle(Color(0xFF569AAE)) }
                Spacer(Modifier.width(7.dp))
                Text(stringResource(R.string.today), color = Color(0xFF34798E), fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Text("HydroTracker", Modifier.align(Alignment.Center), color = Ink,
                fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            IconButton(enabled = !busy, onClick = { confirmReset = true }, modifier = Modifier.align(Alignment.CenterEnd).size(48.dp)) {
                Icon(Icons.Outlined.Refresh, stringResource(R.string.reset_today), tint = Ink, modifier = Modifier.size(20.dp))
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val bottleHeight = (maxHeight * 0.50f).coerceIn(150.dp, 270.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                WaterBottle(fill, Modifier.height(bottleHeight).aspectRatio(0.56f))
                Spacer(Modifier.height(26.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$intake", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(" / $goal ml", Modifier.padding(bottom = 4.dp), color = Color(0xFF276F88),
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(stringResource(R.string.drunk_today), color = Ink, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onRemoveWater, enabled = intake > 0 && !busy, shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCEEFFF), contentColor = Ink),
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = removeLabel }) {
                        Text("− 250 ml", fontWeight = FontWeight.SemiBold)
                    }
                    Button(onClick = onAddWater, enabled = !busy, shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF286E81), contentColor = Color.White),
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = addLabel }) {
                        Text("+ 250 ml", fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = onStatistics) {
                    Icon(Icons.Outlined.BarChart, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.statistics))
                }
            }
        }
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
    val description = stringResource(R.string.bottle_progress, (fill * 100).toInt())
    Canvas(modifier.semantics {
        contentDescription = description
        progressBarRangeInfo = ProgressBarRangeInfo(fill, 0f..1f)
    }) {
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
                val waterTop = 251f - 215f * waterFill
                if (waterFill > 0f) {
                    drawRect(Brush.verticalGradient(listOf(Color(0xFF32BDED), Color(0xFF0878A6)),
                        startY = waterTop, endY = 253f), Offset(12f, waterTop), Size(116f, 255f - waterTop))
                    drawRect(Color(0xFF8CE3FF).copy(alpha = 0.65f), Offset(12f, waterTop), Size(116f, 6f))
                    for ((point, radius) in listOf(Offset(70f, 158f) to 3.4f, Offset(46f, 177f) to 2.1f)) {
                        if (point.y > waterTop + 8f) drawCircle(Color(0xFFB2ECFF).copy(alpha = 0.65f), radius, point)
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
