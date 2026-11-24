package com.example.focushydrate

import com.example.focushydrate.data.*
import org.junit.Assert.*
import org.junit.Test

class WaterSpotTest {
    @Test
    fun acceptsPolishAndInternationalLocations() {
        assertTrue(validCoordinates(51.7592, 19.4560))
        assertTrue(validCoordinates(-33.8688, 151.2093))
    }

    @Test
    fun rejectsCoordinatesOutsideTheMap() {
        assertFalse(validCoordinates(91.0, 19.0))
        assertFalse(validCoordinates(52.0, 181.0))
        assertFalse(validCoordinates(-86.0, 19.0))
    }

    @Test
    fun rejectsNonFiniteCoordinates() {
        assertFalse(validCoordinates(Double.NaN, 19.0))
        assertFalse(validCoordinates(52.0, Double.POSITIVE_INFINITY))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnnamedSpots() {
        WaterSpot("id", "   ", 51.7592, 19.4560)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOverlongNames() {
        WaterSpot("id", "a".repeat(81), 51.7592, 19.4560)
    }

    @Test
    fun distanceMeters_returnsZeroForSameCoordinates() {
        assertEquals(0.0, distanceMeters(51.7592, 19.4560, 51.7592, 19.4560), 0.01)
    }

    @Test
    fun hasNearbySpot_detectsDuplicateWithinDefaultRadius() {
        val existing = listOf(WaterSpot("1", "Punkt Łódź", 51.75920, 19.45600, "user1"))
        // ~11 metrów na północ
        assertTrue(hasNearbySpot(existing, 51.75930, 19.45600))
    }

    @Test
    fun hasNearbySpot_allowsSpotsOutsideRadiusOrWhenEmpty() {
        assertFalse(hasNearbySpot(emptyList(), 51.75920, 19.45600))

        val existing = listOf(WaterSpot("1", "Punkt Łódź", 51.75920, 19.45600, "user1"))
        // ~55 metrów na północ
        assertFalse(hasNearbySpot(existing, 51.75970, 19.45600))
    }

    @Test
    fun replaceFeedback_overwritesPreviousReportFromSameAuthor() {
        val initial = listOf(
            SpotFeedback("spot1", "user1", "Jan", FeedbackStatus.WORKING, "Działa", 1000L),
            SpotFeedback("spot1", "user2", "Anna", FeedbackStatus.WORKING, "OK", 1500L)
        )
        val updated = SpotFeedback("spot1", "user1", "Jan", FeedbackStatus.NO_WATER, "Brak wody", 2000L)

        val result = replaceFeedback(initial, updated)

        assertEquals(2, result.size)
        assertEquals(FeedbackStatus.NO_WATER, result.first { it.authorId == "user1" }.status)
        assertEquals("Brak wody", result.first { it.authorId == "user1" }.note)
    }
}