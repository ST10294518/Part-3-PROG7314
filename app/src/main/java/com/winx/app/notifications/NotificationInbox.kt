package com.winx.app.notifications

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object NotificationInbox {
    fun preferences(context: Context, uid: String): SharedPreferences =
        context.getSharedPreferences("winx_notifications_$uid", Context.MODE_PRIVATE)

    fun enabled(context: Context, uid: String): Boolean =
        preferences(context, uid).getBoolean("enabled", true)

    fun setEnabled(context: Context, uid: String, enabled: Boolean) {
        preferences(context, uid).edit().putBoolean("enabled", enabled).apply()
        if (!enabled) WinxNotifications.clearAlerts(context)
    }

    @Synchronized
    fun read(context: Context, uid: String): List<NotificationRecord> = try {
        val array = JSONArray(preferences(context, uid).getString("history", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) return@mapNotNull null
            NotificationRecord(id, item.optString("title"), item.optString("body"),
                item.optLong("receivedAt"), item.optBoolean("read", false))
        }.sortedByDescending { it.receivedAt }.take(100)
    } catch (_: Exception) { emptyList() }

    @Synchronized
    fun add(context: Context, uid: String, record: NotificationRecord): Boolean {
        val existing = read(context, uid)
        if (existing.any { it.id == record.id }) return false
        save(context, uid, NotificationRules.append(existing, record))
        return true
    }

    @Synchronized
    fun markAllRead(context: Context, uid: String) {
        save(context, uid, read(context, uid).map { it.copy(read = true) })
    }

    @Synchronized
    fun clear(context: Context, uid: String) {
        save(context, uid, emptyList())
        WinxNotifications.clearAlerts(context)
    }

    private fun save(context: Context, uid: String, records: List<NotificationRecord>) {
        val array = JSONArray()
        records.forEach {
            array.put(JSONObject().put("id", it.id).put("title", it.title)
                .put("body", it.body).put("receivedAt", it.receivedAt).put("read", it.read))
        }
        preferences(context, uid).edit().putString("history", array.toString()).apply()
    }
}
