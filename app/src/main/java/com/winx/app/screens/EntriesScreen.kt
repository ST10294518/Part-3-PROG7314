package com.winx.app.screens

import com.winx.app.utils.T

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import com.google.firebase.auth.FirebaseAuth
import com.winx.app.utils.FavouriteEntries
import com.winx.app.utils.RandomMemoryRules
import com.winx.app.utils.FavouriteRules
import com.winx.app.utils.currentWinxLanguage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winx.app.ui.theme.WinxBlue
import com.winx.app.ui.theme.WinxDarkBlue
import com.winx.app.ui.theme.WinxLightPink
import com.winx.app.ui.theme.WinxOrange
import com.winx.app.ui.theme.WinxWhite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun EntriesScreen(
    entries: List<TravelEntry> = emptyList(),
    onBack: () -> Unit = {},
    onAddEntryClick: () -> Unit = {},
    onEntryClick: (TravelEntry) -> Unit = {},
    onOfflineClick: () -> Unit = {}
) {

    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var uid by remember { mutableStateOf(auth.currentUser?.uid) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { uid = it.currentUser?.uid }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    val accountUid = uid
    var lastRandomEntryId by rememberSaveable(accountUid) { mutableStateOf<Int?>(null) }
    var favourites by remember(accountUid) {
        mutableStateOf(accountUid?.let { FavouriteEntries.read(context, it) } ?: emptySet<Int>())
    }
    var onlyFavourites by rememberSaveable(accountUid) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var favouritesReady by remember(accountUid) { mutableStateOf(false) }
    var favouritesBusy by remember(accountUid) { mutableStateOf(false) }
    var favouritesError by remember(accountUid) { mutableStateOf(false) }
    var refreshAttempt by remember(accountUid) { mutableStateOf(0) }
    LaunchedEffect(accountUid, refreshAttempt) {
        if (accountUid == null) return@LaunchedEffect

        favouritesBusy = true
        favouritesError = false
        try {
            favourites = FavouriteEntries.refresh(context, accountUid)
            favouritesReady = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            favouritesError = true
        } finally {
            favouritesBusy = false
        }
    }
    val visibleIds = FavouriteRules.visibleIds(entries.map { it.id }, favourites).toSet()
    val displayedEntries = if (accountUid == null) emptyList() else if (onlyFavourites) {
        entries.filter { it.id in visibleIds }
    } else entries

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WinxWhite)
    ) {

        // ---------------------------------------------------------
        // HEADER
        // ---------------------------------------------------------

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WinxDarkBlue)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = T("Back"),
                    tint = WinxWhite
                )
            }
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = T("My Entries"),
                    color = WinxWhite,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = T("Your travel memories"),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
        }


        if (favouritesError) {
            TextButton(
                onClick = {
                    favouritesReady = false
                    favouritesBusy = true
                    refreshAttempt++
                },
                enabled = !favouritesBusy,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(favouriteLabel("Could not sync favourites. Tap to retry."))
            }
        }

        // ---------------------------------------------------------
        // ENTRIES
        // ---------------------------------------------------------

        if (accountUid != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    colors = favouriteChipColors(),
                    selected = !onlyFavourites,
                    onClick = { onlyFavourites = false },
                    label = { Text(favouriteLabel("All entries")) }
                )
                FilterChip(
                    colors = favouriteChipColors(),
                    selected = onlyFavourites,
                    onClick = { onlyFavourites = true },
                    label = { Text("${favouriteLabel("Favourites")} (${visibleIds.size})") }
                )
            }
        }

        if (accountUid != null) {
            Button(
                onClick = {
                    // Always use the current account and active entries, not the favourites filter.
                    if (auth.currentUser?.uid == accountUid) {
                        val pickedId = RandomMemoryRules.pickId(
                            entries.map { it.id }, lastRandomEntryId
                        )
                        val memory = entries.firstOrNull { it.id == pickedId }
                        if (memory != null) {
                            lastRandomEntryId = memory.id
                            onEntryClick(memory)
                        }
                    }
                },
                enabled = entries.any { it.id > 0 },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WinxDarkBlue,
                    contentColor = WinxWhite,
                    disabledContainerColor = Color(0xFFE0E0E0),
                    disabledContentColor = WinxDarkBlue
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(favouriteLabel("Surprise me"), fontWeight = FontWeight.Bold)
            }
            Text(
                text = favouriteLabel(
                    if (entries.any { it.id > 0 }) "Rediscover a random travel memory."
                    else "Add an entry to unlock random memories."
                ),
                color = WinxDarkBlue,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        if (accountUid != null) {
            Button(
                onClick = onOfflineClick,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WinxBlue, contentColor = WinxWhite)
            ) { Text("Offline Journal") }
        }

        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (displayedEntries.isEmpty()) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = T("No entries"),
                        tint = WinxBlue,
                        modifier = Modifier.size(30.dp)
                    )

                    Spacer(
                        modifier = Modifier.height(14.dp)
                    )

                    Text(
                        text = when {
                            accountUid == null -> favouriteLabel("Please sign in to view your entries.")
                            onlyFavourites -> favouriteLabel("No favourites yet")
                            else -> T("No entries yet")
                        },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = WinxDarkBlue
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = if (onlyFavourites && accountUid != null) {
                            favouriteLabel("Tap a heart on an entry to save it here.")
                        } else T("Start capturing your journey by creating your first entry."),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )

                    if (accountUid != null) Button(
                        onClick = if (onlyFavourites) ({ onlyFavourites = false }) else onAddEntryClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WinxOrange
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {

                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = T("Add entry")
                        )

                        Spacer(
                            modifier = Modifier.size(4.dp)
                        )

                        Text(
                            text = if (onlyFavourites) favouriteLabel("All entries") else T("Add Entry"),
                            color = WinxDarkBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

            } else {

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {

                    items(displayedEntries, key = { it.id }) { entry ->

                        TravelEntryCard(
                            entry = entry,
                            isFavourite = entry.id in favourites,
                            canToggleFavourite = favouritesReady && !favouritesBusy,
                            onToggleFavourite = {
                                if (accountUid != null && accountUid == auth.currentUser?.uid &&
                                    favouritesReady && !favouritesBusy
                                ) {
                                    val saved = entry.id !in favourites
                                    favouritesBusy = true
                                    scope.launch {
                                        try {
                                            favourites = FavouriteEntries.setFavourite(
                                                context, accountUid, entry.id, saved
                                            )
                                            favouritesError = false
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            favouritesReady = false
                                            favouritesError = true
                                        } finally {
                                            favouritesBusy = false
                                        }
                                    }
                                }
                            },
                            onClick = {
                                onEntryClick(entry)
                            }
                        )
                    }
                }
            }
        }
        if (accountUid != null) {
            Button(
                onClick = onAddEntryClick,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WinxBlue,
                    contentColor = WinxWhite
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(T("Add Entry"), fontWeight = FontWeight.Bold)
            }
        }
    }
}


