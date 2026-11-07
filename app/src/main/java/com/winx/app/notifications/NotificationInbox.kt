package com.winx.app.notifications

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object NotificationInbox {
    fun preferences(context: Context, uid: String): SharedPreferences =
        context.getSharedPreferences("winx_notifications_$uid", Context.MODE_PRIVATE)
    fun enabled(context: Context, uid: String): Boolean = preferences(context, uid).getBoolean("enabled", true)
    fun setEnabled(context: Context, uid: String, enabled: Boolean) {
        preferences(context, uid).edit().putBoolean("enabled", enabled).apply()
        if (!enabled) WinxNotifications.clearAlerts(context)
        NotificationRuntime.schedule(context)
    }
    @Synchronized
    fun read(context: Context, uid: String): List<NotificationRecord> = try {
        val array = JSONArray(preferences(context, uid).getString("history", "[]"))
        (0 until array.length()).mapNotNull { i ->
            val n = array.optJSONObject(i) ?: return@mapNotNull null
            val id = n.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            NotificationRecord(id, n.optString("title"), n.optString("body"), n.optLong("receivedAt"),
                n.optBoolean("read"), n.optString("kind"), if (n.isNull("entryId")) null else n.optInt("entryId"))
        }.sortedByDescending { it.receivedAt }.take(100)
    } catch (_: Exception) { emptyList() }

    @Synchronized
    fun add(context: Context, uid: String, record: NotificationRecord): Boolean {
        if (record.id in hidden(context, uid) || record.receivedAt <= preferences(context, uid).getLong("clear_before", 0)) return false
        val existing = read(context, uid)
        if (existing.any { it.id == record.id }) return false
        val pendingRead = pending(context, uid, "pending_read")
        save(context, uid, NotificationRules.append(existing, record.copy(read = record.read || record.id in pendingRead)))
        return true
    }

    @Synchronized
    fun mergeServer(context: Context, uid: String, server: List<NotificationRecord>) {
        val old = read(context, uid).associateBy { it.id }
        val hidden = hidden(context, uid)
        val pendingRead = pending(context, uid, "pending_read")
        val before = preferences(context, uid).getLong("clear_before", 0)
        val merged = server.filter { it.id !in hidden && it.receivedAt > before }.map {
            it.copy(read = it.read || it.id in pendingRead || old[it.id]?.read == true)
        }
        // Console-only debug messages are local. Server events are authoritative.
        val console = old.values.filter { it.kind.isBlank() && it.id !in hidden }
        save(context, uid, (merged + console).distinctBy { it.id }.sortedByDescending { it.receivedAt }.take(100))
    }

    @Synchronized
    fun markRead(context: Context, uid: String, id: String) {
        save(context, uid, read(context, uid).map { if (it.id == id) it.copy(read = true) else it })
        WinxNotifications.cancel(context, uid, id)
        queue(context, uid, "pending_read", setOf(id))
        NotificationRuntime.schedule(context)
    }
    @Synchronized
    fun markAllRead(context: Context, uid: String) {
        val records = read(context, uid)
        save(context, uid, records.map { it.copy(read = true) })
        WinxNotifications.clearAlerts(context)
        queue(context, uid, "pending_read", records.map { it.id }.toSet())
        NotificationRuntime.schedule(context)
    }
    @Synchronized
    fun clear(context: Context, uid: String) {
        val records = read(context, uid)
        val ids = records.map { it.id }.toSet()
        val before = maxOf(preferences(context, uid).getLong("clear_before", 0), records.filter { it.kind.isNotBlank() }.maxOfOrNull { it.receivedAt } ?: 0)
        preferences(context, uid).edit().putLong("clear_before", before).putLong("pending_clear_before", before).apply()
        queue(context, uid, "pending_clear", ids)
        preferences(context, uid).edit().putStringSet("hidden", (hidden(context, uid) + ids).takeLastSet(2000)).apply()
        save(context, uid, emptyList())
        WinxNotifications.clearAlerts(context)
        NotificationRuntime.schedule(context)
    }
    private fun Set<String>.takeLastSet(limit: Int) = toList().takeLast(limit).toSet()
    private fun hidden(context: Context, uid: String) = pending(context, uid, "hidden")
    @Synchronized
    fun pending(context: Context, uid: String, key: String): Set<String> =
        preferences(context, uid).getStringSet(key, emptySet())?.toSet() ?: emptySet()
    private fun queue(context: Context, uid: String, key: String, ids: Set<String>) {
        preferences(context, uid).edit().putStringSet(key, pending(context, uid, key) + ids).apply()
    }
    @Synchronized
    fun acknowledge(context: Context, uid: String, key: String, ids: Set<String>) {
        preferences(context, uid).edit().putStringSet(key, pending(context, uid, key) - ids).apply()
    }
    private fun save(context: Context, uid: String, records: List<NotificationRecord>) {
        val array = JSONArray()
        records.forEach { n -> array.put(JSONObject().put("id", n.id).put("title", n.title).put("body", n.body)
            .put("receivedAt", n.receivedAt).put("read", n.read).put("kind", n.kind)
            .put("entryId", n.entryId ?: JSONObject.NULL)) }
        preferences(context, uid).edit().putString("history", array.toString()).apply()
    }
}
