package com.winx.app.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.winx.app.data.remote.TravelEntryDto
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OfflineStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun draft(op: String) = TravelEntryDto(
        title = "Offline", location = "Durban", country = "South Africa", date = "2026-11-06T00:00:00",
        rating = 4, notes = "Private", clientOperationId = op
    )

    @Test fun pendingEntrySurvivesDatabaseReopen() {
        val name = "offline-test-${UUID.randomUUID()}.db"
        val op = UUID.randomUUID().toString()
        try {
            OfflineStore(context, name).use { it.enqueue("a", op, draft(op)) }
            OfflineStore(context, name).use {
                assertEquals(op, it.snapshot("a").pending.single().operationId)
                assertEquals("Private", it.snapshot("a").pending.single().entry.notes)
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun accountSwitchCannotExposeCacheOrQueue() {
        val name = "offline-test-${UUID.randomUUID()}.db"
        val op = UUID.randomUUID().toString()
        try {
            OfflineStore(context, name).use {
                it.enqueue("a", op, draft(op))
                it.replaceCache("a", listOf(draft(op).copy(id = 8)))
                assertTrue(it.snapshot("b").pending.isEmpty())
                assertTrue(it.snapshot("b").cached.isEmpty())
                assertEquals(1, it.snapshot("a").pending.size)
                assertEquals(1, it.snapshot("a").cached.size)
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun retryAndRepeatedLocalSaveKeepOneOperation() {
        val name = "offline-test-${UUID.randomUUID()}.db"
        val op = UUID.randomUUID().toString()
        try {
            OfflineStore(context, name).use {
                it.enqueue("a", op, draft(op))
                it.enqueue("a", op, draft(op))
                it.setState("a", op, "FAILED", "HTTP 404")
                it.retryFailures("b")
                assertEquals("FAILED", it.snapshot("a").pending.single().state)
                it.retryFailures("a")
                assertEquals("PENDING", it.snapshot("a").pending.single().state)
                assertEquals(op, it.snapshot("a").pending.single().operationId)
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun acknowledgementIsAtomicAndScopedToOwner() {
        val name = "offline-test-${UUID.randomUUID()}.db"
        val op = UUID.randomUUID().toString()
        try {
            OfflineStore(context, name).use {
                it.enqueue("a", op, draft(op))
                it.acknowledge("b", op, draft(op).copy(id = 9))
                assertEquals(1, it.snapshot("a").pending.size)
                assertTrue(it.snapshot("b").cached.isEmpty())
                it.acknowledge("a", op, draft(op).copy(id = 9))
                it.acknowledge("a", op, draft(op).copy(id = 9))
                assertTrue(it.snapshot("a").pending.isEmpty())
                assertEquals(9, it.snapshot("a").cached.single().id)
            }
        } finally { context.deleteDatabase(name) }
    }
}
