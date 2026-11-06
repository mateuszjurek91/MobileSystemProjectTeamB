package com.example.focushydrate

import com.example.focushydrate.data.*
import org.junit.Assert.*
import org.junit.Test

class SpotFeedbackTest {
    @Test fun editingOwnFeedbackReplacesItAndPreservesOtherUsers() {
        val first = SpotFeedback("spot", "user1", "One", FeedbackStatus.NOT_WORKING, "Broken", 1)
        val other = SpotFeedback("spot", "user2", "Two", FeedbackStatus.NO_WATER, "", 2)
        val updated = first.copy(status = FeedbackStatus.WORKING, note = "Fixed", createdAt = 3)
        val result = replaceFeedback(listOf(first, other), updated)
        assertEquals(2, result.size); assertEquals(updated, result.first()); assertTrue(result.contains(other))
    }
    @Test fun feedbackDoesNotReplaceReportsForOtherSpots() {
        val first = SpotFeedback("spot1", "user", "One", FeedbackStatus.WORKING, "", 1)
        val next = first.copy(spotId = "spot2", createdAt = 2)
        assertEquals(2, replaceFeedback(listOf(first), next).size)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedFeedback() {
        SpotFeedback("spot", "user", "One", FeedbackStatus.NO_WATER, "a".repeat(501), 1)
    }
}
