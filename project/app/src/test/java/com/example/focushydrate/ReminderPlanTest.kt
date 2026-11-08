package com.example.focushydrate

import com.example.focushydrate.reminders.*
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ReminderPlanTest {
    private val zone = TimeZone.getTimeZone("Europe/Warsaw")
    private fun time(value: String): Long = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { timeZone = zone }.parse(value)!!.time
    private fun plan(enabledAt: String = "2026-11-06 08:00") = ReminderPlan(enabled = true, enabledAt = time(enabledAt))
    @Test fun disabledHasNoAlarm() {
        assertNull(nextReminder(ReminderPlan(), time("2026-11-06 10:00"), timeZone = zone))
    }
    @Test fun enablingWaitsAnIntervalAndRecentDrinkRestartsIt() {
        val p = plan()
        assertEquals(time("2026-11-06 10:00"), nextReminder(p, time("2026-11-06 08:00"), timeZone = zone))
        assertEquals(time("2026-11-06 11:30"), nextReminder(p, time("2026-11-06 09:31"), time("2026-11-06 09:30"), zone))
    }
    @Test fun deliveredAlarmWaitsAgainAndOverdueAlarmCanDeliverNow() {
        val p = plan()
        assertEquals(time("2026-11-06 11:00"), nextReminder(p, time("2026-11-06 11:00"), timeZone = zone))
        assertEquals(time("2026-11-06 13:00"), nextReminder(p.copy(lastSent = time("2026-11-06 11:00")), time("2026-11-06 11:00"), timeZone = zone))
    }
    @Test fun quietHoursAndExclusiveEndPreventLateNotifications() {
        val p = plan()
        assertEquals(time("2026-11-07 08:00"), nextReminder(p, time("2026-11-06 22:00"), timeZone = zone))
        assertEquals(time("2026-11-07 08:00"), nextReminder(p, time("2026-11-06 21:30"), time("2026-11-06 21:00"), zone))
        assertEquals(time("2026-11-06 08:00"), nextReminder(p.copy(enabledAt = 0), time("2026-11-06 06:00"), timeZone = zone))
    }
    @Test fun weekdaysCrossWeekMonthAndYearBoundaries() {
        val monday = plan().copy(weekdays = setOf(Calendar.MONDAY))
        assertEquals(time("2026-11-09 08:00"), nextReminder(monday, time("2026-11-06 23:00"), timeZone = zone))
        assertEquals(time("2027-01-04 08:00"), nextReminder(monday, time("2026-12-31 23:00"), timeZone = zone))
    }
    @Test fun dailyEndUsesWallClockAcrossBothDaylightSavingChanges() {
        val p = plan().copy(enabledAt = 0, startMinute = 8*60, endMinute = 22*60)
        for(day in listOf("2026-03-29", "2026-10-25")) {
            assertEquals(time("$day 21:30"), nextReminder(p, time("$day 21:30"), timeZone = zone))
            assertTrue(nextReminder(p, time("$day 22:00"), timeZone = zone)!! > time("$day 22:00"))
        }
    }
    @Test fun clockCorrectionDoesNotDeferRemindersForDays() {
        val p = plan().copy(enabledAt = time("2026-12-01 10:00"), lastSent = time("2026-12-01 11:00"))
        assertEquals(time("2026-11-06 10:00"), nextReminder(p, time("2026-11-06 10:00"), time("2026-12-01 12:00"), zone))
    }
    @Test fun timeParsingRejectsInvalidInputAndAllowsMidnightEnd() {
        assertEquals(480, parseReminderTime("08:00"))
        assertEquals(1440, parseReminderTime("24:00"))
        for(value in listOf("", "8:00", "24:01", "08:60", "aa:bb", " 08:00")) assertNull(value, parseReminderTime(value))
    }
    @Test fun invalidPlansAreRejected() {
        for(factory in listOf<()->ReminderPlan>({ReminderPlan(startMinute=1000,endMinute=500)},
            {ReminderPlan(weekdays=emptySet())}, {ReminderPlan(intervalMinutes=0)}, {ReminderPlan(weekdays=setOf(8))})) {
            try { factory(); fail("Invalid plan accepted") } catch(expected: IllegalArgumentException) {}
        }
    }
}
