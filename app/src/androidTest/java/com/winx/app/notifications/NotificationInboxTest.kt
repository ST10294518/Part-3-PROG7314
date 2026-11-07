package com.winx.app.notifications

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NotificationInboxTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val a = "push-test-a-${UUID.randomUUID()}"
    private val b = "push-test-b-${UUID.randomUUID()}"
    private fun event() = NotificationRecord(UUID.randomUUID().toString(), "Memory saved", "Saved", 10,
        kind = "entry.created")
    @After fun clean() {
        NotificationInbox.preferences(context, a).edit().clear().commit()
        NotificationInbox.preferences(context, b).edit().clear().commit()
    }
    @Test fun receivedHistorySurvivesContextReopen() {
        val n = event(); assertTrue(NotificationInbox.add(context, a, n))
        NotificationInbox.preferences(context, a).edit().putBoolean("test_flush", true).commit()
        val reopened = context.createPackageContext(context.packageName, 0)
        assertEquals(n, NotificationInbox.read(reopened, a).single())
    }
    @Test fun serverRecoveryBeforePushDoesNotDuplicateEvent() {
        val n = event(); NotificationInbox.mergeServer(context, a, listOf(n))
        assertFalse(NotificationInbox.add(context, a, n))
        assertEquals(1, NotificationInbox.read(context, a).size)
    }
    @Test fun clearCannotBeUndoneByDelayedPushOrRecovery() {
        val n = event(); NotificationInbox.add(context, a, n)
        NotificationInbox.clear(context, a)
        NotificationInbox.mergeServer(context, a, listOf(n))
        assertFalse(NotificationInbox.add(context, a, n))
        assertTrue(NotificationInbox.read(context, a).isEmpty())
        assertTrue(n.id in NotificationInbox.pending(context, a, "pending_clear"))
    }
    @Test fun accountQueuesAndHistoryRemainSeparate() {
        val n = event(); NotificationInbox.add(context, a, n)
        NotificationInbox.markRead(context, a, n.id)
        assertTrue(NotificationInbox.read(context, b).isEmpty())
        assertTrue(NotificationInbox.pending(context, b, "pending_read").isEmpty())
        assertTrue(n.id in NotificationInbox.pending(context, a, "pending_read"))
    }
    @Test fun lateUnreadReplayDoesNotUndoReadState() {
        val n = event(); NotificationInbox.add(context, a, n)
        NotificationInbox.markRead(context, a, n.id)
        NotificationInbox.mergeServer(context, a, listOf(n.copy(read = false)))
        assertFalse(NotificationInbox.add(context, a, n))
        assertTrue(NotificationInbox.read(context, a).single().read)
    }
}
