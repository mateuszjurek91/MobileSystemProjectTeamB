package com.example.focushydrate.data

enum class FeedbackStatus { WORKING, NOT_WORKING, NO_WATER, WRONG_LOCATION }
data class SpotFeedback(val spotId: String, val authorId: String, val authorName: String,
    val status: FeedbackStatus, val note: String, val createdAt: Long) {
    init { require(spotId.isNotBlank()); require(authorId.isNotBlank()); require(note.length <= 500); require(createdAt >= 0) }
}
fun replaceFeedback(existing: List<SpotFeedback>, next: SpotFeedback): List<SpotFeedback> =
    (existing.filterNot { it.spotId == next.spotId && it.authorId == next.authorId } + next).sortedByDescending { it.createdAt }