// =====================================================================
// TRAVEL ENTRY CARD
// =====================================================================

@Composable
private fun TravelEntryCard(
    entry: TravelEntry,
    isFavourite: Boolean,
    canToggleFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = WinxLightPink
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title.ifBlank {
                            T("Untitled Entry")
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = WinxDarkBlue
                    )
                }
                IconButton(onClick = onToggleFavourite, enabled = canToggleFavourite && entry.id > 0) {
                    Icon(
                        imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = favouriteLabel(
                            if (isFavourite) "Remove from favourites" else "Add to favourites"
                        ),
                        tint = if (isFavourite) Color(0xFFC2185B) else WinxDarkBlue
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = T("Location"),
                    tint = WinxBlue
                )

                Spacer(
                    modifier = Modifier.size(3.dp)
                )

                Text(
                    text = if (
                        entry.location.isNotBlank() &&
                        entry.country.isNotBlank()
                    ) {
                        "${entry.location}, ${entry.country}"
                    } else if (entry.location.isNotBlank()) {
                        entry.location
                    } else if (entry.country.isNotBlank()) {
                        entry.country
                    } else {
                        T("Location not specified")
                    },
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = if (entry.rating > 0) {
                    T("Rating {0}", "★".repeat(entry.rating))
                } else {
                    T("No rating")
                },
                fontSize = 14.sp,
                color = WinxOrange,
                fontWeight = FontWeight.SemiBold
            )

            if (entry.notes.isNotBlank()) {

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = entry.notes,
                    fontSize = 14.sp,
                    color = WinxDarkBlue
                )
            }
        }
    }
}

@Composable
private fun favouriteLabel(text: String): String {
    val translations = when (currentWinxLanguage()) {
        "isiZulu (Zulu)", "zu", "zu-ZA" -> mapOf(
            "Surprise me" to "Ngimangaze",
            "Rediscover a random travel memory." to "Phinda uthole inkumbulo yohambo ekhethwe ngokungahleliwe.",
            "Add an entry to unlock random memories." to "Engeza okufakiwe ukuze uvule izinkumbulo ezingahleliwe.",
            "All entries" to "Konke okufakiwe",
            "Favourites" to "Izintandokazi",
            "Could not sync favourites. Tap to retry." to "Ayikwazanga ukuvumelanisa izintandokazi. Thepha ukuze uzame futhi.",
            "No favourites yet" to "Azikho izintandokazi okwamanje",
            "Tap a heart on an entry to save it here." to "Thepha inhliziyo kokufakiwe ukuze ukugcine lapha.",
            "Add to favourites" to "Engeza ezintandokazini",
            "Remove from favourites" to "Susa ezintandokazini",
            "Please sign in to view your entries." to "Sicela ungene ukuze ubone okufakiwe kwakho."
        )
        "Afrikaans", "af", "af-ZA" -> mapOf(
            "Surprise me" to "Verras my",
            "Rediscover a random travel memory." to "Herontdek 'n ewekansige reisherinnering.",
            "Add an entry to unlock random memories." to "Voeg 'n inskrywing by om ewekansige herinneringe te ontsluit.",
            "All entries" to "Alle inskrywings",
            "Favourites" to "Gunstelinge",
            "Could not sync favourites. Tap to retry." to "Kon nie gunstelinge sinkroniseer nie. Tik om weer te probeer.",
            "No favourites yet" to "Nog geen gunstelinge nie",
            "Tap a heart on an entry to save it here." to "Tik die hartjie op 'n inskrywing om dit hier te stoor.",
            "Add to favourites" to "Voeg by gunstelinge",
            "Remove from favourites" to "Verwyder uit gunstelinge",
            "Please sign in to view your entries." to "Meld aan om jou inskrywings te sien."
        )
        else -> emptyMap()
    }
    return translations[text] ?: text
}


@Composable
private fun favouriteChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = WinxWhite,
    labelColor = WinxDarkBlue,
    selectedContainerColor = WinxDarkBlue,
    selectedLabelColor = WinxWhite
)

