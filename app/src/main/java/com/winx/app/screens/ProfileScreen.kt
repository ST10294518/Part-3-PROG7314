package com.winx.app.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.winx.app.utils.T
import com.winx.app.utils.winxFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }

    // Observe sign-in changes so another account cannot reuse this profile.
    var user by remember(auth) {
        mutableStateOf(auth.currentUser)
    }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            user = firebaseAuth.currentUser
        }

        auth.addAuthStateListener(listener)

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val userId = user?.uid

    if (userId == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(T("Please sign in to view your profile."))

            Button(onClick = onBack) {
                Text(T("Back"))
            }
        }

        return
    }

    // Each account has a separate local profile preferences file.
    // Do not migrate the old shared values: their owner is unknown.
    val preferences = remember(context, userId) {
        context.getSharedPreferences(
            "winx_profile_$userId",
            Context.MODE_PRIVATE
        )
    }

    var name by remember(userId) {
        mutableStateOf(
            preferences.getString(
                "name",
                user?.displayName ?: ""
            ) ?: ""
        )
    }

    var phone by remember(userId) {
        mutableStateOf(
            preferences.getString("phone", "") ?: ""
        )
    }

    var dateOfBirth by remember(userId) {
        mutableStateOf(
            preferences.getString("date_of_birth", "") ?: ""
        )
    }

    var country by remember(userId) {
        mutableStateOf(
            preferences.getString("country", "") ?: ""
        )
    }

    var editing by remember(userId) {
        mutableStateOf(false)
    }

    var saved by remember(userId) {
        mutableStateOf(false)
    }

    var saving by remember(userId) {
        mutableStateOf(false)
    }

    var profileError by remember(userId) {
        mutableStateOf("")
    }

    val email = user?.email ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(T("Profile"))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = T("Back")
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = !saving,
                        onClick = {
                            editing = !editing
                            saved = false
                            profileError = ""
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = T("Edit Profile")
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = T("Profile"),
                modifier = Modifier
                    .size(90.dp)
                    .align(Alignment.CenterHorizontally),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = T("View and manage your personal information."),
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    saved = false
                    profileError = ""
                },
                label = {
                    Text(T("Full Name"))
                },
                enabled = editing && !saving,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = winxFieldColors()
            )

            OutlinedTextField(
                value = email,
                onValueChange = {},
                label = {
                    Text(T("Email Address"))
                },
                enabled = false,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = winxFieldColors()
            )

            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it
                    saved = false
                    profileError = ""
                },
                label = {
                    Text(T("Phone Number"))
                },
                enabled = editing && !saving,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = winxFieldColors()
            )

            OutlinedTextField(
                value = dateOfBirth,
                onValueChange = {
                    dateOfBirth = it
                    saved = false
                    profileError = ""
                },
                label = {
                    Text(T("Date of Birth"))
                },
                enabled = editing && !saving,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = winxFieldColors()
            )

            OutlinedTextField(
                value = country,
                onValueChange = {
                    country = it
                    saved = false
                    profileError = ""
                },
                label = {
                    Text(T("Country"))
                },
                enabled = editing && !saving,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = winxFieldColors()
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            if (editing) {
                Button(
                    enabled = !saving,
                    onClick = {
                        saved = false
                        profileError = ""

                        val savedName = name.trim()
                        val savedPhone = phone.trim()
                            .replace(Regex("[\\s()\\-]"), "")
                        val savedDateOfBirth = dateOfBirth.trim()
                        val savedCountry = country.trim()

                        // Accept 7–15 digits with an optional leading +.
                        val validPhone =
                            Regex("^\\+?[0-9]{7,15}$")
                                .matches(savedPhone)

                        val currentUser = auth.currentUser

                        when {
                            currentUser == null ||
                                    currentUser.uid != userId -> {
                                profileError =
                                    "Your account changed. Reopen your profile."
                            }

                            savedName.isBlank() -> {
                                profileError =
                                    "Please enter your full name."
                            }

                            !validPhone -> {
                                profileError =
                                    "Enter a valid phone number (7–15 digits)."
                            }

                            savedCountry.isBlank() -> {
                                profileError =
                                    "Please enter your country."
                            }

                            else -> {
                                saving = true

                                val profileUpdates =
                                    UserProfileChangeRequest.Builder()
                                        .setDisplayName(savedName)
                                        .build()

                                currentUser.updateProfile(profileUpdates)
                                    .addOnCompleteListener { task ->
                                        saving = false

                                        // Ignore results from a previous account.
                                        if (auth.currentUser?.uid != userId) {
                                            return@addOnCompleteListener
                                        }

                                        if (task.isSuccessful) {
                                            preferences.edit()
                                                .putString(
                                                    "name",
                                                    savedName
                                                )
                                                .putString(
                                                    "phone",
                                                    savedPhone
                                                )
                                                .putString(
                                                    "date_of_birth",
                                                    savedDateOfBirth
                                                )
                                                .putString(
                                                    "country",
                                                    savedCountry
                                                )
                                                .apply()

                                            name = savedName
                                            phone = savedPhone
                                            dateOfBirth = savedDateOfBirth
                                            country = savedCountry

                                            editing = false
                                            saved = true
                                        } else {
                                            profileError =
                                                "Unable to save your profile. Please try again."
                                        }
                                    }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        T(
                            if (saving) {
                                "Saving..."
                            } else {
                                "Save Profile"
                            }
                        )
                    )
                }
            }

            if (profileError.isNotBlank()) {
                Text(
                    text = T(profileError),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (saved) {
                Text(
                    text = T("Profile saved successfully."),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
