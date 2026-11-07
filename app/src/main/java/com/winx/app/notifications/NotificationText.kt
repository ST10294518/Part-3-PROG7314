package com.winx.app.notifications

import com.winx.app.utils.translate

/** New notification labels supplement the existing language dictionary. */
fun notificationText(text: String, language: String): String {
    val words = when (language) {
        "isiZulu (Zulu)" -> mapOf(
            "No notifications yet." to "Azikho izaziso okwamanje.",
            "New messages will appear here when received." to "Imiyalezo emisha izovela lapha uma itholwa.",
            "Clear history" to "Sula umlando",
            "Clear notification history?" to "Sula umlando wezaziso?",
            "This clears notification history for this account on all devices after they refresh." to "Lokhu kusula umlando wezaziso wale akhawunti kuwo wonke amadivayisi ngemva kokuvuselelwa.",
            "Mark all as read" to "Maka konke njengokufundiwe",
            "Unread" to "Akufundiwe",
            "Open Android notification settings" to "Vula izilungiselelo zezaziso ze-Android",
            "Android notifications are blocked. Allow them to receive alerts." to "Izaziso ze-Android zivinjiwe. Zivumele ukuze uthole izaziso.",
            "Alerts are on." to "Izaziso zivuliwe.",
            "Alerts are off. Received messages remain in your history." to "Izaziso zivaliwe. Imiyalezo etholiwe ihlala emlandweni wakho.",
            "Please sign in to view notifications." to "Sicela ungene ukuze ubuke izaziso.",
            "Allow notification permission" to "Vumela imvume yezaziso"
        )
        "Afrikaans" -> mapOf(
            "No notifications yet." to "Nog geen kennisgewings nie.",
            "New messages will appear here when received." to "Nuwe boodskappe sal hier verskyn wanneer dit ontvang word.",
            "Clear history" to "Vee geskiedenis uit",
            "Clear notification history?" to "Vee kennisgewinggeskiedenis uit?",
            "This clears notification history for this account on all devices after they refresh." to "Dit vee hierdie rekening se kennisgewinggeskiedenis op alle toestelle uit wanneer hulle verfris.",
            "Mark all as read" to "Merk alles as gelees",
            "Unread" to "Ongelees",
            "Open Android notification settings" to "Maak Android-kennisgewinginstellings oop",
            "Android notifications are blocked. Allow them to receive alerts." to "Android-kennisgewings is geblokkeer. Laat dit toe om kennisgewings te ontvang.",
            "Alerts are on." to "Kennisgewings is aan.",
            "Alerts are off. Received messages remain in your history." to "Kennisgewings is af. Ontvange boodskappe bly in jou geskiedenis.",
            "Please sign in to view notifications." to "Meld asseblief aan om kennisgewings te sien.",
            "Allow notification permission" to "Laat kennisgewingtoestemming toe"
        )
        else -> emptyMap()
    }
    return words[text] ?: translate(text, language)
}
