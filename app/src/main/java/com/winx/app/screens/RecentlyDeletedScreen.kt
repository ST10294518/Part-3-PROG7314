package com.winx.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.winx.app.data.remote.TravelEntryRepository
import com.winx.app.ui.theme.WinxDarkBlue
import com.winx.app.ui.theme.WinxWhite
import com.winx.app.utils.T
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedScreen(onBack: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    var uid by remember { mutableStateOf(auth.currentUser?.uid) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { uid = it.currentUser?.uid }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    val accountUid = uid
    val repository = remember { TravelEntryRepository() }
    val scope = rememberCoroutineScope()
    var entries by remember(accountUid) { mutableStateOf(emptyList<TravelEntry>()) }
    var loading by remember(accountUid) { mutableStateOf(false) }
    var busy by remember(accountUid) { mutableStateOf(false) }
    var error by remember(accountUid) { mutableStateOf<String?>(null) }
    var pendingDelete by remember(accountUid) { mutableStateOf<TravelEntry?>(null) }
    var confirmEmpty by remember(accountUid) { mutableStateOf(false) }

    suspend fun reload() {
        if (accountUid == null || auth.currentUser?.uid != accountUid) return
        loading = true
        error = null
        try {
            val result = repository.getDeletedEntries()
            if (auth.currentUser?.uid == accountUid) {
                result.onSuccess { entries = it }
                    .onFailure { error = it.message ?: "Unable to load deleted entries." }
            }
        } finally {
            if (auth.currentUser?.uid == accountUid) loading = false
        }
    }

    fun perform(action: suspend () -> Result<Unit>) {
        if (busy || loading || accountUid == null || auth.currentUser?.uid != accountUid) return
        busy = true
        error = null
        scope.launch {
            try {
                val result = action()
                if (auth.currentUser?.uid == accountUid) {
                    if (result.isSuccess) reload()
                    else error = result.exceptionOrNull()?.message ?: "Unable to update deleted entries."
                }
            } finally {
                if (auth.currentUser?.uid == accountUid) busy = false
            }
        }
    }

    LaunchedEffect(accountUid) { reload() }

    Scaffold(
        containerColor = WinxWhite,
        topBar = {
            TopAppBar(
                title = { Text(T("Recently Deleted")) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WinxDarkBlue,
                    titleContentColor = WinxWhite,
                    navigationIconContentColor = WinxWhite
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = T("Back"))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(T("Recently deleted items are kept here until permanently removed."), color = WinxDarkBlue)
            }
            if (accountUid == null) {
                item { Text(T("Please sign in to view your entries."), color = WinxDarkBlue) }
            } else {
                if (loading || busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                error?.let { message ->
                    item { Text(message, color = Color(0xFFB00020)) }
                }
                item {
                    OutlinedButton(
                        onClick = { scope.launch { reload() } },
                        enabled = !loading && !busy
                    ) { Text(T("Retry"), color = WinxDarkBlue) }
                }
                if (!loading && error == null && entries.isEmpty()) {
                    item { Text(T("Your Recently Deleted folder is empty."), color = WinxDarkBlue) }
                }
                items(entries, key = { it.id }) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE9F1))) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(entry.title.ifBlank { T("Untitled Entry") }, color = WinxDarkBlue)
                            Text(listOf(entry.location, entry.country).filter { it.isNotBlank() }
                                .joinToString(", "), color = Color.DarkGray)
                            Text(entry.date.take(10), color = Color.DarkGray)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    enabled = !busy && !loading,
                                    onClick = { perform { repository.restoreEntry(entry.id) } },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = WinxDarkBlue, contentColor = WinxWhite
                                    )
                                ) { Text(T("Restore")) }
                                TextButton(
                                    enabled = !busy && !loading,
                                    onClick = { pendingDelete = entry }
                                ) { Text(T("Delete"), color = Color(0xFFB00020)) }
                            }
                        }
                    }
                }
                if (entries.isNotEmpty()) item {
                    OutlinedButton(
                        enabled = !busy && !loading,
                        onClick = { confirmEmpty = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(T("Empty Deleted Items"), color = WinxDarkBlue) }
                }
            }
        }
    }

    if (confirmEmpty || pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false; pendingDelete = null },
            title = { Text(T(if (confirmEmpty) "Empty Recently Deleted?" else "Delete Entry?")) },
            text = {
                Text(T(if (confirmEmpty)
                    "This will permanently remove all items from the Recently Deleted list."
                else "This will permanently delete this entry. This cannot be undone."))
            },
            confirmButton = {
                TextButton(onClick = {
                    val entry = pendingDelete
                    val emptyAll = confirmEmpty
                    pendingDelete = null
                    confirmEmpty = false
                    if (emptyAll) perform { repository.emptyDeletedEntries() }
                    else if (entry != null) perform { repository.permanentlyDeleteEntry(entry.id) }
                }) { Text(T("Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false; pendingDelete = null }) { Text(T("Cancel")) }
            }
        )
    }
}
