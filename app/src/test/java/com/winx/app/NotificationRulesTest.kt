package com.winx.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRulesTest {
    @Test fun anotherAccountCannotReceivePrivateMessage() {
        assertFalse(NotificationRules.acceptsRecipient("account-b", "account-a", false))
    }
    @Test fun signedOutUserCannotReceiveHistoryEvenInDebug() {
        assertFalse(NotificationRules.acceptsRecipient(null, null, true))
    }
    @Test fun productionRequiresRecipient() {
        assertFalse(NotificationRules.acceptsRecipient("a", null, false))
        assertTrue(NotificationRules.acceptsRecipient("a", "a", false))
    }
    @Test fun genericConsoleTestMustBeExplicitlyAllowed() {
        assertTrue(NotificationRules.acceptsRecipient("a", null, true))
    }
    @Test fun repeatedEventPreservesReadStateAndDoesNotDuplicate() {
        val existing = NotificationRecord("id", "Title", "Body", 1, true)
        assertEquals(listOf(existing), NotificationRules.append(listOf(existing), existing.copy(read = false)))
    }
    @Test fun historyIsNewestFirstAndBounded() {
        val history = (1..100).map { NotificationRecord("$it", "Title", "Body", it.toLong()) }
        val result = NotificationRules.append(history, NotificationRecord("101", "Title", "Body", 101))
        assertEquals(100, result.size)
        assertEquals("101", result.first().id)
        assertEquals("2", result.last().id)
    }
}