package com.winx.app.notifications

import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class WinxMessagingService : FirebaseMessagingService() {

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Keep the latest token for future backend registration.
        getSharedPreferences("winx_push", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .putBoolean("registration_pending", true)
            .apply()

        if (isDebugBuild()) {
            Log.d("WinxFCM", "FCM token: $token")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        val recipientUid = message.data["recipient_uid"]

        // Generic Firebase Console tests are allowed in debug builds.
        // Production messages must identify the intended account.
        val allowGenericTest =
            isDebugBuild() && message.notification != null

        if (!NotificationRules.acceptsRecipient(
                currentUid = uid,
                recipientUid = recipientUid,
                allowGenericTest = allowGenericTest
            )
        ) {
            Log.d("WinxFCM", "Message ignored: no matching signed-in account.")
            return
        }

        val accountUid = uid ?: return

        val title = message.data["winx_title"]
            ?: message.data["title"]
            ?: message.notification?.title
            ?: "Winx"

        val body = message.data["winx_body"]
            ?: message.data["body"]
            ?: message.notification?.body
            ?: return

        // A stable ID prevents duplicate history items.
        val eventId = message.data["event_id"]
            ?.takeIf { it.isNotBlank() }
            ?: message.messageId
            ?: return

        // Check again before saving in case the account changed.
        if (FirebaseAuth.getInstance().currentUser?.uid != accountUid) {
            return
        }

        val record = NotificationRecord(
            id = eventId,
            title = title,
            body = body,
            receivedAt = System.currentTimeMillis(),
            read = false
        )

        val added = NotificationInbox.add(
            context = this,
            uid = accountUid,
            record = record
        )

        if (!added) {
            return
        }

        Log.d("WinxFCM", "New message saved to notification history.")

        // Keep history while allowing the user to mute phone alerts.
        if (
            NotificationInbox.enabled(this, accountUid) &&
            FirebaseAuth.getInstance().currentUser?.uid == accountUid
        ) {
            WinxNotifications.show(
                context = this,
                title = title,
                body = body
            )
        }
    }

    private fun isDebugBuild(): Boolean {
        return applicationInfo.flags and
                ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
}