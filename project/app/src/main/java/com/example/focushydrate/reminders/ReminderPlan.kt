package com.example.focushydrate.reminders

import java.util.Calendar
import java.util.TimeZone

data class ReminderPlan(
    val enabled: Boolean = false,
    val startMinute: Int = 8 * 60,
    val endMinute: Int = 22 * 60,
    val intervalMinutes: Int = 120,
    val weekdays: Set<Int> = (1..7).toSet(),
    val enabledAt: Long = 0,
    val lastSent: Long = 0
) {
    init {
        require(startMinute in 0..1439 && endMinute in 1..1440 && startMinute < endMinute)
        require(intervalMinutes in 30..240)
        require(weekdays.isNotEmpty() && weekdays.all { it in 1..7 })
    }
}

/** Calendar weekdays use Sunday=1. The end of the daily window is exclusive. */
fun nextReminder(
    plan: ReminderPlan,
    now: Long,
    lastDrink: Long = 0,
    timeZone: TimeZone = TimeZone.getDefault()
): Long? {
    if (!plan.enabled) return null
    val interval = plan.intervalMinutes * 60_000L
    // Ignore future anchors after a manual clock correction.
    val anchor = listOf(plan.enabledAt, lastDrink, plan.lastSent).filter { it in 1..now }.maxOrNull() ?: 0L
    val earliest = maxOf(now, anchor + interval)
    val day = Calendar.getInstance(timeZone).apply { timeInMillis = earliest }
    repeat(8) {
        if (day.get(Calendar.DAY_OF_WEEK) in plan.weekdays) {
            val start = (day.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, plan.startMinute / 60)
                set(Calendar.MINUTE, plan.startMinute % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val end = (day.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (plan.endMinute == 1440) add(Calendar.DAY_OF_MONTH, 1)
                else {
                    set(Calendar.HOUR_OF_DAY, plan.endMinute / 60)
                    set(Calendar.MINUTE, plan.endMinute % 60)
                }
            }.timeInMillis
            val candidate = maxOf(earliest, start)
            if (candidate < end) return candidate
        }
        day.add(Calendar.DAY_OF_MONTH, 1)
    }
    return null
}


fun parseReminderTime(value: String): Int? {
    if (value == "24:00") return 1440
    if (!Regex("[0-9]{2}:[0-9]{2}").matches(value)) return null
    val hour = value.take(2).toInt()
    val minute = value.takeLast(2).toInt()
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}
