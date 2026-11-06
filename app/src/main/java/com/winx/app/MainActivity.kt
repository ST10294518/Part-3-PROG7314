package com.winx.app

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.google.firebase.auth.FirebaseAuth
import com.winx.app.auth.BiometricPolicy
import com.winx.app.navigation.AppNavigation
import com.winx.app.ui.theme.WinxTheme
import com.winx.app.utils.T
import com.winx.app.utils.localise
import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.messaging.FirebaseMessaging
import com.winx.app.notifications.WinxNotifications

class MainActivity : FragmentActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val settings by lazy {
        getSharedPreferences("winx_biometric", MODE_PRIVATE)
    }

    private var signedInUid by mutableStateOf<String?>(null)
    private var biometricEnabled by mutableStateOf(false)
    // Deliberately not saved: restarting the process must require verification.
    private var sessionUnlocked by mutableStateOf(false)
    private var promptRunning by mutableStateOf(false)
    private var lockMessage by mutableStateOf("")
    private var sessionGeneration by mutableStateOf(0)
    private var observedUid: String? = null
    // Only the explicit recovery action allows fresh account login as fallback.
    private var recoveryRequested = false
    private lateinit var biometricPrompt: BiometricPrompt
    private var pendingUid: String? = null
    private var pendingSuccess: (() -> Unit)? = null
    private var pendingResult: ((Boolean, String) -> Unit)? = null

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            biometricEnabled = signedInUid?.let {
                settings.getBoolean("enabled_$it", false)
            } ?: false
        }

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val newUid = firebaseAuth.currentUser?.uid
        if (newUid != observedUid) {
            val freshSignIn = observedUid == null && newUid != null
            biometricPrompt.cancelAuthentication()
            clearPrompt()
            observedUid = newUid
            signedInUid = newUid
            sessionGeneration++
            // Normal sign-in to an opted-in account still requires biometrics.
            // Recovery grants access only after an explicit sign-out/login action.
            sessionUnlocked = freshSignIn && recoveryRequested
            if (newUid != null) recoveryRequested = false
            lockMessage = ""
        }
        biometricEnabled = newUid?.let {
            settings.getBoolean("enabled_$it", false)
        } ?: false
    }
    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            Log.d(
                "WinxFCM",
                "Notification permission granted: $granted"
            )
        }

    private fun initialisePushNotifications() {
        WinxNotifications.createChannel(this)

        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result

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
                } else {
                    Log.w(
                        "WinxFCM",
                        "Unable to obtain notification token",
                        task.exception
                    )
                }
            }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initialisePushNotifications()
        sessionGeneration = savedInstanceState?.getInt("sessionGeneration", 0) ?: 0
        observedUid = auth.currentUser?.uid
        signedInUid = observedUid
        biometricEnabled = observedUid?.let {
            settings.getBoolean("enabled_$it", false)
        } ?: false

        // Recreate the prompt callback early in the Activity lifecycle.
        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    val expectedUid = pendingUid
                    val action = pendingSuccess
                    val callback = pendingResult
                    clearPrompt()
                    if (expectedUid == null || auth.currentUser?.uid != expectedUid ||
                        !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    ) return
                    Log.d("WinxBiometric", "Biometric verification succeeded")
                    action?.invoke()
                    callback?.invoke(true, "Biometric preference updated.")
                }

                override fun onAuthenticationFailed() {
                    // A failed attempt is not successful authentication.
                    lockMessage = "Biometric not recognised. Try again."
                    Log.d("WinxBiometric", "Biometric attempt not recognised")
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    val callback = pendingResult
                    clearPrompt()
                    lockMessage = message.toString()
                    callback?.invoke(false, message.toString())
                    Log.d("WinxBiometric", "Biometric prompt ended with code $code")
                }
            }
        )

        settings.registerOnSharedPreferenceChangeListener(preferenceListener)
        auth.addAuthStateListener(authListener)

        setContent {
            WinxTheme {
                val holder = rememberSaveableStateHolder()
                val locked = BiometricPolicy.requiresUnlock(
                    signedIn = signedInUid != null,
                    enabled = biometricEnabled,
                    sessionUnlocked = sessionUnlocked
                )
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (locked) {
                            // Back cannot reveal protected content behind this screen.
                            BackHandler { moveTaskToBack(true) }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(T("Winx is locked"))
                                Text(T("Verify your identity to continue."))
                                if (lockMessage.isNotBlank()) {
                                    Text(T(lockMessage))
                                }
                                Button(
                                    enabled = !promptRunning,
                                    onClick = {
                                        startVerification(
                                            title = "Unlock Winx",
                                            onSuccess = {
                                                sessionUnlocked = true
                                                lockMessage = ""
                                            }
                                        )
                                    }
                                ) {
                                    Text(T("Unlock with biometrics"))
                                }
                                TextButton(
                                    enabled = !promptRunning,
                                    onClick = {
                                        recoveryRequested = true
                                        auth.signOut()
                                    }
                                ) {
                                    Text(T("Sign out and sign in again"))
                                }
                                Text(T("Account login is required after signing out."))
                            }
                        } else {
                            // Preserve navigation/drafts while the same session is locked.
                            // A sign-out starts a new key, excluding the previous user's state.
                            holder.SaveableStateProvider(
                                "${signedInUid ?: "signed-out"}:$sessionGeneration"
                            ) {
                                AppNavigation()
                            }
                        }
                    }
                }
            }
        }
    }

    fun isBiometricEnabled(uid: String): Boolean =
        settings.getBoolean("enabled_$uid", false)

    fun biometricUnavailableMessage(): String? = when (
        BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        )
    ) {
        BiometricManager.BIOMETRIC_SUCCESS -> null
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            "Add a fingerprint or supported biometric in your phone settings first."
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
            "This phone has no supported strong biometric sensor."
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
            "The biometric sensor is temporarily unavailable. Try again later."
        else -> "Strong biometric authentication is unavailable on this phone."
    }

    fun requestBiometricChange(
        enable: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onResult(false, "Please sign in again.")
            return
        }
        startVerification(
            title = if (enable) "Enable biometric protection" else "Disable biometric protection",
            onSuccess = {
                settings.edit().putBoolean("enabled_$uid", enable).apply()
                sessionUnlocked = true
                biometricEnabled = enable
            },
            onResult = onResult
        )
    }

    private fun startVerification(
        title: String,
        onSuccess: () -> Unit,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        if (promptRunning) return
        val uid = auth.currentUser?.uid ?: return
        val unavailable = biometricUnavailableMessage()
        if (unavailable != null) {
            lockMessage = unavailable
            onResult?.invoke(false, unavailable)
            return
        }
        pendingUid = uid
        pendingSuccess = onSuccess
        pendingResult = onResult
        promptRunning = true
        lockMessage = ""
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(localise(this, title))
            .setSubtitle(localise(this, "Verify using your registered biometric"))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(localise(this, "Cancel"))
            .build()
        try {
            biometricPrompt.authenticate(info)
        } catch (_: RuntimeException) {
            clearPrompt()
            lockMessage = "Unable to start biometric verification. Please try again."
            onResult?.invoke(false, lockMessage)
        }
    }

    private fun clearPrompt() {
        promptRunning = false
        pendingUid = null
        pendingSuccess = null
        pendingResult = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("sessionGeneration", sessionGeneration)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        if (biometricEnabled && signedInUid != null) {
            sessionUnlocked = false
            biometricPrompt.cancelAuthentication()
            clearPrompt()
        }
        super.onStop()
    }

    override fun onDestroy() {
        auth.removeAuthStateListener(authListener)
        settings.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onDestroy()
    }
}
