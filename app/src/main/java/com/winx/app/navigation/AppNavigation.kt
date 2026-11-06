package com.winx.app.navigation

import com.winx.app.utils.T
import android.util.Log

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.google.firebase.auth.FirebaseAuth
import com.winx.app.auth.GoogleAuthManager
import com.winx.app.data.remote.TravelEntryRepository

import com.winx.app.screens.AboutUsScreen
import com.winx.app.screens.CalendarScreen
import com.winx.app.screens.CountriesScreen
import com.winx.app.screens.DashboardScreen
import com.winx.app.screens.EditEntryScreen
import com.winx.app.screens.EntriesScreen
import com.winx.app.screens.EntryDetailsScreen
import com.winx.app.screens.EntryScreen
import com.winx.app.screens.LanguageScreen
import com.winx.app.screens.LibraryScreen
import com.winx.app.screens.LoginScreen
import com.winx.app.screens.NotificationsScreen
import com.winx.app.screens.ProfileScreen
import com.winx.app.screens.RecentlyDeletedScreen
import com.winx.app.screens.RegisterScreen
import com.winx.app.screens.SecurityScreen
import com.winx.app.screens.SettingsScreen
import com.winx.app.screens.TravelEntry
import com.winx.app.screens.WelcomeScreen

import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

@Composable
fun AppNavigation() {
    val auth = remember { FirebaseAuth.getInstance() }
    var userUid by remember { mutableStateOf(auth.currentUser?.uid) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { currentAuth ->
            userUid = currentAuth.currentUser?.uid
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    // Discard the old navigation stack, screen state and coroutine scope when the account changes.
    key(userUid) {
        AppSessionNavigation(
            signedIn = userUid != null,
            onSignedIn = { userUid = auth.currentUser?.uid },
            onSignOut = {
                auth.signOut()
                userUid = null
            }
        )
    }
}

@Composable
private fun AppSessionNavigation(
    signedIn: Boolean,
    onSignedIn: () -> Unit,
    onSignOut: () -> Unit
) {

    val navController = rememberNavController()

    // =============================================================
    // CONTEXT / COROUTINES
    // =============================================================

    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()

    // =============================================================
    // GOOGLE AUTHENTICATION
    // =============================================================

    val googleAuthManager = remember(context) {
        GoogleAuthManager(context)
    }

    var googleSignInLoading by remember {
        mutableStateOf(false)
    }

    var googleSignInError by remember {
        mutableStateOf<String?>(null)
    }

    // Forces the current Compose destination to refresh after a language change.
    // Each translated composable observes the saved language directly.

    // =============================================================
    // REST API REPOSITORY
    // =============================================================

    val repository = remember {
        TravelEntryRepository()
    }

    // =============================================================
    // SAVED ENTRIES
    // =============================================================

    var entries by remember {
        mutableStateOf<List<TravelEntry>>(emptyList())
    }

    // =============================================================
    // CURRENT ENTRY
    // =============================================================

    var selectedEntry by remember {
        mutableStateOf<TravelEntry?>(null)
    }

    // =============================================================
    // API ERROR
    // =============================================================

    var apiError by remember {
        mutableStateOf<String?>(null)
    }

    var entriesLoading by remember {
        mutableStateOf(false)
    }

    // =============================================================
    // DELETE CONFIRMATION
    // =============================================================

    var entryToDelete by remember {
        mutableStateOf<TravelEntry?>(null)
    }

    // =============================================================
    // LOAD ENTRIES FROM REST API
    // =============================================================

    fun loadEntries() {

        coroutineScope.launch {

            entriesLoading = true

            Log.d(
                "AppNavigation",
                "Loading entries from REST API..."
            )

            repository
                .getEntries()
                .onSuccess { loadedEntries ->

                    entries = loadedEntries

                    apiError = null

                    Log.d(
                        "AppNavigation",
                        "Entries loaded successfully. Count=${loadedEntries.size}"
                    )
                }
                .onFailure { exception ->

                    apiError =
                        exception.message
                            ?: "Unable to load entries from the REST API."

                    Log.e(
                        "AppNavigation",
                        "Failed to load entries.",
                        exception
                    )
                }

            entriesLoading = false
        }
    }

    // =============================================================
    // NAVIGATION
    // =============================================================



    NavHost(
        navController = navController,
        startDestination = if (signedIn) "dashboard" else "welcome"
    ) {

        // =========================================================
        // WELCOME
        // =========================================================

        composable("welcome") {

            WelcomeScreen(
                onBeginClick = {
                    navController.navigate("login")
                }
            )
        }

        // =========================================================
        // LOGIN
        // =========================================================

        composable("login") {

            LoginScreen(

                onLoginClick = {

                    onSignedIn()
                },

                onCreateAccountClick = {
                    navController.navigate("register")
                },

                onGoogleSignInClick = {

                    if (!googleSignInLoading) {

                        googleSignInLoading = true
                        googleSignInError = null

                        coroutineScope.launch {

                            val result =
                                googleAuthManager.signInWithGoogle()

                            result
                                .onSuccess {

                                    googleSignInLoading = false

                                    onSignedIn()
                                }
                                .onFailure { exception ->

                                    googleSignInLoading = false

                                    googleSignInError =
                                        exception.message
                                            ?: "Google sign-in failed."
                                }
                        }
                    }
                },

                googleSignInLoading = googleSignInLoading,

                googleSignInError = googleSignInError
            )
        }

        // =========================================================
        // REGISTER
        // =========================================================

        composable("register") {

            RegisterScreen(

                onCreateAccountClick = {

                    onSignedIn()
                }
            )
        }

        // =========================================================
        // DASHBOARD
        // =========================================================

        composable("dashboard") {

            LaunchedEffect(Unit) {
                loadEntries()
            }

            DashboardScreen(
                entries = entries,
                isLoadingEntries = entriesLoading,
                entriesError = apiError,
                onRetryEntries = {
                    loadEntries()
                },
                onEntryDetailsClick = { entry ->
                    selectedEntry = entry
                    navController.navigate("entryDetails")
                },

                onAddEntryClick = {
                    navController.navigate("entry")
                },

                onAddCountryClick = {
                    navController.navigate("countries")
                },

                onEntryClick = {

                    loadEntries()

                    navController.navigate("entries")
                },

                onCalendarClick = {

                    navController.navigate("calendar") {
                        launchSingleTop = true
                    }
                },

                onLibraryClick = {

                    navController.navigate("library") {
                        launchSingleTop = true
                    }
                },

                onSettingsClick = {
                    navController.navigate("settings")
                },

                onNotificationsClick = {
                    navController.navigate("notifications")
                }
            )
        }

        // =========================================================
        // SETTINGS
        // =========================================================

        composable("settings") {

            SettingsScreen(

                onBack = {
                    navController.popBackStack()
                },

                onProfileClick = {
                    navController.navigate("profile")
                },

                onLanguageClick = {
                    navController.navigate("language")
                },

                onRecentlyDeletedClick = {
                    navController.navigate("recentlyDeleted")
                },

                onSecurityClick = {
                    navController.navigate("security")
                },

                onNotificationsClick = {
                    navController.navigate("notifications")
                },

                onAboutUsClick = {
                    navController.navigate("aboutUs")
                },

                onLogout = onSignOut
            )
        }

        // =========================================================
        // PROFILE
        // =========================================================

        composable("profile") {

            ProfileScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // =========================================================
        // LANGUAGE
        // =========================================================

        composable("language") {

            LanguageScreen(
                onBack = {
                    navController.popBackStack()
                },
                onLanguageChanged = {
                    // No navigation recreation: keep current drafts and screen state.
                }
            )
        }

        // =========================================================
        // RECENTLY DELETED
        // =========================================================

        composable("recentlyDeleted") {

            RecentlyDeletedScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // =========================================================
        // SECURITY
        // =========================================================

        composable("security") {

            SecurityScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // =========================================================
        // NOTIFICATIONS
        // =========================================================

        composable("notifications") {

            NotificationsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // =========================================================
        // ABOUT US
        // =========================================================

        composable("aboutUs") {

            AboutUsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // =========================================================
        // MY ENTRIES
        // =========================================================

        composable("entries") {

            LaunchedEffect(Unit) {
                loadEntries()
            }

            EntriesScreen(

                entries = entries,

                onBack = {
                    navController.popBackStack()
                },

                onAddEntryClick = {
                    navController.navigate("entry")
                },

                onEntryClick = { entry ->

                    selectedEntry = entry

                    navController.navigate("entryDetails")
                }
            )
        }

        // =========================================================
        // ENTRY DETAILS
        // =========================================================

        composable("entryDetails") {

            selectedEntry?.let { entry ->

                EntryDetailsScreen(

                    entry = entry,

                    onBackClick = {
                        navController.popBackStack()
                    },

                    // -------------------------------------------------
                    // EDIT
                    // -------------------------------------------------

                    onEditClick = {

                        navController.navigate("editEntry")
                    },

                    // -------------------------------------------------
                    // DELETE
                    // -------------------------------------------------

                    onDeleteClick = {

                        entryToDelete = entry
                    }
                )
            }
        }

        // =========================================================
        // EDIT ENTRY
        // =========================================================

        composable("editEntry") {
            // Destination scope cancels a pending save when this form is removed.
            val formScope = rememberCoroutineScope()
            var isSaving by remember { mutableStateOf(false) }
            var saveError by rememberSaveable { mutableStateOf<String?>(null) }

            selectedEntry?.let { entry ->
                EditEntryScreen(
                    entry = entry,
                    isSaving = isSaving,
                    saveError = saveError,
                    onBackClick = {
                        if (!isSaving) navController.popBackStack()
                    },
                    onSaveClick = { updatedEntry ->
                        if (!isSaving) {
                            isSaving = true
                            saveError = null
                            formScope.launch {
                                try {
                                    val result = repository.updateEntry(updatedEntry)
                                    ensureActive()
                                    result.onSuccess {
                                        entries = entries.map {
                                            if (it.id == updatedEntry.id) updatedEntry else it
                                        }
                                        selectedEntry = updatedEntry
                                        apiError = null
                                        navController.popBackStack()
                                    }.onFailure { exception ->
                                        Log.e("AppNavigation", "Failed to update entry", exception)
                                        saveError = "Unable to update the entry. Check your connection and try again."
                                    }
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    }
                )
            }
        }

        // =========================================================
        // CREATE ENTRY
        // =========================================================

        composable("entry") { entryBackStack ->
            val formScope = rememberCoroutineScope()
            var isSaving by remember { mutableStateOf(false) }
            var saveError by rememberSaveable { mutableStateOf<String?>(null) }
            // Country belongs to this draft, rather than a global navigation variable.
            val draftCountry by entryBackStack.savedStateHandle
                .getStateFlow("draftCountry", "")
                .collectAsState()

            EntryScreen(
                selectedCountry = draftCountry,
                isSaving = isSaving,
                saveError = saveError,
                onCountriesClick = {
                    if (!isSaving) navController.navigate("countries")
                },
                onSaveClick = { newEntry ->
                    if (!isSaving) {
                        isSaving = true
                        saveError = null
                        formScope.launch {
                            try {
                                val result = repository.createEntry(newEntry)
                                ensureActive()
                                result.onSuccess { createdEntry ->
                                    entries = entries + createdEntry
                                    selectedEntry = createdEntry
                                    apiError = null
                                    navController.navigate("entries") {
                                        popUpTo("entry") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }.onFailure { exception ->
                                    Log.e("AppNavigation", "Failed to create entry", exception)
                                    saveError = "Unable to save the entry. Check your connection and try again."
                                }
                            } finally {
                                isSaving = false
                            }
                        }
                    }
                },
                onCancelClick = {
                    if (!isSaving) navController.popBackStack()
                }
            )
        }

        // =========================================================
        // COUNTRIES
        // =========================================================

        composable("countries") {

            CountriesScreen(

                onSaveClick = { country ->

                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("draftCountry", country)

                    navController.popBackStack()
                },

                onCancelClick = {
                    navController.popBackStack()
                }
            )
        }

        // =========================================================
        // CALENDAR
        // =========================================================

        composable("calendar") {

            LaunchedEffect(Unit) {
                loadEntries()
            }

            CalendarScreen(
                entries = entries,
                isLoading = entriesLoading,
                errorMessage = apiError,
                onRetry = {
                    loadEntries()
                },
                onEntryDetailsClick = { entry ->
                    selectedEntry = entry
                    navController.navigate("entryDetails")
                },

                onDashboardClick = {

                    navController.navigate("dashboard") {

                        popUpTo("dashboard") {
                            inclusive = false
                        }

                        launchSingleTop = true
                    }
                },

                onEntryClick = {

                    navController.navigate("entry") {
                        launchSingleTop = true
                    }
                },

                onLibraryClick = {

                    navController.navigate("library") {
                        launchSingleTop = true
                    }
                },

                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }

        // =========================================================
        // LIBRARY
        // =========================================================

        composable("library") {

            LibraryScreen(

                onDashboardClick = {

                    navController.navigate("dashboard") {

                        popUpTo("dashboard") {
                            inclusive = false
                        }

                        launchSingleTop = true
                    }
                },

                onEntriesClick = {
                    loadEntries()
                    navController.navigate("entries") {
                        launchSingleTop = true
                    }
                },

                onEntryDetailsClick = { entry ->
                    selectedEntry = entry
                    navController.navigate("entryDetails")
                },

                onCalendarClick = {

                    navController.navigate("calendar") {
                        launchSingleTop = true
                    }
                },

                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }
    }

    // =============================================================
    // DELETE CONFIRMATION DIALOG
    // =============================================================

    entryToDelete?.let { entry ->

        AlertDialog(

            onDismissRequest = {
                entryToDelete = null
            },

            title = {
                Text(
                    text = T("Delete Entry?")
                )
            },

            text = {
                Text(
                    text =
                        T("Are you sure you want to delete \"{0}\"?", entry.title)
                )
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        entryToDelete = null

                        coroutineScope.launch {

                            Log.d(
                                "AppNavigation",
                                "Deleting entry ID=${entry.id}"
                            )

                            repository
                                .deleteEntry(entry.id)
                                .onSuccess {

                                    Log.d(
                                        "AppNavigation",
                                        "Entry deleted successfully. ID=${entry.id}"
                                    )

                                    entries =
                                        entries.filter {
                                            it.id != entry.id
                                        }

                                    selectedEntry = null

                                    apiError = null

                                    navController.popBackStack()
                                }
                                .onFailure { exception ->

                                    Log.e(
                                        "AppNavigation",
                                        "Failed to delete entry ID=${entry.id}",
                                        exception
                                    )

                                    apiError =
                                        exception.message
                                            ?: "Unable to delete the entry."
                                }
                        }
                    }
                ) {

                    Text(
                        text = T("Delete")
                    )
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {
                        entryToDelete = null
                    }
                ) {

                    Text(
                        text = T("Cancel")
                    )
                }
            }
        )
    }

    // =============================================================
    // GENERAL API ERROR DIALOG
    // =============================================================

    apiError?.let { errorMessage ->

        AlertDialog(

            onDismissRequest = {
                apiError = null
            },

            title = {
                Text(
                    text = T("API Error")
                )
            },

            text = {
                Text(
                    text = errorMessage
                )
            },

            confirmButton = {

                TextButton(
                    onClick = {
                        apiError = null
                    }
                ) {

                    Text(
                        text = T("OK")
                    )
                }
            }
        )
    }
}