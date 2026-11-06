package com.example.focushydrate.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyIntake(val day: String, val millilitres: Int, val goal: Int = 2000) {
    init { require(millilitres >= 0); require(goal > 0) }
    val progress: Float get() = (millilitres.toFloat() / goal).coerceIn(0f, 1f)
}
enum class HistoryRange { WEEK, MONTH, YEAR, ALL }
data class ChartPoint(val day: String, val millilitres: Long)

object Days {
    fun key(time: Long = System.currentTimeMillis()): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(time))
    fun calendar(day: String): Calendar = Calendar.getInstance().apply {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(day)
        time = requireNotNull(parsed)
        set(Calendar.HOUR_OF_DAY, 12)
    }
    fun shift(day: String, count: Int): String = key(calendar(day).apply { add(Calendar.DAY_OF_MONTH, count) }.timeInMillis)
    fun monthStart(day: String): String = key(calendar(day).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis)
    fun shiftMonth(day: String, count: Int): String = key(calendar(monthStart(day)).apply { add(Calendar.MONTH, count) }.timeInMillis)
    fun monthDays(day: String): List<String> {
        val c = calendar(monthStart(day))
        return (1..c.getActualMaximum(Calendar.DAY_OF_MONTH)).map { number -> key((c.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, number) }.timeInMillis) }
    }
    fun between(start: String, end: String): List<String> {
        if (start > end) return emptyList()
        val result = mutableListOf<String>()
        val c = calendar(start)
        while (key(c.timeInMillis) <= end) {
            result.add(key(c.timeInMillis)); c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return result
    }
}

fun logWater(history: List<DailyIntake>, day: String, delta: Int, goal: Int = 2000): List<DailyIntake> {
    val previous = history.find { it.day == day }
    val amount = ((previous?.millilitres ?: 0).toLong() + delta).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    return (history.filterNot { it.day == day } + DailyIntake(day, amount, previous?.goal ?: goal)).sortedBy { it.day }
}
fun historyPoints(history: List<DailyIntake>, range: HistoryRange, today: String): List<ChartPoint> {
    val start = when (range) {
        HistoryRange.WEEK -> Days.shift(today, -6)
        HistoryRange.MONTH -> Days.shift(today, -29)
        HistoryRange.YEAR -> Days.shift(today, -364)
        HistoryRange.ALL -> history.minOfOrNull { it.day }?.coerceAtMost(today) ?: today
    }
    val amounts = history.filter { it.day <= today }.associate { it.day to it.millilitres }
    val daily = Days.between(start, today).map { ChartPoint(it, (amounts[it] ?: 0).toLong()) }
    return if (range == HistoryRange.YEAR || range == HistoryRange.ALL) daily.groupBy { it.day.take(7) }
        .map { (month, entries) -> ChartPoint("$month-01", entries.sumOf { it.millilitres }) } else daily
}
