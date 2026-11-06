package com.winx.app.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.winx.app.data.remote.FirebaseRequestSession
import com.winx.app.data.remote.RetrofitClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

class JournalSyncWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    companion object { private val syncLock = Mutex() }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        syncLock.withLock { synchronise() }
    }

    private suspend fun synchronise(): Result {
        val auth = FirebaseAuth.getInstance()
        val uid = inputData.getString("uid") ?: auth.currentUser?.uid ?: return Result.success()
        if (!OfflineRules.maySync(uid, auth.currentUser?.uid)) return Result.success()
        val session = FirebaseRequestSession(uid)
        val store = OfflineRuntime.store
        val api = RetrofitClient.api
        var retry = false
        for (pending in store.snapshot(uid).pending.filter { it.state == "PENDING" }) {
            if (!OfflineRules.maySync(uid, auth.currentUser?.uid)) return Result.success()
            try {
                val response = session.request { token -> api.createOfflineEntry(token, pending.entry) }
                if (!response.isSuccessful) {
                    when (OfflineRules.failureFor(response.code())) {
                        OfflineRules.Failure.RETRY -> {
                            store.setState(uid, pending.operationId, "PENDING", "Connection/server problem. Automatic retry is scheduled.")
                            retry = true
                            break
                        }
                        OfflineRules.Failure.AUTH -> {
                            store.setState(uid, pending.operationId, "AUTH_REQUIRED", "Sign in again, then retry sync.")
                            break
                        }
                        OfflineRules.Failure.PERMANENT -> {
                            val reason = if (response.code() == 404) "Offline API is not deployed yet. Retry after deployment."
                                else "Server rejected this entry (HTTP ${response.code()}). Entry remains on this phone."
                            store.setState(uid, pending.operationId, "FAILED", reason)
                        }
                    }
                } else {
                    val cloud = response.body()
                    if (cloud == null || cloud.id <= 0 || cloud.clientOperationId != pending.operationId) {
                        store.setState(uid, pending.operationId, "FAILED", "Cloud service needs an update before syncing. Your entry is safe on this phone.")
                    } else {
                        store.acknowledge(uid, pending.operationId, cloud)
                    }
                }
            } catch (exception: CancellationException) {
                if (!OfflineRules.maySync(uid, auth.currentUser?.uid)) return Result.success()
                throw exception
            } catch (exception: Exception) {
                if (!OfflineRules.maySync(uid, auth.currentUser?.uid)) return Result.success()
                when (exception) {
                    is FirebaseAuthException -> store.setState(uid, pending.operationId, "AUTH_REQUIRED", "Sign in again, then retry sync.")
                    is IOException, is FirebaseNetworkException -> {
                        store.setState(uid, pending.operationId, "PENDING", "Waiting for a working connection. Automatic retry is scheduled.")
                        retry = true
                    }
                    else -> {
                        store.setState(uid, pending.operationId, "PENDING", "Sync was interrupted. Automatic retry is scheduled.")
                        retry = true
                    }
                }
                break
            }
        }
        if (OfflineRules.maySync(uid, auth.currentUser?.uid)) {
            try {
                val cloud = session.request { token -> api.getEntries(token) }
                store.replaceCache(uid, cloud)
            } catch (exception: CancellationException) {
                if (OfflineRules.maySync(uid, auth.currentUser?.uid)) throw exception
            } catch (_: Exception) {
                store.setProblem(uid, "Cloud refresh unavailable. Cached entries remain readable.")
                retry = true
            }
        }
        return if (retry) Result.retry() else Result.success()
    }
}
