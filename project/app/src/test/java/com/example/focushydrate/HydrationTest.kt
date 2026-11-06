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
}
