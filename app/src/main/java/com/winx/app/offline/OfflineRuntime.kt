package com.winx.app.offline

import android.app.Application
import android.content.Context
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object OfflineRuntime {
    lateinit var store: OfflineStore
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listener: FirebaseAuth.AuthStateListener? = null
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun initialise(context: Context) {
        store = OfflineStore(context)
        // Periodic recovery also covers a process death between SQLite commit and work enqueue.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "winx-offline-recovery", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<JournalSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network).build()
        )
        listener = FirebaseAuth.AuthStateListener { auth ->
            auth.currentUser?.uid?.let { uid ->
                scope.launch {
                    store.retryFailures(uid, authOnly = true)
                    schedule(context, uid)
                }
            }
        }.also { FirebaseAuth.getInstance().addAuthStateListener(it) }
    }

    fun schedule(context: Context, uid: String) {
        val work = OneTimeWorkRequestBuilder<JournalSyncWorker>()
            .setInputData(workDataOf("uid" to uid))
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        // Appending avoids dropping a newly queued entry while an earlier drain finishes.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "winx-journal-$uid", ExistingWorkPolicy.APPEND_OR_REPLACE, work
        )
    }
}

class WinxOfflineApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OfflineRuntime.initialise(this)
    }
}
