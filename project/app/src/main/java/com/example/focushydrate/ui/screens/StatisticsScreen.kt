package com.example.focushydrate.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focushydrate.R
import com.example.focushydrate.data.*
import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlin.math.sin
import androidx.compose.ui.graphics.drawscope.rotate

@Composable
fun StatisticsScreen(history: List<DailyIntake>, today: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val locale = LocalConfiguration.current.locales[0]
    var month by rememberSaveable { mutableStateOf(Days.monthStart(today)) }
    var selected by rememberSaveable { mutableStateOf(today) }
    var range by rememberSaveable { mutableStateOf(HistoryRange.WEEK) }
    val days = remember(month) { Days.monthDays(month) }
    val offset = (Days.calendar(month).get(Calendar.DAY_OF_WEEK) + 5) % 7
    val slots: List<String?> = List(offset) { null } + days
    val lookup = remember(history) { history.associateBy { it.day } }
    val points = remember(history, range, today) { historyPoints(history, range, today) }
    val total = points.sumOf { it.millilitres }
    val number = NumberFormat.getIntegerInstance(locale)
    val motion = rememberWaterMotion(slots.any { day ->
        val progress = lookup[day]?.progress ?: 0f
        day != null && day <= today && progress > 0f && progress < 1f
    })
    val settledTilt = motion.settledTilt()
    val verticalBounce = motion.verticalBounce()
    val selectedEntry = lookup[selected]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.statistics), style = MaterialTheme.typography.titleLarge)
        }
        Text(stringResource(R.string.line_chart), style = MaterialTheme.typography.titleLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryRange.entries.forEach { item ->
                FilterChip(selected = range == item, onClick = { range = item }, label = { Text(stringResource(when(item) {
                    HistoryRange.WEEK -> R.string.week; HistoryRange.MONTH -> R.string.month
                    HistoryRange.YEAR -> R.string.year; HistoryRange.ALL -> R.string.all_time
                })) })
            }
        }
        val totalLabel = stringResource(R.string.total_intake, number.format(total))
        val dayCount = remember(history, range, today) {
            inclusiveDayCount(rangeStart(history, range, today), today)
        }
        val averageLabel = stringResource(R.string.daily_average, (total / dayCount).toInt())
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(totalLabel, fontWeight = FontWeight.SemiBold)
                Text(averageLabel, style = MaterialTheme.typography.bodyMedium)
                val hasEntries = history.any { it.day >= rangeStart(history, range, today) && it.day <= today }
                if (!hasEntries) Text(stringResource(R.string.no_entries_in_period))
                val summary = stringResource(R.string.chart_summary, totalLabel, averageLabel)
                IntakeChart(points, summary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val pattern = if (range == HistoryRange.WEEK || range == HistoryRange.MONTH) "d MMM" else "MMM yyyy"
                    points.firstOrNull()?.let { Text(SimpleDateFormat(pattern, locale).format(Days.calendar(it.day).time), fontSize = 12.sp) }
                    points.lastOrNull()?.let { Text(SimpleDateFormat(pattern, locale).format(Days.calendar(it.day).time), fontSize = 12.sp) }
                }
                Text(stringResource(if (range == HistoryRange.WEEK || range == HistoryRange.MONTH) R.string.chart_daily else R.string.chart_monthly),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = Days.shiftMonth(month, -1) }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.previous_month))
                    }
                    Text(SimpleDateFormat("LLLL yyyy", locale).format(Days.calendar(month).time),
                        Modifier.weight(1f), fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(enabled = month < Days.monthStart(today), onClick = { month = Days.shiftMonth(month, 1) }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.next_month))
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    val weekdayNames = DateFormatSymbols(locale).shortWeekdays
                    listOf(2,3,4,5,6,7,1).forEach { day ->
                        Text(weekdayNames[day], Modifier.weight(1f), fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                slots.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (week + List(7 - week.size) { null }).forEach { day ->
                            if (day == null) Spacer(Modifier.weight(1f).heightIn(min = 48.dp))
                            else {
                                val entry = lookup[day]
                                val fraction = entry?.progress ?: 0f
                                val future = day > today
                                val label = if (future) stringResource(R.string.future_day)
                                    else if (entry == null) "$day: ${stringResource(R.string.no_entry)}"
                                    else stringResource(R.string.day_progress, day, entry.millilitres, entry.goal, (fraction * 100).toInt())
                                val shape = RoundedCornerShape(10.dp)
                                val selection = if (day == selected) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                                } else Modifier
                                Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(shape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .then(selection)
                                    .clickable(enabled = !future) { selected = day }
                                    .semantics { contentDescription = label }) {
                                    Canvas(Modifier.matchParentSize()) {
                                        val waterColor = Color(0xFF68BDD1).copy(alpha = 0.65f)
                                        if (fraction >= 1f) {
                                            drawRect(waterColor)
                                        } else if (fraction > 0f) {
                                            val angle = settledTilt.value
                                            val bounceLimit = minOf(fraction, 1f - fraction) * size.minDimension * 0.25f
                                            val bounce = (verticalBounce.value * size.height / 223f)
                                                .coerceIn(-bounceLimit, bounceLimit)
                                            val level = center.y + liquidOffset(angle, size.width, size.height, fraction) + bounce
                                            val span = maxOf(size.width, size.height)
                                            val sway = motion.sway(angle)
                                            val amplitude = (motion.amplitude(sway) * size.height / 260f)
                                                .coerceAtMost(minOf(fraction, 1f - fraction) * size.minDimension * 0.35f)
                                            val water = Path().apply {
                                                for (step in 0..48) {
                                                    val x = center.x + span * (step / 24f - 1f)
                                                    val y = level + amplitude *
                                                        sin((x - center.x) / size.width * 6.283185f + sway * 0.035f)
                                                    if (step == 0) moveTo(x, y) else lineTo(x, y)
                                                }
                                                lineTo(center.x + span, center.y + span)
                                                lineTo(center.x - span, center.y + span)
                                                close()
                                            }
                                            rotate(angle) { drawPath(water, waterColor) }
                                        }
                                    }
                                    Column(Modifier.fillMaxWidth().align(Alignment.Center).padding(vertical = 4.dp), verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(day.takeLast(2).toInt().toString(), fontSize = 14.sp,
                                            color = if (future) Color(0xFF6E8993) else Color(0xFF145368),
                                            fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal)
                                        if (!future) Text(
                                            if (entry == null) "—" else "${(fraction * 100).toInt()}%",
                                            fontSize = 10.sp,
                                            color = Color(0xFF145368)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (selectedEntry != null) Text(stringResource(R.string.day_progress, selected,
                    selectedEntry.millilitres, selectedEntry.goal, (selectedEntry.progress * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                else Text("$selected · ${stringResource(R.string.no_entry)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun IntakeChart(points: List<ChartPoint>, description: String) {
    val max = (points.maxOfOrNull { it.millilitres } ?: 0L).coerceAtLeast(2000L)
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column {
        Text("$max ml", fontSize = 11.sp)
        Canvas(Modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = description }) {
            val margin = 10.dp.toPx()
            val width = size.width - margin * 2
            val height = size.height - margin * 2
            for (row in 0..4) {
                val y = margin + height * row / 4
                drawLine(gridColor, Offset(margin, y), Offset(margin + width, y), 1.dp.toPx())
            }
            val positions = points.mapIndexed { index, point ->
                Offset(if (points.size == 1) size.width / 2 else margin + width * index / (points.size - 1),
                    margin + height * (1 - point.millilitres.toFloat() / max))
            }
            val path = Path()
            positions.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
            drawPath(path, lineColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            if (positions.size <= 31) positions.forEach { drawCircle(lineColor, 3.dp.toPx(), it) }
        }
        Text("0 ml", fontSize = 11.sp)
    }
}
