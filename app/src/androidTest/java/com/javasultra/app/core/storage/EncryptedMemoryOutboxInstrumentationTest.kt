package com.javasultra.app.core.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.javasultra.app.core.memory.AndroidKeystoreMemoryEncryptor
import com.javasultra.app.core.memory.EncryptedMemoryRepository
import com.javasultra.app.core.memory.MemoryId
import com.javasultra.app.core.memory.MemoryKind
import com.javasultra.app.core.memory.MemoryLifecycle
import com.javasultra.app.core.memory.MemoryOutboxItem
import com.javasultra.app.core.memory.MemoryOutboxResult
import com.javasultra.app.core.memory.MemoryProvenance
import com.javasultra.app.core.memory.MemoryRecord
import com.javasultra.app.core.memory.MemoryRepositoryFailure
import com.javasultra.app.core.memory.MemoryRepositoryResult
import com.javasultra.app.core.memory.MemorySensitivity
import com.javasultra.app.core.memory.MemorySourceType
import com.javasultra.app.core.memory.MemoryVerification
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EncryptedMemoryOutboxInstrumentationTest {
    private lateinit var context: Context
    private val suffix = UUID.randomUUID().toString().replace("-", "")
    private val owner get() = "owner-$suffix"
    private val now = Instant.parse("2026-11-11T08:00:00Z")

    @Before fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("javas_encrypted_memory.db")
    }

    @After fun cleanup() {
        context.deleteDatabase("javas_encrypted_memory.db")
    }

    private fun record(id: String, content: String) = MemoryRecord(
        MemoryId(id), owner, MemoryKind.SEMANTIC, content,
        MemoryProvenance(MemorySourceType.RESEARCH_EVIDENCE, "source-$suffix",
            now.minusSeconds(10), "learning-agent"),
        now, now, 0.9, MemoryVerification.CORROBORATED, MemorySensitivity.INTERNAL,
        MemoryLifecycle.PROPOSED
    )

    @Test fun outboxPayloadIsEncryptedOwnerScopedAndAcknowledgementIsIdempotent() {
        val repository = EncryptedMemoryRepository(
            context, AndroidKeystoreMemoryEncryptor("javas.test.outbox.$suffix")
        )
        try {
            val payload = "private learning journal receipt $suffix".toByteArray()
            val item = MemoryOutboxItem("outbox-$suffix", owner, payload, now)
            assertTrue(repository.insertProposedBatchWithOutbox(
                listOf(record("memory-$suffix", "Verified public fact")), item, owner
            ) is MemoryRepositoryResult.Success)

            val stored = repository.readableDatabase.rawQuery(
                "SELECT encrypted_payload, owner_hash FROM memory_outbox WHERE outbox_id = ?",
                arrayOf(item.id)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                Pair(cursor.getBlob(0), cursor.getString(1))
            }
            assertFalse(String(stored.first, Charsets.UTF_8).contains(String(payload, Charsets.UTF_8)))
            assertFalse(stored.second.contains(owner))

            val pending = repository.pendingOutbox(owner)
            assertTrue(pending is MemoryOutboxResult.Success)
            assertEquals(1, (pending as MemoryOutboxResult.Success).value.size)
            assertTrue(pending.value.single().payload.contentEquals(payload))

            assertEquals(MemoryRepositoryFailure.OWNER_MISMATCH,
                (repository.acknowledgeOutbox(item.id, "other-owner") as MemoryOutboxResult.Failure).reason)
            assertTrue(repository.acknowledgeOutbox(item.id, owner) is MemoryOutboxResult.Success)
            assertTrue(repository.acknowledgeOutbox(item.id, owner) is MemoryOutboxResult.Success)
            assertTrue((repository.pendingOutbox(owner) as MemoryOutboxResult.Success).value.isEmpty())
        } finally {
            repository.close()
        }
    }

    @Test fun memoryBatchAndOutboxRollbackTogetherOnConflictingMemoryId() {
        val repository = EncryptedMemoryRepository(
            context, AndroidKeystoreMemoryEncryptor("javas.test.outbox.rollback.$suffix")
        )
        try {
            val existing = record("existing-$suffix", "Original memory")
            assertTrue(repository.insertProposedBatch(listOf(existing), owner) is MemoryRepositoryResult.Success)
            val item = MemoryOutboxItem(
                "outbox-rollback-$suffix", owner, "receipt".toByteArray(), now
            )
            val result = repository.insertProposedBatchWithOutbox(
                listOf(record("new-$suffix", "Should roll back"), existing.copy(content = "Conflicting")),
                item, owner
            )
            assertEquals(MemoryRepositoryFailure.DUPLICATE_ID,
                (result as MemoryRepositoryResult.Failure).reason)
            val newCount = repository.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM memory_records WHERE record_id = ?", arrayOf("new-$suffix")
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            val outboxCount = repository.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM memory_outbox WHERE outbox_id = ?", arrayOf(item.id)
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            assertEquals(0, newCount)
            assertEquals(0, outboxCount)
        } finally {
            repository.close()
        }
    }

    @Test fun exactProposalAndOutboxReplayDoesNotDuplicateRows() {
        val repository = EncryptedMemoryRepository(
            context, AndroidKeystoreMemoryEncryptor("javas.test.outbox.replay.$suffix")
        )
        try {
            val candidate = record("replay-$suffix", "Exact proposal")
            val item = MemoryOutboxItem("receipt-replay-$suffix", owner, byteArrayOf(1, 2, 3), now)
            repeat(2) {
                assertTrue(repository.insertProposedBatchWithOutbox(listOf(candidate), item, owner)
                    is MemoryRepositoryResult.Success)
            }
            assertEquals(1, (repository.pendingOutbox(owner) as MemoryOutboxResult.Success).value.size)
        } finally {
            repository.close()
        }
    }
}
