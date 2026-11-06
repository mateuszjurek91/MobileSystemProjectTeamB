package com.example.focushydrate.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PreferencesAccountStorage(context: Context) : AccountStorage {
    private val prefs = context.getSharedPreferences("local_accounts", Context.MODE_PRIVATE)
    override fun read(): AccountsSnapshot {
        val list = JSONArray(prefs.getString("accounts", "[]") ?: "[]")
        val accounts = (0 until list.length()).map { index ->
            val item = list.getJSONObject(index)
            AccountRecord(AccountProfile(item.getString("id"), item.getString("email"), item.getString("username"),
                item.getBoolean("consent")), PasswordDigest(item.getString("salt"), item.getString("hash")))
        }
        return AccountsSnapshot(accounts, prefs.getString("session", null))
    }
    override fun write(snapshot: AccountsSnapshot): Boolean {
        val list = JSONArray()
        snapshot.accounts.forEach { record ->
            list.put(JSONObject().put("id", record.profile.id).put("email", record.profile.email)
                .put("username", record.profile.username).put("consent", record.profile.emailNotifications)
                .put("salt", record.password.salt).put("hash", record.password.hash))
        }
        return prefs.edit().putString("accounts", list.toString()).putString("session", snapshot.sessionId).commit()
    }
}
