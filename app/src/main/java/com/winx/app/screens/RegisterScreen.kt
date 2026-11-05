package com.winx.app.screens

import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.winx.app.R
import com.winx.app.ui.theme.WinxBlue
import com.winx.app.ui.theme.WinxDarkBlue
import com.winx.app.utils.T

@Composable
fun RegisterScreen(
    onCreateAccountClick: () -> Unit = {}
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    val firebaseAuth = remember {
        FirebaseAuth.getInstance()
    }

    // Explicit colours keep the form readable on the dark background.
    val registerFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color.White.copy(alpha = 0.8f),

        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.White,
        disabledLabelColor = Color.White.copy(alpha = 0.8f),

        focusedPlaceholderColor = Color.White.copy(alpha = 0.7f),
        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.7f),

        cursorColor = Color.White,

        focusedBorderColor = Color.White,
        unfocusedBorderColor = Color.White.copy(alpha = 0.65f),
        disabledBorderColor = Color.White.copy(alpha = 0.4f),

        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WinxDarkBlue)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = 24.dp,
                vertical = 32.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // White backing keeps the complete Winx logo visible.
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(140.dp)
                .background(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(
                    id = R.drawable.winx_logo
                ),
                contentDescription = "Winx logo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = T(
                "Capture places. Cherish moments.\nRemember every journey."
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Text(
            text = T("Create your account"),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                errorMessage = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    text = T("Username"),
                    color = Color.White
                )
            },
            singleLine = true,
            enabled = !isLoading,
            colors = registerFieldColors,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    text = T("Email"),
                    color = Color.White
                )
            },
            singleLine = true,
            enabled = !isLoading,
            colors = registerFieldColors,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    text = T("Password"),
                    color = Color.White
                )
            },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            colors = registerFieldColors,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                errorMessage = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    text = T("Confirm Password"),
                    color = Color.White
                )
            },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            colors = registerFieldColors,
            shape = RoundedCornerShape(12.dp)
        )

        errorMessage?.let { message ->
            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                text = T(message),
                color = Color(0xFFFFB4AB),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = {
                val cleanUsername = username.trim()
                val cleanEmail = email.trim()

                errorMessage = null

                when {
                    cleanUsername.isBlank() -> {
                        errorMessage = "Please enter a username."
                    }

                    cleanEmail.isBlank() -> {
                        errorMessage = "Please enter an email address."
                    }

                    !Patterns.EMAIL_ADDRESS
                        .matcher(cleanEmail)
                        .matches() -> {
                        errorMessage = "Please enter a valid email address."
                    }

                    password.isBlank() -> {
                        errorMessage = "Please enter a password."
                    }

                    password.length < 6 -> {
                        errorMessage =
                            "Password must contain at least 6 characters."
                    }

                    confirmPassword.isBlank() -> {
                        errorMessage = "Please confirm your password."
                    }

                    password != confirmPassword -> {
                        errorMessage = "Passwords do not match."
                    }

                    else -> {
                        isLoading = true

                        firebaseAuth
                            .createUserWithEmailAndPassword(
                                cleanEmail,
                                password
                            )
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val firebaseUser = task.result?.user

                                    if (firebaseUser != null) {
                                        val profileUpdates =
                                            UserProfileChangeRequest
                                                .Builder()
                                                .setDisplayName(
                                                    cleanUsername
                                                )
                                                .build()

                                        firebaseUser
                                            .updateProfile(profileUpdates)
                                            .addOnCompleteListener {
                                                isLoading = false
                                                password = ""
                                                confirmPassword = ""

                                                // The account exists even if the
                                                // display-name update fails.
                                                // Its name can be edited later.
                                                onCreateAccountClick()
                                            }
                                    } else {
                                        isLoading = false
                                        errorMessage =
                                            "Account was created, but the user could not be loaded."
                                    }
                                } else {
                                    isLoading = false
                                    errorMessage =
                                        task.exception?.localizedMessage
                                            ?: "Account creation failed. Please try again."
                                }
                            }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WinxBlue,
                contentColor = Color.White,
                disabledContainerColor = WinxBlue.copy(
                    alpha = 0.6f
                ),
                disabledContentColor = Color.White
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Text(
                    text = T("Create Account"),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}