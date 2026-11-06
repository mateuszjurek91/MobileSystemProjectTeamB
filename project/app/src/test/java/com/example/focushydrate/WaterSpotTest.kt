package com.example.focushydrate

import com.example.focushydrate.data.WaterSpot
import com.example.focushydrate.data.validCoordinates
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
}
