package com.javasultra.app.core.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.javasultra.app.core.learning.EncryptedLearningJournalStore
import com.javasultra.app.core.learning.LearningDomain
import com.javasultra.app.core.learning.LearningJournalEntry
import com.javasultra.app.core.learning.LearningJournalEvent
import com.javasultra.app.core.learning.LearningJournalEventType
import com.javasultra.app.core.learning.LearningJournalState
import com.javasultra.app.core.learning.LearningJournalStoreFailure
import com.javasultra.app.core.learning.LearningJournalStoreResult
import com.javasultra.app.core.memory.AndroidKeystoreMemoryEncryptor
import com.javasultra.app.core.memory.EncryptedMemoryRepository
import com.javasultra.app.core.memory.MemoryEncryptor
import com.javasultra.app.core.memory.MemoryId
import com.javasultra.app.core.memory.MemoryKind
import com.javasultra.app.core.memory.MemoryLifecycle
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
class EncryptedStorageInstrumentationTest {
    private lateinit var context: Context
    private val suffix = UUID.randomUUID().toString().replace("-", "")

    @Before
    fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("javas_encrypted_memory.db")
        context.deleteDatabase("javas_learning_journal.db")
    }

    @After
    fun cleanUp() {
        context.deleteDatabase("javas_encrypted_memory.db")
        context.deleteDatabase("javas_learning_journal.db")
    }

    private fun record(id: String, content: String, owner: String = "creator-$suffix"): MemoryRecord {
        val created = Instant.parse("2026-11-10T10:00:00Z")
        return MemoryRecord(
            id = MemoryId(id),
            ownerPrincipalId = owner,
            kind = MemoryKind.SEMANTIC,
            content = content,
            provenance = MemoryProvenance(
                MemorySourceType.RESEARCH_EVIDENCE, "research-$suffix",
                created.minusSeconds(30), "javas-learning-agent"
            ),
            createdAt = created,
            updatedAt = created,
            confidence = 0.9,
            verification = MemoryVerification.CORROBORATED,
            sensitivity = MemorySensitivity.INTERNAL,
            lifecycle = MemoryLifecycle.PROPOSED,
            tags = setOf("instrumentation", "storage")
        )
    }

    @Test
    fun memoryBatchIsEncryptedAndExactRetryDoesNotDuplicateRows() {
        val owner = "creator-$suffix"
        val candidate = record("record-$suffix", "Confidential test memory $suffix", owner)
        val repository = EncryptedMemoryRepository(
            context, AndroidKeystoreMemoryEncryptor("javas.test.memory.$suffix")
        )
        try {
            assertTrue(repository.insertProposedBatch(listOf(candidate), owner) is MemoryRepositoryResult.Success)
            assertTrue(repository.insertProposedBatch(listOf(candidate), owner) is MemoryRepositoryResult.Success)

            val ciphertext = repository.readableDatabase.rawQuery(
                "SELECT encrypted_payload FROM memory_records WHERE record_id = ?",
                arrayOf(candidate.id.value)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getBlob(0)
            }
            assertFalse(String(ciphertext, Charsets.UTF_8).contains(candidate.content))

            val count = repository.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM memory_records WHERE record_id = ?",
                arrayOf(candidate.id.value)
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            assertEquals(1, count)
        } finally {
            repository.close()
        }
    }

    @Test
    fun conflictingExistingRecordRollsBackEarlierNewRowsInBatch() {
        val owner = "creator-$suffix"
        val existing = record("existing-$suffix", "Original approved proposal", owner)
        val newRecord = record("new-$suffix", "New proposed memory", owner)
        val conflict = existing.copy(content = "Changed content with same ID")
        val repository = EncryptedMemoryRepository(
            context, AndroidKeystoreMemoryEncryptor("javas.test.memory.rollback.$suffix")
        )
        try {
            assertTrue(repository.insertProposedBatch(listOf(existing), owner) is MemoryRepositoryResult.Success)
            val result = repository.insertProposedBatch(listOf(newRecord, conflict), owner)
            assertEquals(
                MemoryRepositoryFailure.DUPLICATE_ID,
                (result as MemoryRepositoryResult.Failure).reason
            )
            val newCount = repository.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM memory_records WHERE record_id = ?",
                arrayOf(newRecord.id.value)
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            assertEquals(0, newCount)
        } finally {
            repository.close()
        }
    }

    private fun journalEntry() = LearningJournalEntry(
        id = "journal-$suffix",
        taskId = "task-$suffix",
        objective = "Verify encrypted persistence",
        domain = LearningDomain.SCIENCE,
        state = LearningJournalState.KNOWLEDGE_PROPOSED,
        createdAt = Instant.parse("2026-11-10T10:00:00Z"),
        updatedAt = Instant.parse("2026-11-10T10:01:00Z")
    )

    private fun receipt(entry: LearningJournalEntry) = LearningJournalEvent(
        id = "receipt-$suffix",
        entryId = entry.id,
        type = LearningJournalEventType.MEMORY_PROPOSAL_PERSISTED,
        occurredAt = Instant.parse("2026-11-10T10:02:00Z"),
        actor = "creator-$suffix",
        safeDetail = "PROPOSAL_BATCH_PERSISTED"
    )

    @Test
    fun journalReceiptReplayIsIdempotentAndConflictingReplayIsRejected() {
        val entry = journalEntry()
        val store = EncryptedLearningJournalStore(
            context, AndroidKeystoreMemoryEncryptor("javas.test.journal.$suffix")
        )
        try {
            assertTrue(store.create(entry) is LearningJournalStoreResult.Success)
            assertTrue(store.append(receipt(entry), entry.taskId) is LearningJournalStoreResult.Success)
            assertTrue(store.append(receipt(entry), entry.taskId) is LearningJournalStoreResult.Success)
            assertEquals(
                LearningJournalStoreFailure.EVENT_ID_COLLISION,
                (store.append(receipt(entry).copy(actor = "different-actor"), entry.taskId)
                    as LearningJournalStoreResult.Failure).reason
            )
            assertEquals(1, (store.events(entry.id) as LearningJournalStoreResult.Success).value.size)
            assertEquals(
                LearningJournalState.KNOWLEDGE_PROPOSED,
                (store.get(entry.id) as LearningJournalStoreResult.Success).value.state
            )
        } finally {
            store.close()
        }
    }

    private class FailOnThirdEncrypt(alias: String) : MemoryEncryptor {
        private val delegate = AndroidKeystoreMemoryEncryptor(alias)
        private var encryptCount = 0

        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
            encryptCount++
            if (encryptCount == 3) error("Injected snapshot encryption failure")
            return delegate.encrypt(plaintext, associatedData)
        }

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray =
            delegate.decrypt(ciphertext, associatedData)
    }

    @Test
    fun journalSnapshotEncryptionFailureRollsBackTheEventInsert() {
        val entry = journalEntry()
        val store = EncryptedLearningJournalStore(
            context, FailOnThirdEncrypt("javas.test.journal.rollback.$suffix")
        )
        try {
            assertTrue(store.create(entry) is LearningJournalStoreResult.Success)
            assertEquals(
                LearningJournalStoreFailure.STORAGE_ERROR,
                (store.append(receipt(entry), entry.taskId) as LearningJournalStoreResult.Failure).reason
            )
            assertTrue((store.events(entry.id) as LearningJournalStoreResult.Success).value.isEmpty())
            assertEquals(
                LearningJournalState.KNOWLEDGE_PROPOSED,
                (store.get(entry.id) as LearningJournalStoreResult.Success).value.state
            )
        } finally {
            store.close()
        }
    }
}
