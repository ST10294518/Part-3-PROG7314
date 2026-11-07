package com.winx.app.notifications

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.winx.app.MainActivity
import com.winx.app.R

object WinxNotifications {
    const val CHANNEL_ID = "winx_updates"
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL_ID, "Winx updates", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Travel memories and account updates" })
    }
    @Synchronized
    fun show(context: Context, title: String, body: String,
             uid: String? = FirebaseAuth.getInstance().currentUser?.uid,
             eventId: String = java.util.UUID.randomUUID().toString()) {
        // Serialize the last UID check and posting with UI-driven account changes.
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                show(context.applicationContext, title, body, uid, eventId)
            }
            return
        }
        if (uid == null) return
        createChannel(context)
        if (FirebaseAuth.getInstance().currentUser?.uid != uid || !NotificationInbox.enabled(context, uid)) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val prefs = NotificationInbox.preferences(context, uid)
        val alerted = prefs.getStringSet("alerted", emptySet())?.toSet() ?: emptySet()
        if (eventId in alerted) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("winx://notification/$eventId")
            putExtra("winx_notification_uid", uid)
            putExtra("winx_notification_id", eventId)
        }
        val tap = PendingIntent.getActivity(context, eventId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(tap).setAutoCancel(true).build()
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) return
        try { manager.notify(uid, eventId.hashCode(), notification) }
        catch (_: SecurityException) { return }
        prefs.edit().putStringSet("alerted", (alerted + eventId).toList().takeLast(2000).toSet()).apply()
    }
    fun cancel(context: Context, uid: String, id: String) {
        NotificationManagerCompat.from(context).cancel(uid, id.hashCode())
    }
    fun clearAlerts(context: Context) { NotificationManagerCompat.from(context).cancelAll() }
}
