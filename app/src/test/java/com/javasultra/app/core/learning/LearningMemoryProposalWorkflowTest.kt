package com.javasultra.app.core.learning

import com.javasultra.app.core.memory.MemoryLifecycle
import com.javasultra.app.core.memory.MemoryOutboxItem
import com.javasultra.app.core.memory.MemoryOutboxResult
import com.javasultra.app.core.memory.MemoryOutboxStore
import com.javasultra.app.core.memory.MemoryRecord
import com.javasultra.app.core.memory.MemoryRepositoryFailure
import com.javasultra.app.core.memory.MemoryRepositoryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LearningMemoryProposalWorkflowTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private val digest: String by lazy(LazyThreadSafetyMode.NONE) {
        LearningEvidenceDigest.computeSha256(listOf(claim()), sources())
    }
    private val creator = "creator-1"
    private val journalEntryId = "journal-workflow-1"

    private fun request(decision: CreatorLearningDecision = CreatorLearningDecision.APPROVE) =
        CreatorLearningReviewRequest(
            task = LearningTask(
                "task-workflow-1", "Research a public science topic", LearningDomain.SCIENCE,
                now.minusSeconds(60), LearningTaskState.READY_FOR_REVIEW, true, 10, 3600, true
            ),
            creatorPrincipalId = creator,
            authenticatedReviewerPrincipalId = creator,
            reviewId = "review-workflow-1",
            decision = decision,
            expectedEvidenceDigestSha256 = digest,
            submittedEvidenceDigestSha256 = digest,
            claimIds = setOf("claim-workflow-1"),
            reviewedAt = now.minusSeconds(5),
            stepUpAuthenticated = true
        )

    private fun claim() = EvidenceClaim(
        "claim-workflow-1", "A corroborated public research statement",
        setOf("source-workflow-a", "source-workflow-b"),
        setOf("source-workflow-a", "source-workflow-b"), emptySet(),
        now.minusSeconds(60), null, ClaimStatus.SUPPORTED, 0.85
    )

    private fun sources() = listOf(
        ResearchSource(
            "source-workflow-a", EvidenceSourceKind.OFFICIAL_DOCUMENTATION, "Source A",
            "Publisher A", "https://a.example.org", now.minusSeconds(120), now.minusSeconds(30), 0.9
        ),
        ResearchSource(
            "source-workflow-b", EvidenceSourceKind.ACADEMIC_REVIEW, "Source B",
            "Publisher B", "https://b.example.org", now.minusSeconds(120), now.minusSeconds(20), 0.9
        )
    )

    private class RecordingWriter(
        var result: MemoryRepositoryResult<Unit> = MemoryRepositoryResult.Success(Unit)
    ) : LearningProposalBatchWriter {
        var calls = 0
        var records = emptyList<MemoryRecord>()
        var actor: String? = null
        override fun insertProposedBatch(records: List<MemoryRecord>, actorPrincipalId: String): MemoryRepositoryResult<Unit> {
            calls++
            this.records = records
            actor = actorPrincipalId
            return result
        }
    }

    private class RecordingJournalWriter(
        var result: LearningJournalStoreResult<LearningJournalEntry>
    ) : LearningJournalReceiptWriter {
        var calls = 0
        var event: LearningJournalEvent? = null
        var expectedTaskId: String? = null
        override fun append(event: LearningJournalEvent, expectedTaskId: String): LearningJournalStoreResult<LearningJournalEntry> {
            calls++
            this.event = event
            this.expectedTaskId = expectedTaskId
            return result
        }
    }

    private fun journalWriter(
        result: LearningJournalStoreResult<LearningJournalEntry> =
            LearningJournalStoreResult.Success(
                LearningJournalEntry(
                    journalEntryId, "task-workflow-1", "Research a public science topic",
                    LearningDomain.SCIENCE, LearningJournalState.KNOWLEDGE_PROPOSED,
                    now.minusSeconds(120), now.minusSeconds(10)
                )
            )
    ) = RecordingJournalWriter(result)

    private fun execute(
        req: CreatorLearningReviewRequest = request(),
        writer: RecordingWriter = RecordingWriter(),
        journal: RecordingJournalWriter = journalWriter(),
        creatorId: String = creator,
        entryId: String = journalEntryId,
        consumed: Set<String> = emptySet()
    ) = LearningMemoryProposalWorkflow(writer, journal).approveAndPersist(
        req, consumed, listOf(claim()), sources(), creatorId, entryId, now
    )

    @Test fun approvedClaimsArePersistedAndJournaledAsOneProposedBatch() {
        val writer = RecordingWriter()
        val journal = journalWriter()
        val result = execute(writer = writer, journal = journal)
        assertTrue(result is LearningProposalWorkflowResult.Persisted)
        assertEquals(1, writer.calls)
        assertEquals(creator, writer.actor)
        assertEquals(1, writer.records.size)
        assertEquals(creator, writer.records.single().ownerPrincipalId)
        assertEquals(MemoryLifecycle.PROPOSED, writer.records.single().lifecycle)
        assertEquals(1, journal.calls)
        assertEquals(LearningJournalEventType.MEMORY_PROPOSAL_PERSISTED, journal.event?.type)
        assertEquals("PROPOSAL_BATCH_PERSISTED", journal.event?.safeDetail)
        assertEquals("task-workflow-1", journal.expectedTaskId)
    }

    @Test fun journalFailureReportsDurableMemoryAndReturnsStableReceiptForRetry() {
        val writer = RecordingWriter()
        val journal = journalWriter(LearningJournalStoreResult.Failure(LearningJournalStoreFailure.STORAGE_ERROR))
        val result = execute(writer = writer, journal = journal)
        assertTrue(result is LearningProposalWorkflowResult.PersistedJournalPending)
        val pending = result as LearningProposalWorkflowResult.PersistedJournalPending
        assertEquals(1, writer.calls)
        assertEquals(LearningJournalStoreFailure.STORAGE_ERROR, pending.journalFailure)
        assertTrue(pending.receiptEvent.id.startsWith("proposal-receipt-"))
        assertEquals(creator, pending.receiptEvent.actor)
        assertEquals(journalEntryId, pending.receiptEvent.entryId)
        assertEquals("PROPOSAL_BATCH_PERSISTED", pending.receiptEvent.safeDetail)
    }

    @Test fun retryAfterJournalFailureUsesTheSameReceiptEventId() {
        val firstJournal = journalWriter(LearningJournalStoreResult.Failure(LearningJournalStoreFailure.STORAGE_ERROR))
        val first = execute(journal = firstJournal) as LearningProposalWorkflowResult.PersistedJournalPending
        val secondJournal = journalWriter()
        val second = execute(journal = secondJournal)
        assertTrue(second is LearningProposalWorkflowResult.Persisted)
        assertEquals(first.receiptEvent.id, secondJournal.event?.id)
        assertEquals(1, secondJournal.calls)
    }

    @Test fun alteredEvidenceCannotBePersistedUnderAnUnchangedReviewDigest() {
        val writer = RecordingWriter()
        val journal = journalWriter()
        val req = request()
        val alteredClaim = claim().copy(statement = "Tampered statement with original claim ID")
        val result = LearningMemoryProposalWorkflow(writer, journal).approveAndPersist(
            req, emptySet(), listOf(alteredClaim), sources(), creator, journalEntryId, now
        )
        assertTrue(result is LearningProposalWorkflowResult.Rejected)
        assertEquals(LearningProposalDenial.EVIDENCE_DIGEST_MISMATCH,
            (result as LearningProposalWorkflowResult.Rejected).mappingDenial)
        assertEquals(0, writer.calls)
        assertEquals(0, journal.calls)
    }

    @Test fun journalReceiptIsBoundToTheLearningTaskId() {
        val writer = RecordingWriter()
        val journal = journalWriter(LearningJournalStoreResult.Failure(LearningJournalStoreFailure.TASK_MISMATCH))
        val result = execute(writer = writer, journal = journal)
        assertTrue(result is LearningProposalWorkflowResult.PersistedJournalPending)
        assertEquals(LearningJournalStoreFailure.TASK_MISMATCH,
            (result as LearningProposalWorkflowResult.PersistedJournalPending).journalFailure)
        assertEquals("task-workflow-1", journal.expectedTaskId)
        assertEquals(1, writer.calls)
    }

    @Test fun invalidJournalEntryStopsBeforeMemoryPersistence() {
        val writer = RecordingWriter()
        val journal = journalWriter()
        val result = execute(writer = writer, journal = journal, entryId = " ")
        assertTrue(result is LearningProposalWorkflowResult.Rejected)
        assertEquals(LearningProposalWorkflowDenial.JOURNAL_ENTRY_INVALID,
            (result as LearningProposalWorkflowResult.Rejected).reason)
        assertEquals(0, writer.calls)
        assertEquals(0, journal.calls)
    }

    @Test fun rejectedCreatorReviewNeverCallsEitherStore() {
        val writer = RecordingWriter()
        val journal = journalWriter()
        val result = execute(req = request(CreatorLearningDecision.REJECT), writer = writer, journal = journal)
        assertTrue(result is LearningProposalWorkflowResult.Rejected)
        assertEquals(LearningProposalWorkflowDenial.REVIEW_REJECTED, (result as LearningProposalWorkflowResult.Rejected).reason)
        assertEquals(0, writer.calls)
        assertEquals(0, journal.calls)
    }

    @Test fun consumedReviewIdIsRejectedBeforePersistence() {
        val req = request()
        val writer = RecordingWriter()
        val journal = journalWriter()
        val result = execute(req = req, writer = writer, journal = journal, consumed = setOf(req.reviewId))
        assertTrue(result is LearningProposalWorkflowResult.Rejected)
        assertEquals(CreatorReviewDenial.REVIEW_ALREADY_CONSUMED,
            (result as LearningProposalWorkflowResult.Rejected).reviewDenial)
        assertEquals(0, writer.calls)
        assertEquals(0, journal.calls)
    }

    @Test fun wrongAuthenticatedReviewerIsRejectedBeforePersistence() {
        val req = request().copy(authenticatedReviewerPrincipalId = "other-user")
        val writer = RecordingWriter()
        val journal = journalWriter()
        val result = execute(req = req, writer = writer, journal = journal)
        assertTrue(result is LearningProposalWorkflowResult.Rejected)
        assertEquals(CreatorReviewDenial.INVALID_IDENTITY,
            (result as LearningProposalWorkflowResult.Rejected).reviewDenial)
        assertEquals(0, writer.calls)
        assertEquals(0, journal.calls)
    }

    @Test fun creatorMismatchIsRejectedBeforePersistence() {
        val writer = RecordingWriter()
        val journal = journalWriter()
        val result = execute(writer = writer, journal = journal, creatorId = "different-creator")
        assertTrue(result is LearningProposalWorkflowResult.Rejected)
        assertEquals(LearningProposalWorkflowDenial.MAPPING_REJECTED, (result as LearningProposalWorkflowResult.Rejected).reason)
        assertEquals(0, writer.calls)
        assertEquals(0, journal.calls)
        assertFalse(result is LearningProposalWorkflowResult.Persisted)
    }

    @Test fun memoryPersistenceFailureNeverAppendsJournalReceipt() {
        val writer = RecordingWriter(MemoryRepositoryResult.Failure(MemoryRepositoryFailure.STORAGE_ERROR))
        val journal = journalWriter()
        val result = execute(writer = writer, journal = journal)
        assertTrue(result is LearningProposalWorkflowResult.Rejected)
        assertEquals(MemoryRepositoryFailure.STORAGE_ERROR,
            (result as LearningProposalWorkflowResult.Rejected).persistenceFailure)
        assertEquals(1, writer.calls)
        assertEquals(0, journal.calls)
    }

    private class FakeOutboxStore : MemoryOutboxStore {
        val pending = linkedMapOf<String, MemoryOutboxItem>()
        override fun insertProposedBatchWithOutbox(
            records: List<MemoryRecord>, outboxItem: MemoryOutboxItem, actorPrincipalId: String
        ): MemoryRepositoryResult<Unit> {
            if (outboxItem.ownerPrincipalId != actorPrincipalId) {
                return MemoryRepositoryResult.Failure(MemoryRepositoryFailure.OWNER_MISMATCH)
            }
            pending.putIfAbsent(outboxItem.id, outboxItem)
            return MemoryRepositoryResult.Success(Unit)
        }
        override fun pendingOutbox(actorPrincipalId: String, limit: Int): MemoryOutboxResult<List<MemoryOutboxItem>> =
            MemoryOutboxResult.Success(pending.values.filter { it.ownerPrincipalId == actorPrincipalId }.take(limit))
        override fun acknowledgeOutbox(id: String, actorPrincipalId: String): MemoryOutboxResult<Unit> {
            val item = pending[id] ?: return MemoryOutboxResult.Success(Unit)
            if (item.ownerPrincipalId != actorPrincipalId) {
                return MemoryOutboxResult.Failure(MemoryRepositoryFailure.OWNER_MISMATCH)
            }
            pending.remove(id)
            return MemoryOutboxResult.Success(Unit)
        }
    }

    private class OutboxAwareWriter(private val store: FakeOutboxStore) :
        LearningProposalBatchWriter, LearningProposalOutboxBatchWriter {
        var atomicCalls = 0
        override fun insertProposedBatch(
            records: List<MemoryRecord>, actorPrincipalId: String
        ): MemoryRepositoryResult<Unit> = MemoryRepositoryResult.Success(Unit)
        override fun insertProposedBatchWithOutbox(
            records: List<MemoryRecord>, outboxItem: MemoryOutboxItem, actorPrincipalId: String
        ): MemoryRepositoryResult<Unit> {
            atomicCalls++
            return store.insertProposedBatchWithOutbox(records, outboxItem, actorPrincipalId)
        }
    }

    @Test fun durableOutboxPathCommitsProposalAndReceiptBeforeJournalDispatch() {
        val store = FakeOutboxStore()
        val writer = OutboxAwareWriter(store)
        val journal = journalWriter()
        val dispatcher = LearningMemoryProposalOutboxDispatcher(store, journal)
        val workflow = LearningMemoryProposalWorkflow(writer, journal, dispatcher)
        val result = workflow.approveAndPersist(
            request(), emptySet(), listOf(claim()), sources(), creator, journalEntryId, now
        )
        assertTrue(result is LearningProposalWorkflowResult.Persisted)
        assertEquals(1, writer.atomicCalls)
        assertEquals(1, journal.calls)
        assertTrue(store.pending.isEmpty())
    }

    @Test fun durableOutboxKeepsReceiptForRestartWhenJournalWriteFails() {
        val store = FakeOutboxStore()
        val writer = OutboxAwareWriter(store)
        val journal = journalWriter(
            LearningJournalStoreResult.Failure(LearningJournalStoreFailure.STORAGE_ERROR)
        )
        val dispatcher = LearningMemoryProposalOutboxDispatcher(store, journal)
        val workflow = LearningMemoryProposalWorkflow(writer, journal, dispatcher)
        val result = workflow.approveAndPersist(
            request(), emptySet(), listOf(claim()), sources(), creator, journalEntryId, now
        )
        assertTrue(result is LearningProposalWorkflowResult.PersistedJournalPending)
        assertEquals(1, store.pending.size)
        journal.result = journalWriter().result
        val replay = dispatcher.dispatchPending(creator).single()
        assertTrue(replay.result is LearningJournalStoreResult.Success)
        assertTrue(store.pending.isEmpty())
    }
}
