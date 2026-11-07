package com.winx.app.utils

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomMemoryRulesTest {

    @Test
    fun emptyHistoryHasNoMemory() {
        assertNull(RandomMemoryRules.pickId(emptyList()))
    }

    @Test
    fun unsavedEntriesCannotBeSelected() {
        assertNull(RandomMemoryRules.pickId(listOf(0, -1)))

        assertEquals(
            7,
            RandomMemoryRules.pickId(listOf(0, -1, 7))
        )
    }

    @Test
    fun singleMemoryCanBeOpenedAgain() {
        assertEquals(
            7,
            RandomMemoryRules.pickId(
                availableIds = listOf(7),
                previousId = 7
            )
        )
    }

    @Test
    fun previousMemoryIsSkippedWhenAnotherIsAvailable() {
        assertEquals(
            9,
            RandomMemoryRules.pickId(
                availableIds = listOf(7, 9),
                previousId = 7
            )
        )
    }

    @Test
    fun onlyAvailableEntriesCanBeSelected() {
        val available = listOf(2, 4, 6)
        val random = Random(42)

        repeat(30) {
            val selected = RandomMemoryRules.pickId(
                availableIds = available,
                previousId = 99,
                random = random
            )

            assertTrue(selected in available)
        }
    }

    @Test
    fun duplicateIdsStillAllowAvoidingPreviousMemory() {
        assertEquals(
            9,
            RandomMemoryRules.pickId(
                availableIds = listOf(7, 7, 9),
                previousId = 7
            )
        )
    }
}