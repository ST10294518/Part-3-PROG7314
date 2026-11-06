package com.winx.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FavouriteRulesTest {
    @Test fun addsUnselectedEntry() {
        assertEquals(setOf(1, 2), FavouriteRules.toggle(setOf(1), 2))
    }
    @Test fun removesSelectedEntry() {
        assertEquals(setOf(2), FavouriteRules.toggle(setOf(1, 2), 1))
    }
    @Test fun ignoresUnsavedEntryIds() {
        assertEquals(setOf(2), FavouriteRules.toggle(setOf(2), 0))
        assertEquals(setOf(2), FavouriteRules.toggle(setOf(2), -1))
    }
    @Test fun deletedOrUnavailableEntriesStayHidden() {
        assertEquals(listOf(3, 1), FavouriteRules.visibleIds(listOf(3, 1, 2), setOf(1, 3, 99)))
    }
    @Test fun togglingTwiceRestoresOriginalSelection() {
        val original = setOf(1, 3)
        assertEquals(original, FavouriteRules.toggle(FavouriteRules.toggle(original, 2), 2))
    }
}
