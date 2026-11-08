package com.example.focushydrate.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class HydrationStore(context: Context) {
    private val prefs = context.getSharedPreferences("hydration_history", Context.MODE_PRIVATE)
    fun load(accountId: String): List<DailyIntake> {
        val list = JSONArray(prefs.getString(accountId, "[]") ?: "[]")
        return (0 until list.length()).map { index ->
            val item = list.getJSONObject(index)
            DailyIntake(item.getString("day"), item.getInt("amount"), item.getInt("goal"))
        }.sortedBy { it.day }
    }
    fun save(accountId: String, history: List<DailyIntake>, drinkAt: Long? = null): Boolean {
        val list = JSONArray()
        history.forEach { list.put(JSONObject().put("day", it.day).put("amount", it.millilitres).put("goal", it.goal)) }
        val editor = prefs.edit().putString(accountId, list.toString())
        if (drinkAt != null) editor.putLong("last_drink_$accountId", drinkAt)
        return editor.commit()
    }
    fun lastDrink(accountId: String): Long = prefs.getLong("last_drink_$accountId", 0)
    fun delete(accountId: String): Boolean = prefs.edit().remove(accountId).remove("last_drink_$accountId").commit()
}
