package com.winx.app.utils

object FavouriteRules {
    fun toggle(existing: Set<Int>, entryId: Int): Set<Int> = when {
        entryId <= 0 -> existing
        entryId in existing -> existing - entryId
        else -> existing + entryId
    }

    // Filter against the currently available entries; never resurrect deleted entries.
    fun visibleIds(availableIds: List<Int>, favourites: Set<Int>): List<Int> =
        availableIds.filter { it in favourites }
}
