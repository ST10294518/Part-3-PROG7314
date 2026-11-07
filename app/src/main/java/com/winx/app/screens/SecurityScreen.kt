package com.winx.app.screens

import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.winx.app.MainActivity
import com.winx.app.utils.T

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) {
        var candidate = context
        while (candidate is ContextWrapper && candidate !is MainActivity) {
            candidate = candidate.baseContext
        }
        candidate as? MainActivity
    }
    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser
    val uid = user?.uid
    if (user == null || uid == null) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(T("Please sign in to manage security."))
            Button(onClick = onBack) { Text(T("Back")) }
        }
        return
    }

    var biometricEnabled by remember(uid) {
        mutableStateOf(activity?.isBiometricEnabled(uid) ?: false)
    }
    var biometricBusy by remember(uid) { mutableStateOf(false) }
    var currentPassword by remember(uid) { mutableStateOf("") }
    var newPassword by remember(uid) { mutableStateOf("") }
    var confirmPassword by remember(uid) { mutableStateOf("") }
    var passwordBusy by remember(uid) { mutableStateOf(false) }
    var message by remember(uid) { mutableStateOf("") }
    val supportsPassword = user.providerData.any {
        it.providerId == EmailAuthProvider.PROVIDER_ID
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(T("Security")) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !passwordBusy && !biometricBusy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, T("Back"))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(T("Biometric Authentication"))
            Text(T("Use your phone's registered biometric to unlock Winx when returning to the app."))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = T("Enable biometric protection"),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = biometricEnabled,
                    enabled = activity != null && !biometricBusy && !passwordBusy,
                    onCheckedChange = { enable ->
                        biometricBusy = true
                        message = ""
                        activity?.requestBiometricChange(enable) { success, resultMessage ->
                            biometricBusy = false
                            if (auth.currentUser?.uid == uid) {
                                if (success) {
                                    biometricEnabled = activity?.isBiometricEnabled(uid) ?: false
                                }
                                message = resultMessage
                            }
                        }
                    }
                )
            }
            Text(
                T(if (biometricEnabled) "Biometric authentication is enabled."
                else "Biometric authentication is disabled.")
            )
            Text(T("If biometrics are unavailable, sign out from the lock screen and sign in to your account again."))
            if (activity == null) Text(T("Unable to access biometric authentication."))

            Text(T("Change Password"))
            if (!supportsPassword) {
                Text(T("This account uses Google sign-in. Manage its password through Google."))
            } else {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it; message = "" },
                    label = { Text(T("Current Password")) },
                    singleLine = true,
                    enabled = !passwordBusy,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; message = "" },
                    label = { Text(T("New Password")) },
                    singleLine = true,
                    enabled = !passwordBusy,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; message = "" },
                    label = { Text(T("Confirm New Password")) },
                    singleLine = true,
                    enabled = !passwordBusy,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = !passwordBusy && !biometricBusy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val signedInUser = auth.currentUser
                        val email = signedInUser?.email
                        when {
                            signedInUser == null || signedInUser.uid != uid || email == null ->
                                message = "Please sign in again."
                            currentPassword.isBlank() -> message = "Enter your current password."
                            newPassword.length < 6 ->
                                message = "New password must contain at least 6 characters."
                            newPassword != confirmPassword -> message = "Passwords do not match."
                            else -> {
                                passwordBusy = true
                                val passwordToSave = newPassword
                                val credential = EmailAuthProvider.getCredential(email, currentPassword)
                                signedInUser.reauthenticate(credential)
                                    .addOnCompleteListener { reauth ->
                                        if (auth.currentUser?.uid != uid) {
                                            passwordBusy = false
                                        } else if (!reauth.isSuccessful) {
                                            passwordBusy = false
                                            message = "Unable to verify your current password. Please try again."
                                        } else {
                                            signedInUser.updatePassword(passwordToSave)
                                                .addOnCompleteListener { update ->
                                                    passwordBusy = false
                                                    if (auth.currentUser?.uid == uid) {
                                                        if (update.isSuccessful) {
                                                            currentPassword = ""
                                                            newPassword = ""
                                                            confirmPassword = ""
                                                            message = "Password updated successfully."
                                                        } else {
                                                            message = "Unable to update password. Check your password policy and connection."
                                                        }
                                                    }
                                                }
                                        }
                                    }
                            }
                        }
                    }
                ) { Text(T(if (passwordBusy) "Saving..." else "Update Password")) }
            }
            if (message.isNotBlank()) Text(T(message))
        }
    }
}
