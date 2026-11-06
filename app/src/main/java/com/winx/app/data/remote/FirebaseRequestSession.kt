package com.winx.app.data.remote

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await

internal class FirebaseRequestSession {
    private val auth = FirebaseAuth.getInstance()
    private val user = auth.currentUser

    private suspend fun checkAccount() {
        currentCoroutineContext().ensureActive()
        if (user?.uid != auth.currentUser?.uid) {
            throw CancellationException("The signed-in account changed.")
        }
    }

    suspend fun <T> request(call: suspend (String) -> T): T {
        checkAccount()
        val signedInUser = checkNotNull(user) { "Sign in to use the journal." }

        try {
            val token = checkNotNull(signedInUser.getIdToken(false).await().token) {
                "Firebase did not return an ID token."
            }
            checkAccount()
            val response = call("Bearer $token")
            checkAccount()
            return response
        } catch (exception: Exception) {
            checkAccount()
            throw exception
        }
    }
}
