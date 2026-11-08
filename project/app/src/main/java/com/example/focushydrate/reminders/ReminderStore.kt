package com.example.focushydrate.reminders

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ReminderStore(context: Context) {
    private val preferences = context.getSharedPreferences("drink_reminders", Context.MODE_PRIVATE)
    fun load(accountId: String): ReminderPlan {
        val raw = preferences.getString(accountId, null) ?: return ReminderPlan()
        val json = JSONObject(raw)
        val days = json.getJSONArray("days")
        return ReminderPlan(json.getBoolean("enabled"), json.getInt("start"), json.getInt("end"),
            json.getInt("interval"), (0 until days.length()).map { days.getInt(it) }.toSet(),
            json.getLong("enabledAt"), json.getLong("lastSent"))
    }
    fun save(accountId: String, plan: ReminderPlan): Boolean {
        val json = JSONObject().put("enabled", plan.enabled).put("start", plan.startMinute)
            .put("end", plan.endMinute).put("interval", plan.intervalMinutes)
            .put("days", JSONArray(plan.weekdays.sorted())).put("enabledAt", plan.enabledAt).put("lastSent", plan.lastSent)
        return preferences.edit().putString(accountId, json.toString()).commit()
    }
    fun delete(accountId: String): Boolean = preferences.edit().remove(accountId).commit()
    fun token(): String? = preferences.getString("_alarm_token", null)
    fun setToken(token: String?): Boolean = preferences.edit().putString("_alarm_token", token).commit()
}
