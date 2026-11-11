package com.javasultra.app.core.learning

import com.javasultra.app.core.memory.MemoryId
import com.javasultra.app.core.memory.MemoryOutboxItem
import com.javasultra.app.core.memory.MemoryOutboxResult
import com.javasultra.app.core.memory.MemoryOutboxStore
import com.javasultra.app.core.memory.MemoryRecord
import com.javasultra.app.core.memory.MemoryRepositoryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LearningMemoryProposalOutboxTest {
    private val now = Instant.parse("2026-11-11T08:00:00Z")
    private val actor = "creator-outbox"

    private fun entry() = LearningJournalEntry(
        "entry-outbox", "task-outbox", "Persist verified learning",
        LearningDomain.SCIENCE, LearningJournalState.KNOWLEDGE_PROPOSED,
        now.minusSeconds(60), now.minusSeconds(30)
    )

    private fun event() = LearningJournalEvent(
        "receipt-outbox", "entry-outbox", LearningJournalEventType.MEMORY_PROPOSAL_PERSISTED,
        now.minusSeconds(10), actor, safeDetail = "PROPOSAL_BATCH_PERSISTED"
    )

    private fun item() = MemoryOutboxItem(
        event().id, actor,
        LearningJournalReceiptEnvelopeCodec.encode(LearningJournalReceiptEnvelope("task-outbox", event())),
        now
    )

    private class FakeOutbox : MemoryOutboxStore {
        val pending = linkedMapOf<String, MemoryOutboxItem>()
        var failAck = false
        override fun insertProposedBatchWithOutbox(
            records: List<MemoryRecord>, outboxItem: MemoryOutboxItem, actorPrincipalId: String
        ): MemoryRepositoryResult<Unit> {
            if (outboxItem.ownerPrincipalId != actorPrincipalId) {
                return MemoryRepositoryResult.Failure(com.javasultra.app.core.memory.MemoryRepositoryFailure.OWNER_MISMATCH)
            }
            pending.putIfAbsent(outboxItem.id, outboxItem.copy(payload = outboxItem.payload.copyOf()))
            return MemoryRepositoryResult.Success(Unit)
        }
        override fun pendingOutbox(actorPrincipalId: String, limit: Int): MemoryOutboxResult<List<MemoryOutboxItem>> =
            MemoryOutboxResult.Success(pending.values.filter { it.ownerPrincipalId == actorPrincipalId }.take(limit))
        override fun acknowledgeOutbox(id: String, actorPrincipalId: String): MemoryOutboxResult<Unit> {
            if (failAck) return MemoryOutboxResult.Failure(com.javasultra.app.core.memory.MemoryRepositoryFailure.STORAGE_ERROR)
            val current = pending[id] ?: return MemoryOutboxResult.Success(Unit)
            if (current.ownerPrincipalId != actorPrincipalId) {
                return MemoryOutboxResult.Failure(com.javasultra.app.core.memory.MemoryRepositoryFailure.OWNER_MISMATCH)
            }
            pending.remove(id)
            return MemoryOutboxResult.Success(Unit)
        }
    }

    private class FakeJournal(
        var result: LearningJournalStoreResult<LearningJournalEntry> =
            LearningJournalStoreResult.Success(
                LearningJournalEntry(
                    "entry-outbox", "task-outbox", "Persist verified learning",
                    LearningDomain.SCIENCE, LearningJournalState.KNOWLEDGE_PROPOSED,
                    Instant.parse("2026-11-11T07:59:00Z"), Instant.parse("2026-11-11T07:59:50Z")
                )
            )
    ) : LearningJournalReceiptWriter {
        var calls = 0
        var lastEvent: LearningJournalEvent? = null
        var lastTask: String? = null
        override fun append(event: LearningJournalEvent, expectedTaskId: String): LearningJournalStoreResult<LearningJournalEntry> {
            calls++
            lastEvent = event
            lastTask = expectedTaskId
            return result
        }
    }

    @Test fun receiptEnvelopeRoundTripsAndRejectsTrailingBytes() {
        val encoded = LearningJournalReceiptEnvelopeCodec.encode(LearningJournalReceiptEnvelope("task-outbox", event()))
        val decoded = LearningJournalReceiptEnvelopeCodec.decode(encoded)
        assertEquals("task-outbox", decoded.expectedTaskId)
        assertEquals(event(), decoded.event)
        assertTrue(runCatching { LearningJournalReceiptEnvelopeCodec.decode(encoded + byteArrayOf(0)) }.isFailure)
    }

    @Test fun successfulJournalDeliveryAcknowledgesOutboxExactlyOnce() {
        val store = FakeOutbox().apply { pending[item().id] = item() }
        val journal = FakeJournal()
        val result = LearningMemoryProposalOutboxDispatcher(store, journal).dispatchPending(actor).single()
        assertTrue(result.result is LearningJournalStoreResult.Success)
        assertEquals("task-outbox", journal.lastTask)
        assertEquals(event(), journal.lastEvent)
        assertTrue(store.pending.isEmpty())
    }

    @Test fun journalFailureLeavesDurableItemPendingForRestartRetry() {
        val store = FakeOutbox().apply { pending[item().id] = item() }
        val journal = FakeJournal(LearningJournalStoreResult.Failure(LearningJournalStoreFailure.STORAGE_ERROR))
        val dispatcher = LearningMemoryProposalOutboxDispatcher(store, journal)
        val first = dispatcher.dispatchPending(actor).single()
        assertEquals(LearningJournalStoreFailure.STORAGE_ERROR,
            (first.result as LearningJournalStoreResult.Failure).reason)
        assertEquals(1, store.pending.size)

        journal.result = FakeJournal().result
        val second = dispatcher.dispatchPending(actor).single()
        assertTrue(second.result is LearningJournalStoreResult.Success)
        assertTrue(store.pending.isEmpty())
    }

    @Test fun appendSucceededButAckFailedCanBeReplayedIdempotently() {
        val store = FakeOutbox().apply { pending[item().id] = item(); failAck = true }
        val journal = FakeJournal()
        val dispatcher = LearningMemoryProposalOutboxDispatcher(store, journal)
        val first = dispatcher.dispatchPending(actor).single()
        assertEquals(LearningJournalStoreFailure.STORAGE_ERROR,
            (first.result as LearningJournalStoreResult.Failure).reason)
        assertEquals(1, store.pending.size)
        store.failAck = false
        val second = dispatcher.dispatchPending(actor).single()
        assertTrue(second.result is LearningJournalStoreResult.Success)
        assertEquals(2, journal.calls)
        assertTrue(store.pending.isEmpty())
    }

    @Test fun outboxCannotBeDispatchedByAnotherOwner() {
        val store = FakeOutbox().apply { pending[item().id] = item() }
        val journal = FakeJournal()
        val result = LearningMemoryProposalOutboxDispatcher(store, journal).dispatch(item(), "intruder")
        assertEquals(LearningJournalStoreFailure.INVALID_EVENT,
            (result as LearningJournalStoreResult.Failure).reason)
        assertEquals(0, journal.calls)
        assertFalse(store.pending.isEmpty())
    }

    @Test fun envelopeActorMismatchIsRejectedBeforeJournalWrite() {
        val store = FakeOutbox()
        val tampered = event().copy(actor = "other-creator")
        val badItem = MemoryOutboxItem(
            "bad-envelope", actor,
            LearningJournalReceiptEnvelopeCodec.encode(LearningJournalReceiptEnvelope("task-outbox", tampered)),
            now
        )
        val journal = FakeJournal()
        val result = LearningMemoryProposalOutboxDispatcher(store, journal).dispatch(badItem, actor)
        assertEquals(LearningJournalStoreFailure.INVALID_EVENT,
            (result as LearningJournalStoreResult.Failure).reason)
        assertEquals(0, journal.calls)
    }
}
