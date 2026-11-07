package com.winx.app.screens

import android.app.NotificationManager
import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.winx.app.notifications.NotificationRuntime
import com.winx.app.notifications.NotificationInbox
import com.winx.app.notifications.WinxNotifications
import com.winx.app.notifications.notificationText
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { NotificationRuntime.schedule(context) }
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    val uid = user?.uid
    val languagePreferences = remember(context) {
        context.getSharedPreferences("winx_settings", Context.MODE_PRIVATE)
    }
    val preferences = remember(context, uid) {
        uid?.let { NotificationInbox.preferences(context, it) }
    }
    var revision by remember(uid) { mutableIntStateOf(0) }
    var clearDialog by remember(uid) { mutableStateOf(false) }
    DisposableEffect(preferences, languagePreferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        preferences?.registerOnSharedPreferenceChangeListener(listener)
        languagePreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            preferences?.unregisterOnSharedPreferenceChangeListener(listener)
            languagePreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    // Refresh permission state after returning from Android Settings.
    val lifecycleOwner = remember(context) {
        var candidate = context
        while (candidate is ContextWrapper && candidate !is LifecycleOwner) {
            candidate = candidate.baseContext
        }
        candidate as? LifecycleOwner
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    val language = languagePreferences.getString("language", "English (US)") ?: "English (US)"
    fun label(text: String) = notificationText(text, language)
    // revision makes preference and permission changes observable to this screen.
    val records = remember(uid, revision) {
        uid?.let { NotificationInbox.read(context, it) } ?: emptyList()
    }
    val preferenceEnabled = remember(uid, revision) {
        uid?.let { NotificationInbox.enabled(context, it) } ?: false
    }
    val allowed = remember(revision) {
        val runtimeGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val channelAllowed = if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(WinxNotifications.CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
        } else true
        runtimeGranted && channelAllowed && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (uid != null && auth.currentUser?.uid == uid) {
            NotificationInbox.setEnabled(context, uid, granted)
        }
        revision++
    }
    val ink = Color(0xFF053D5B)
    val locale = when (language) {
        "isiZulu (Zulu)" -> Locale.forLanguageTag("zu-ZA")
        "Afrikaans" -> Locale.forLanguageTag("af-ZA")
        else -> Locale.forLanguageTag("en-ZA")
    }
    Scaffold(
        containerColor = Color(0xFFF4B8D0),
        topBar = {
            TopAppBar(
                title = { Text(label("Notifications")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, label("Back"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ink, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uid == null) {
                item { Text(label("Please sign in to view notifications."), color = ink) }
            } else {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = ink)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label("Stay Updated"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Switch(
                                    checked = preferenceEnabled && allowed,
                                    onCheckedChange = { enable ->
                                        if (auth.currentUser?.uid == uid) {
                                            if (enable && Build.VERSION.SDK_INT >= 33 &&
                                                ContextCompat.checkSelfPermission(context,
                                                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                NotificationInbox.setEnabled(context, uid, enable)
                                                revision++
                                            }
                                        }
                                    }
                                )
                            }
                            Text(label(if (!allowed) "Android notifications are blocked. Allow them to receive alerts."
                            else if (preferenceEnabled) "Alerts are on."
                            else "Alerts are off. Received messages remain in your history."))
                            TextButton(onClick = {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                context.startActivity(intent)
                            }) { Text(label("Open Android notification settings")) }
                        }
                    }
                }
                item { Text(label("Recent Notifications"), color = ink, fontWeight = FontWeight.Bold) }
                if (records.isEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = ink)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(label("No notifications yet."))
                                Text(label("New messages will appear here when received."))
                            }
                        }
                    }
                } else {
                    item {
                        Column {
                            TextButton(onClick = {
                                if (auth.currentUser?.uid == uid) NotificationInbox.markAllRead(context, uid)
                            }, enabled = records.any { !it.read }) { Text(label("Mark all as read")) }
                            TextButton(onClick = { clearDialog = true }) { Text(label("Clear history")) }
                        }
                    }
                    items(records, key = { it.id }) { record ->
                        Card(
                            onClick = {
                                if (auth.currentUser?.uid == uid) NotificationInbox.markRead(context, uid, record.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = ink)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (!record.read) Text(label("Unread"), fontWeight = FontWeight.Bold)
                                // Preserve server-authored content; do not translate arbitrary user text.
                                Text(record.title, fontWeight = FontWeight.Bold)
                                Text(record.body)
                                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
                                    .format(Date(record.receivedAt)), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
    if (clearDialog && uid != null) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text(label("Clear notification history?")) },
            text = { Text(label("This clears notification history for this account on all devices after they refresh.")) },
            confirmButton = {
                Button(onClick = {
                    if (auth.currentUser?.uid == uid) NotificationInbox.clear(context, uid)
                    clearDialog = false
                }) { Text(label("Clear history")) }
            },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text(label("Cancel")) } }
        )
    }
}
