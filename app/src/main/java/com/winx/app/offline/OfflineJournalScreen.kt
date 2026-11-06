package com.winx.app.offline

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.winx.app.data.remote.TravelEntryDto
import com.winx.app.ui.theme.WinxDarkBlue
import com.winx.app.ui.theme.WinxWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineJournalScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val auth = remember { FirebaseAuth.getInstance() }
    var uid by remember { mutableStateOf(auth.currentUser?.uid) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { uid = it.currentUser?.uid }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    val accountUid = uid
    val store = OfflineRuntime.store
    val revision by store.changes.collectAsState()
    var snapshot by remember(accountUid) { mutableStateOf(OfflineSnapshot()) }
    val scope = rememberCoroutineScope()
    var title by rememberSaveable(accountUid) { mutableStateOf("") }
    var location by rememberSaveable(accountUid) { mutableStateOf("") }
    var country by rememberSaveable(accountUid) { mutableStateOf("") }
    var date by rememberSaveable(accountUid) { mutableStateOf(LocalDate.now().toString()) }
    var rating by rememberSaveable(accountUid) { mutableStateOf("3") }
    var notes by rememberSaveable(accountUid) { mutableStateOf("") }
    var operationId by rememberSaveable(accountUid) { mutableStateOf(UUID.randomUUID().toString()) }
    var saving by remember(accountUid) { mutableStateOf(false) }
    var message by remember(accountUid) { mutableStateOf<String?>(null) }

    LaunchedEffect(accountUid, revision) {
        if (accountUid != null) {
            val loaded = withContext(Dispatchers.IO) { store.snapshot(accountUid) }
            if (auth.currentUser?.uid == accountUid) snapshot = loaded
        }
    }
    LaunchedEffect(accountUid) {
        if (accountUid != null) OfflineRuntime.schedule(context, accountUid)
    }

    Scaffold(
        containerColor = WinxWhite,
        topBar = {
            TopAppBar(
                title = { Text("Offline Journal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WinxDarkBlue, titleContentColor = WinxWhite,
                    navigationIconContentColor = WinxWhite
                )
            )
        }
    ) { padding ->
        if (accountUid == null) {
            Text("Sign in to view this account's offline journal.", color = WinxDarkBlue,
                modifier = Modifier.padding(padding).padding(20.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Text entries save on this phone first and sync automatically when a connection and your account are available.", color = WinxDarkBlue)
                    Text("Offline entries are read-only after saving. Photos and videos use the existing online entry form.", color = Color.DarkGray)
                    if (snapshot.lastRefresh > 0) Text(
                        "Last cloud refresh: " + DateFormat.getDateTimeInstance().format(Date(snapshot.lastRefresh)),
                        color = Color.DarkGray
                    )
                    if (snapshot.problem.isNotBlank()) Text(snapshot.problem, color = Color(0xFF9C3E00))
                }
                item {
                    OutlinedButton(onClick = {
                        if (auth.currentUser?.uid == accountUid) scope.launch {
                            withContext(Dispatchers.IO) { store.retryFailures(accountUid) }
                            OfflineRuntime.schedule(context, accountUid)
                            message = "Sync requested. Pending entries stay safe while waiting for connectivity."
                        }
                    }) { Text("Refresh / retry sync", color = WinxDarkBlue) }
                }
                item { Text("Create a text memory", color = WinxDarkBlue, fontWeight = FontWeight.Bold) }
                item { OfflineField(title, { title = it }, "Entry title", saving) }
                item { OfflineField(location, { location = it }, "Location", saving) }
                item { OfflineField(country, { country = it }, "Country", saving) }
                item { OfflineField(date, { date = it }, "Date (YYYY-MM-DD)", saving) }
                item { OfflineField(rating, { rating = it }, "Rating (1–5)", saving) }
                item { OfflineField(notes, { notes = it }, "Notes", saving, multiline = true) }
                item {
                    Button(
                        enabled = !saving,
                        onClick = {
                            val score = rating.toIntOrNull()
                            val validDate = runCatching { LocalDate.parse(date) }.getOrNull()
                            if (title.isBlank() || score == null || score !in 1..5 || validDate == null || validDate.isAfter(LocalDate.now())) {
                                message = "Enter a title, a rating from 1 to 5 and a valid date that is not in the future."
                            } else if (auth.currentUser?.uid == accountUid) {
                                saving = true
                                message = null
                                val queued = TravelEntryDto(
                                    title = title.trim(), location = location.trim(), country = country.trim(),
                                    date = "${validDate}T00:00:00", rating = score, notes = notes,
                                    clientOperationId = operationId
                                )
                                val key = operationId
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { store.enqueue(accountUid, key, queued) }
                                        if (auth.currentUser?.uid == accountUid) {
                                            title = ""; location = ""; country = ""; notes = ""
                                            operationId = UUID.randomUUID().toString()
                                            message = "Saved on this phone. Automatic sync is queued."
                                            OfflineRuntime.schedule(context, accountUid)
                                        }
                                    } catch (exception: kotlinx.coroutines.CancellationException) {
                                        throw exception
                                    } catch (_: Exception) {
                                        message = "Unable to save locally. Keep this form and try again."
                                    } finally { saving = false }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WinxDarkBlue, contentColor = WinxWhite),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (saving) "Saving…" else "Save on this phone") }
                    message?.let { Text(it, color = WinxDarkBlue) }
                }
                item { Text("Pending entries (${snapshot.pending.size})", color = WinxDarkBlue, fontWeight = FontWeight.Bold) }
                if (snapshot.pending.isEmpty()) item { Text("No pending entries.", color = Color.DarkGray) }
                items(snapshot.pending, key = { "pending-${it.operationId}" }) { pending ->
                    OfflineMemoryCard(pending.entry,
                        when (pending.state) {
                            "FAILED" -> "Failed — saved locally; use retry after fixing the problem"
                            "AUTH_REQUIRED" -> "Waiting for sign-in — saved locally"
                            else -> "Pending — waiting for automatic sync"
                        }, pending.error)
                }
                item { Text("Synced / cached entries (${snapshot.cached.size})", color = WinxDarkBlue, fontWeight = FontWeight.Bold) }
                if (snapshot.cached.isEmpty()) item {
                    Text("No cached entries yet. Open My Entries online or request a refresh first.", color = Color.DarkGray)
                }
                items(snapshot.cached, key = { "cloud-${it.id}" }) { entry ->
                    OfflineMemoryCard(entry, "Synced — cached copy, read-only offline", "")
                }
            }
        }
    }
}

@Composable
private fun OfflineField(value: String, onChange: (String) -> Unit, label: String, saving: Boolean, multiline: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, enabled = !saving,
        label = { Text(label) }, singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = WinxDarkBlue, unfocusedTextColor = WinxDarkBlue,
            disabledTextColor = WinxDarkBlue, focusedLabelColor = WinxDarkBlue,
            unfocusedLabelColor = WinxDarkBlue, focusedContainerColor = WinxWhite,
            unfocusedContainerColor = WinxWhite, cursorColor = WinxDarkBlue
        )
    )
}

@Composable
private fun OfflineMemoryCard(entry: TravelEntryDto, status: String, error: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE9F1))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(entry.title, color = WinxDarkBlue, fontWeight = FontWeight.Bold)
            Text(status, color = WinxDarkBlue)
            if (error.isNotBlank()) Text(error, color = Color(0xFF9C3E00))
            Text(listOf(entry.location, entry.country).filter { it.isNotBlank() }.joinToString(", "), color = Color.DarkGray)
            Text("${entry.date.take(10)} · Rating ${entry.rating}/5", color = Color.DarkGray)
            Text(entry.notes, color = WinxDarkBlue)
        }
    }
}
