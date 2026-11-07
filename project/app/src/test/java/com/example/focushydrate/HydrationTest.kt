package com.example.focushydrate

import com.example.focushydrate.data.*
import org.junit.Assert.*
import org.junit.Test

class HydrationTest {
    @Test fun loggingUpdatesOnlyTheSelectedDayAndNeverGoesBelowZero() {
        val first = listOf(DailyIntake("2026-11-05", 1500))
        val next = logWater(first, "2026-11-06", 250)
        assertEquals(1500, next.first().millilitres); assertEquals(250, next.last().millilitres)
        assertEquals(0, logWater(next, "2026-11-06", -500).last().millilitres)
        assertEquals(1500, first.single().millilitres)
    }
    @Test fun calendarHandlesLeapYearsAndMonthBoundaries() {
        assertEquals(29, Days.monthDays("2024-02-01").size)
        assertEquals(28, Days.monthDays("2025-02-01").size)
        assertEquals("2026-12-01", Days.shiftMonth("2027-01-30", -1))
        assertEquals("2027-01-01", Days.shift("2026-12-31", 1))
    }
    @Test fun weeklyChartIncludesMissingDaysAsZeroAndExcludesFuture() {
        val history = listOf(DailyIntake("2026-11-01", 1000), DailyIntake("2026-11-06", 500), DailyIntake("2026-11-07", 2000))
        val points = historyPoints(history, HistoryRange.WEEK, "2026-11-06")
        assertEquals(7, points.size); assertEquals("2026-10-31", points.first().day)
        assertEquals(0L, points.first().millilitres); assertEquals(1500L, points.sumOf { it.millilitres })
    }
    @Test fun monthlyChartContainsExactlyThirtyDays() {
        val points = historyPoints(emptyList(), HistoryRange.MONTH, "2026-03-01")
        assertEquals(30, points.size); assertEquals("2026-01-31", points.first().day)
    }
    @Test fun allTimeAggregatesMonthsWithoutLosingAmounts() {
        val history = listOf(DailyIntake("2026-09-10", 500), DailyIntake("2026-09-11", 1000), DailyIntake("2026-11-06", 250))
        val points = historyPoints(history, HistoryRange.ALL, "2026-11-06")
        assertEquals(listOf(1500L, 0L, 250L), points.map { it.millilitres })
    }
    @Test fun exceedingGoalFillsCalendarTileWithoutTruncatingActualIntake() {
        val day = DailyIntake("2026-11-06", 2500, 2000)
        assertEquals(1f, day.progress, 0f); assertEquals(2500, day.millilitres)
    }
    @Test fun resetDoesNotRemovePreviousDays() {
        val history = listOf(DailyIntake("2026-11-05", 1750), DailyIntake("2026-11-06", 1000))
        val reset = logWater(history, "2026-11-06", -1000)
        assertEquals(1750, reset.first().millilitres); assertEquals(0, reset.last().millilitres)
    }

    @Test fun optimizedRangesMatchDailyReferenceIncludingEmptyMonthsAndFutureEntries() {
        val today = "2026-11-07"
        val history = (0..1095 step 17).map { offset ->
            DailyIntake(Days.shift(today, -offset), offset * 31 % 3500)
        } + DailyIntake("2027-01-01", 2000)
        for (range in HistoryRange.entries) {
            val start = rangeStart(history, range, today)
            val amounts = history.filter { it.day <= today }.associate { it.day to it.millilitres.toLong() }
            val daily = Days.between(start, today).map { ChartPoint(it, amounts[it] ?: 0L) }
            val expected = if (range == HistoryRange.ALL || range == HistoryRange.YEAR) {
                daily.groupBy { it.day.take(7) }.map { (month, entries) ->
                    ChartPoint("$month-01", entries.sumOf { it.millilitres })
                }
            } else daily
            assertEquals(range.name, expected, historyPoints(history, range, today))
        }
    }

    @Test fun averageCountsCalendarDaysAcrossDaylightSavingAndLeapDay() {
        val previous = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Warsaw"))
            assertEquals(3, inclusiveDayCount("2026-03-28", "2026-03-30"))
            assertEquals(3, inclusiveDayCount("2026-10-24", "2026-10-26"))
            assertEquals(3, inclusiveDayCount("2024-02-28", "2024-03-01"))
            assertEquals(1, inclusiveDayCount("2026-11-07", "2026-11-07"))
        } finally { java.util.TimeZone.setDefault(previous) }
    }
}
