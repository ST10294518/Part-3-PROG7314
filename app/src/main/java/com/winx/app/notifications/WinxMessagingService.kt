package com.winx.app.notifications

import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class WinxMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Store the latest token locally.
        // Server registration will be added with Person 1.
        getSharedPreferences("winx_push", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()

        val debugBuild =
            applicationInfo.flags and
                    ApplicationInfo.FLAG_DEBUGGABLE != 0

        if (debugBuild) {
            Log.d("WinxFCM", "FCM token: $token")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Winx"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: return

        WinxNotifications.show(
            context = this,
            title = title,
            body = body
        )
    }
}