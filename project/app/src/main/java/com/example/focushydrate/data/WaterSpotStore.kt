package com.example.focushydrate.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class WaterSpotStore(context: Context) {
    private val preferences = context.getSharedPreferences("water_spots", Context.MODE_PRIVATE)
    fun load(): List<WaterSpot> {
        val array = JSONArray(preferences.getString("spots", "[]") ?: "[]")
        return (0 until array.length()).map { index ->
            val entry = array.getJSONObject(index)
            WaterSpot(entry.getString("id"), entry.getString("name"), entry.getDouble("latitude"),
                entry.getDouble("longitude"), entry.optString("ownerId", ""))
        }
    }
    fun loadFeedback(): List<SpotFeedback> {
        val array = JSONArray(preferences.getString("feedback", "[]") ?: "[]")
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            SpotFeedback(item.getString("spotId"), item.getString("authorId"), item.getString("authorName"),
                FeedbackStatus.valueOf(item.getString("status")), item.getString("note"), item.getLong("createdAt"))
        }.sortedByDescending { it.createdAt }
    }
    fun save(spots: List<WaterSpot>): Boolean = preferences.edit().putString("spots", toJson(spots)).commit()
    fun saveFeedback(reports: List<SpotFeedback>): Boolean = preferences.edit().putString("feedback", feedbackJson(reports)).commit()
    fun deleteSpot(id: String): Boolean = preferences.edit()
        .putString("spots", toJson(load().filterNot { it.id == id }))
        .putString("feedback", feedbackJson(loadFeedback().filterNot { it.spotId == id })).commit()
    fun deleteAccountData(accountId: String): Boolean {
        val deletedIds = load().filter { it.ownerId == accountId }.map { it.id }.toSet()
        return preferences.edit().putString("spots", toJson(load().filterNot { it.id in deletedIds }))
            .putString("feedback", feedbackJson(loadFeedback().filterNot { it.authorId == accountId || it.spotId in deletedIds })).commit()
    }
    private fun feedbackJson(reports: List<SpotFeedback>): String = JSONArray().apply {
        reports.forEach { put(JSONObject().put("spotId", it.spotId).put("authorId", it.authorId).put("authorName", it.authorName)
            .put("status", it.status.name).put("note", it.note).put("createdAt", it.createdAt)) }
    }.toString()
    companion object {
        fun toJson(spots: List<WaterSpot>, reports: List<SpotFeedback> = emptyList()): String = JSONArray().apply {
            val bySpot = reports.groupBy { it.spotId }
            spots.forEach { spot ->
                val feedback = bySpot[spot.id].orEmpty()
                put(JSONObject().put("id", spot.id).put("name", spot.name).put("ownerId", spot.ownerId)
                    .put("latitude", spot.latitude).put("longitude", spot.longitude)
                    .put("feedbackCount", feedback.size).put("lastFeedback", feedback.maxByOrNull { it.createdAt }?.status?.name ?: ""))
            }
        }.toString()
    }
}
