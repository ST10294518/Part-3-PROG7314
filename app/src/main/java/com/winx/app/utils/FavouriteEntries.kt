package com.winx.app.utils

import android.content.Context

/** Device-local favourites, stored separately for every Firebase account. */
object FavouriteEntries {
    private fun preferences(context: Context, uid: String) =
        context.getSharedPreferences("winx_favourites_$uid", Context.MODE_PRIVATE)

    fun read(context: Context, uid: String): Set<Int> =
        preferences(context, uid).getStringSet("entry_ids", emptySet())
            .orEmpty().mapNotNull { it.toIntOrNull() }.filter { it > 0 }.toSet()

    fun toggle(context: Context, uid: String, entryId: Int): Set<Int> {
        val updated = FavouriteRules.toggle(read(context, uid), entryId)
        preferences(context, uid).edit()
            .putStringSet("entry_ids", updated.map { it.toString() }.toSet()).apply()
        return updated
    }
}
