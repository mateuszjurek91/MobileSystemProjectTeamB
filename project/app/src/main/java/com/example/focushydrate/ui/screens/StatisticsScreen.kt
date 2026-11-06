package com.example.focushydrate.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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

@Composable
fun StatisticsScreen(history: List<DailyIntake>, today: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val locale = LocalConfiguration.current.locales[0]
    var month by rememberSaveable { mutableStateOf(Days.monthStart(today)) }
    var selected by rememberSaveable { mutableStateOf(today) }
    var range by rememberSaveable { mutableStateOf(HistoryRange.WEEK) }
    val days = Days.monthDays(month)
    val offset = (Days.calendar(month).get(Calendar.DAY_OF_WEEK) + 5) % 7
    val slots: List<String?> = List(offset) { null } + days
    val lookup = history.associateBy { it.day }
    val points = remember(history, range, today) { historyPoints(history, range, today) }
    val total = points.sumOf { it.millilitres }
    val number = NumberFormat.getIntegerInstance(locale)
    val selectedEntry = lookup[selected]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.statistics), fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        }
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            if (day == null) Spacer(Modifier.weight(1f).height(60.dp))
                            else {
                                val entry = lookup[day]
                                val fraction = entry?.progress ?: 0f
                                val future = day > today
                                val label = if (future) stringResource(R.string.future_day)
                                    else if (entry == null) "$day: ${stringResource(R.string.no_entry)}"
                                    else stringResource(R.string.day_progress, day, entry.millilitres, entry.goal, (fraction * 100).toInt())
                                Box(Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (day == selected) Color(0xFFB0D9E9) else Color(0xFFE1F2FA))
                                    .clickable(enabled = !future) { selected = day }
                                    .semantics { contentDescription = label }) {
                                    Canvas(Modifier.matchParentSize()) {
                                        drawRect(Color(0xFF68BDD1).copy(alpha = 0.65f),
                                            Offset(0f, size.height * (1 - fraction)), Size(size.width, size.height * fraction))
                                    }
                                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(day.takeLast(2).toInt().toString(), fontSize = 14.sp,
                                            color = if (future) Color(0xFF6E8993) else Color(0xFF145368),
                                            fontWeight = if (day == selected) FontWeight.Bold else FontWeight.Normal)
                                        if (!future) Text("${(fraction * 100).toInt()}%", fontSize = 10.sp, color = Color(0xFF145368))
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
        val periodStart = when(range) {
            HistoryRange.WEEK -> Days.shift(today, -6); HistoryRange.MONTH -> Days.shift(today, -29)
            HistoryRange.YEAR -> Days.shift(today, -364); HistoryRange.ALL -> history.minOfOrNull { it.day } ?: today
        }
        val dayCount = Days.between(periodStart, today).size.coerceAtLeast(1)
        val averageLabel = stringResource(R.string.daily_average, (total / dayCount).toInt())
        Text(totalLabel, fontWeight = FontWeight.SemiBold)
        Text(averageLabel, style = MaterialTheme.typography.bodyMedium)
        if (history.isEmpty()) Text(stringResource(R.string.no_history))
        val summary = stringResource(R.string.chart_summary, totalLabel, averageLabel)
        IntakeChart(points, summary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val pattern = if (range == HistoryRange.WEEK || range == HistoryRange.MONTH) "d MMM" else "MMM yyyy"
            points.firstOrNull()?.let { Text(SimpleDateFormat(pattern, locale).format(Days.calendar(it.day).time), fontSize = 12.sp) }
            points.lastOrNull()?.let { Text(SimpleDateFormat(pattern, locale).format(Days.calendar(it.day).time), fontSize = 12.sp) }
        }
        Text(stringResource(if (range == HistoryRange.WEEK || range == HistoryRange.MONTH) R.string.chart_daily else R.string.chart_monthly),
            style = MaterialTheme.typography.bodySmall)
        // A readable list also exposes chart values to screen readers.
        points.filter { it.millilitres > 0 }.forEach { point ->
            Text("${point.day} · ${number.format(point.millilitres)} ml", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun IntakeChart(points: List<ChartPoint>, description: String) {
    val max = (points.maxOfOrNull { it.millilitres } ?: 0L).coerceAtLeast(2000L)
    Column {
        Text("$max ml", fontSize = 11.sp)
        Canvas(Modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = description }) {
            val margin = 10.dp.toPx()
            val width = size.width - margin * 2
            val height = size.height - margin * 2
            for (row in 0..4) {
                val y = margin + height * row / 4
                drawLine(Color(0xFF7FADBE).copy(alpha = 0.4f), Offset(margin, y), Offset(margin + width, y), 1.dp.toPx())
            }
            val positions = points.mapIndexed { index, point ->
                Offset(if (points.size == 1) size.width / 2 else margin + width * index / (points.size - 1),
                    margin + height * (1 - point.millilitres.toFloat() / max))
            }
            val path = Path()
            positions.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
            drawPath(path, Color(0xFF24758B), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            if (positions.size <= 31) positions.forEach { drawCircle(Color(0xFF24758B), 3.dp.toPx(), it) }
        }
        Text("0 ml", fontSize = 11.sp)
    }
}
