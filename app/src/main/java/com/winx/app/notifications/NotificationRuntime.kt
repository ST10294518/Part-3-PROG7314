package com.winx.app.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.AtomicFile
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.winx.app.data.remote.FirebaseRequestSession
import com.winx.app.data.remote.RetrofitClient
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit

object NotificationNavigation {
    data class Request(val uid: String, val eventId: String)
    val pending = kotlinx.coroutines.flow.MutableStateFlow<Request?>(null)
}

object NotificationRuntime {
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var foreground: Job? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var previousUid: String? = null

    @Synchronized
    private fun identity(context: Context): Pair<String, String> {
        // Never backed up or shared with another phone.
        val file = AtomicFile(File(context.noBackupFilesDir, "winx-push-installation.properties"))
        val p = Properties()
        try { file.openRead().use { p.load(it) } } catch (_: java.io.FileNotFoundException) { }
        if (p.getProperty("id") == null || p.getProperty("secret") == null) {
            p.setProperty("id", UUID.randomUUID().toString())
            p.setProperty("secret", UUID.randomUUID().toString() + UUID.randomUUID().toString())
            val stream = file.startWrite()
            try { p.store(stream, null); file.finishWrite(stream) }
            catch (e: Exception) { file.failWrite(stream); throw e }
        }
        return p.getProperty("id") to p.getProperty("secret")
    }

    fun initialise(context: Context) {
        val app = context.applicationContext
        WinxNotifications.createChannel(app)
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("winx-notification-recovery",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NotificationSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        previousUid = FirebaseAuth.getInstance().currentUser?.uid
        authListener = FirebaseAuth.AuthStateListener { auth ->
            if (auth.currentUser?.uid != previousUid) {
                previousUid = auth.currentUser?.uid
                WinxNotifications.clearAlerts(app)
                NotificationNavigation.pending.value = null
            }
            schedule(app)
        }.also { FirebaseAuth.getInstance().addAuthStateListener(it) }
    }

    fun schedule(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork("winx-notification-sync",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<NotificationSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build())
    }
    fun foreground(context: Context) {
        if (foreground?.isActive == true) return
        foreground = scope.launch {
            while (isActive) {
                try { synchronise(context.applicationContext) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { Log.d("WinxFCM", "Notification recovery waiting for connection/API.") }
                delay(30_000)
            }
        }
    }
    fun background() { foreground?.cancel(); foreground = null }

    @Suppress("DEPRECATION")
    suspend fun synchronise(context: Context): Unit = lock.withLock {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        val (id, secret) = identity(context)
        val api = RetrofitClient.notificationApi
        if (uid == null) {
            // The installation secret permits revocation after sign-out, including offline sign-out.
            check(api.revoke(DeviceRevocation(id, secret)).isSuccessful) { "Device revocation failed" }
            return@withLock
        }
        val session = FirebaseRequestSession(uid)
        var registrationFailed = false
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            val granted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val channelAllowed = Build.VERSION.SDK_INT < 26 || context.getSystemService(android.app.NotificationManager::class.java)
                .getNotificationChannel(WinxNotifications.CHANNEL_ID)?.importance != android.app.NotificationManager.IMPORTANCE_NONE
            val alerts = NotificationInbox.enabled(context, uid) && granted && channelAllowed &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            check(session.request { api.register(it, DeviceRegistration(id, secret, token, alerts)) }.isSuccessful) {
                "Device registration failed"
            }
            context.getSharedPreferences("winx_push", Context.MODE_PRIVATE).edit()
                .putString("fcm_token", token).putBoolean("registration_pending", false).apply()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { registrationFailed = true }
        for (key in listOf("pending_read", "pending_clear")) {
            val cutoff = NotificationInbox.preferences(context, uid).getLong("pending_clear_before", 0)
            for (batch in NotificationInbox.pending(context, uid, key).chunked(100)) {
                val result = session.request { bearer ->
                    if (key == "pending_read") api.read(bearer, NotificationIds(batch))
                    else api.clear(bearer, NotificationIds(batch, cutoff.takeIf { it > 0 }))
                }
                check(result.isSuccessful) { "Inbox update failed" }
                NotificationInbox.acknowledge(context, uid, key, batch.toSet())
            }
        }
        val records = session.request { api.inbox(it) }
        // Session checks ensure a response obtained for A cannot populate B's inbox.
        NotificationInbox.mergeServer(context, uid, records)
        if (registrationFailed) {
            Log.d("WinxFCM", "Inbox recovered; device registration still pending.")
            throw java.io.IOException("Device registration pending")
        }
        Log.d("WinxFCM", "Device registration and inbox recovery completed.")
    }
}

class NotificationSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        NotificationRuntime.synchronise(applicationContext)
        Result.success()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { Result.retry() }
}
