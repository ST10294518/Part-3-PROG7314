package com.winx.app.offline

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.winx.app.data.remote.TravelEntryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PendingJournalEntry(
    val operationId: String,
    val entry: TravelEntryDto,
    val state: String,
    val error: String,
    val createdAt: Long
)

data class OfflineSnapshot(
    val cached: List<TravelEntryDto> = emptyList(),
    val pending: List<PendingJournalEntry> = emptyList(),
    val lastRefresh: Long = 0,
    val problem: String = ""
)

/** All queries include UID. Pending data and cloud cache are separate tables. */
class OfflineStore(context: Context, name: String = "winx_offline_v1.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 1) {
    private val gson = Gson()
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE cache(uid TEXT NOT NULL, server_id INTEGER NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(uid,server_id))")
        db.execSQL("CREATE TABLE outbox(uid TEXT NOT NULL, operation_id TEXT NOT NULL, payload TEXT NOT NULL, state TEXT NOT NULL, error TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(uid,operation_id))")
        db.execSQL("CREATE TABLE sync_meta(uid TEXT PRIMARY KEY NOT NULL, refreshed_at INTEGER NOT NULL DEFAULT 0, problem TEXT NOT NULL DEFAULT '')")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future versions must add migrations; never discard pending journals.
        error("Unsupported offline database upgrade: $oldVersion to $newVersion")
    }
    private fun changed() { revision.update { it + 1 } }

    @Synchronized
    fun enqueue(uid: String, operationId: String, entry: TravelEntryDto) {
        require(uid.isNotBlank())
        require(entry.id == 0 && entry.clientOperationId == operationId)
        writableDatabase.insertWithOnConflict("outbox", null, ContentValues().apply {
            put("uid", uid); put("operation_id", operationId); put("payload", gson.toJson(entry))
            put("state", "PENDING"); put("error", ""); put("created_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_IGNORE)
        changed()
    }

    @Synchronized
    fun snapshot(uid: String): OfflineSnapshot {
        require(uid.isNotBlank())
        val cached = readableDatabase.query("cache", arrayOf("payload"), "uid=?", arrayOf(uid), null, null, "server_id DESC").use { cursor ->
            buildList { while (cursor.moveToNext()) add(gson.fromJson(cursor.getString(0), TravelEntryDto::class.java)) }
        }
        val pending = readableDatabase.query("outbox", null, "uid=?", arrayOf(uid), null, null, "created_at ASC").use { c ->
            buildList {
                while (c.moveToNext()) add(PendingJournalEntry(
                    c.getString(c.getColumnIndexOrThrow("operation_id")),
                    gson.fromJson(c.getString(c.getColumnIndexOrThrow("payload")), TravelEntryDto::class.java),
                    c.getString(c.getColumnIndexOrThrow("state")),
                    c.getString(c.getColumnIndexOrThrow("error")),
                    c.getLong(c.getColumnIndexOrThrow("created_at"))
                ))
            }
        }
        var refreshed = 0L
        var problem = ""
        readableDatabase.query("sync_meta", arrayOf("refreshed_at", "problem"), "uid=?", arrayOf(uid), null, null, null).use {
            if (it.moveToFirst()) { refreshed = it.getLong(0); problem = it.getString(1) }
        }
        return OfflineSnapshot(cached, pending, refreshed, problem)
    }

    @Synchronized
    fun hasSnapshot(uid: String): Boolean {
        val refreshed = readableDatabase.rawQuery(
            "SELECT refreshed_at FROM sync_meta WHERE uid=?", arrayOf(uid)
        ).use { it.moveToFirst() && it.getLong(0) > 0 }
        return refreshed || readableDatabase.rawQuery(
            "SELECT 1 FROM cache WHERE uid=? LIMIT 1", arrayOf(uid)
        ).use { it.moveToFirst() }
    }

    @Synchronized
    fun replaceCache(uid: String, entries: List<TravelEntryDto>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("cache", "uid=?", arrayOf(uid))
            entries.filter { !it.isDeleted && it.id > 0 }.forEach { cacheRow(db, uid, it) }
            db.insertWithOnConflict("sync_meta", null, ContentValues().apply {
                put("uid", uid); put("refreshed_at", System.currentTimeMillis()); put("problem", "")
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        changed()
    }

    private fun cacheRow(db: SQLiteDatabase, uid: String, entry: TravelEntryDto) {
        db.insertWithOnConflict("cache", null, ContentValues().apply {
            put("uid", uid); put("server_id", entry.id); put("payload", gson.toJson(entry))
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun cacheOne(uid: String, entry: TravelEntryDto) {
        if (entry.id > 0 && !entry.isDeleted) cacheRow(writableDatabase, uid, entry)
        changed()
    }

    @Synchronized
    fun removeCached(uid: String, id: Int) {
        writableDatabase.delete("cache", "uid=? AND server_id=?", arrayOf(uid, id.toString()))
        changed()
    }

    /** Acknowledgement and cached cloud record commit atomically. A crash cannot lose both. */
    @Synchronized
    fun acknowledge(uid: String, operationId: String, cloud: TravelEntryDto) {
        require(cloud.id > 0 && cloud.clientOperationId == operationId)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val removed = db.delete("outbox", "uid=? AND operation_id=?", arrayOf(uid, operationId))
            if (removed > 0) {
                if (cloud.isDeleted) db.delete("cache", "uid=? AND server_id=?", arrayOf(uid, cloud.id.toString()))
                else cacheRow(db, uid, cloud)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        changed()
    }

    @Synchronized
    fun setState(uid: String, operationId: String, state: String, error: String) {
        writableDatabase.update("outbox", ContentValues().apply {
            put("state", state); put("error", error)
        }, "uid=? AND operation_id=?", arrayOf(uid, operationId))
        changed()
    }

    @Synchronized
    fun retryFailures(uid: String, authOnly: Boolean = false) {
        writableDatabase.update("outbox", ContentValues().apply {
            put("state", "PENDING"); put("error", "")
        }, if (authOnly) "uid=? AND state='AUTH_REQUIRED'" else "uid=? AND state IN ('FAILED','AUTH_REQUIRED')", arrayOf(uid))
        changed()
    }

    @Synchronized
    fun setProblem(uid: String, message: String) {
        val db = writableDatabase
        db.execSQL("INSERT OR IGNORE INTO sync_meta(uid) VALUES(?)", arrayOf(uid))
        db.update("sync_meta", ContentValues().apply { put("problem", message) }, "uid=?", arrayOf(uid))
        changed()
    }
}
