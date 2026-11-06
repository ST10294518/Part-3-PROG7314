package com.winx.app.notifications

data class NotificationRecord(
    val id: String,
    val title: String,
    val body: String,
    val receivedAt: Long,
    val read: Boolean = false
)

object NotificationRules {
    fun acceptsRecipient(currentUid: String?, recipientUid: String?, allowGenericTest: Boolean): Boolean {
        if (currentUid.isNullOrBlank()) return false
        return if (recipientUid.isNullOrBlank()) allowGenericTest else currentUid == recipientUid
    }

    /** A repeated event keeps its read flag and does not become a second alert. */
    fun append(existing: List<NotificationRecord>, incoming: NotificationRecord): List<NotificationRecord> {
        if (existing.any { it.id == incoming.id }) return existing
        return (existing + incoming).sortedByDescending { it.receivedAt }.take(100)
    }
}
