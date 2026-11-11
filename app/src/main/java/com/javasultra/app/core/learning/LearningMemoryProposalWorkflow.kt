package com.javasultra.app.core.learning

import com.javasultra.app.core.memory.MemoryRecord
import com.javasultra.app.core.memory.MemoryOutboxItem
import com.javasultra.app.core.memory.MemoryOutboxStore
import com.javasultra.app.core.memory.MemoryRepository
import com.javasultra.app.core.memory.MemoryRepositoryFailure
import com.javasultra.app.core.memory.MemoryRepositoryResult
import java.security.MessageDigest
import java.time.Instant

enum class LearningProposalWorkflowDenial {
    REVIEW_REJECTED,
    MAPPING_REJECTED,
    EMPTY_PROPOSAL_SET,
    PERSISTENCE_REJECTED,
    JOURNAL_ENTRY_INVALID
}

sealed interface LearningProposalWorkflowResult {
    data class Persisted(
        val proposedRecords: List<MemoryRecord>,
        val journalEntry: LearningJournalEntry
    ) : LearningProposalWorkflowResult

    /** Memory is durable, but its journal receipt needs an idempotent retry. */
    data class PersistedJournalPending(
        val proposedRecords: List<MemoryRecord>,
        val receiptEvent: LearningJournalEvent,
        val journalFailure: LearningJournalStoreFailure
    ) : LearningProposalWorkflowResult

    data class Rejected(
        val reason: LearningProposalWorkflowDenial,
        val reviewDenial: CreatorReviewDenial? = null,
        val mappingDenial: LearningProposalDenial? = null,
        val persistenceFailure: MemoryRepositoryFailure? = null
    ) : LearningProposalWorkflowResult
}

fun interface LearningProposalBatchWriter {
    fun insertProposedBatch(
        records: List<MemoryRecord>,
        actorPrincipalId: String
    ): MemoryRepositoryResult<Unit>
}

class MemoryRepositoryLearningProposalBatchWriter(
    private val repository: MemoryRepository
) : LearningProposalBatchWriter, LearningProposalOutboxBatchWriter {
    override fun insertProposedBatch(
        records: List<MemoryRecord>,
        actorPrincipalId: String
    ): MemoryRepositoryResult<Unit> = repository.insertProposedBatch(records, actorPrincipalId)

    override fun insertProposedBatchWithOutbox(
        records: List<MemoryRecord>,
        outboxItem: MemoryOutboxItem,
        actorPrincipalId: String
    ): MemoryRepositoryResult<Unit> {
        val store = repository as? MemoryOutboxStore
            ?: return MemoryRepositoryResult.Failure(MemoryRepositoryFailure.STORAGE_ERROR)
        return store.insertProposedBatchWithOutbox(records, outboxItem, actorPrincipalId)
    }
}

interface LearningProposalOutboxBatchWriter {
    fun insertProposedBatchWithOutbox(
        records: List<MemoryRecord>,
        outboxItem: MemoryOutboxItem,
        actorPrincipalId: String
    ): MemoryRepositoryResult<Unit>
}

fun interface LearningJournalReceiptWriter {
    fun append(event: LearningJournalEvent, expectedTaskId: String): LearningJournalStoreResult<LearningJournalEntry>
}

class EncryptedLearningJournalReceiptWriter(
    private val store: EncryptedLearningJournalStore
) : LearningJournalReceiptWriter {
    override fun append(event: LearningJournalEvent, expectedTaskId: String): LearningJournalStoreResult<LearningJournalEntry> =
        store.append(event, expectedTaskId)
}

/**
 * Coordinates Creator review, evidence mapping, encrypted proposal persistence and a journal receipt.
 *
 * The two stores cannot share a SQLite transaction. If proposal insertion succeeds but journal
 * append fails, this returns PersistedJournalPending with a stable event ID. Retrying the same
 * review safely replays the memory batch and appends the exact same journal event.
 *
 * The supplied consumed-review set is a local snapshot, not durable replay protection. Production
 * must consume review IDs, enforce authenticated identity, persist proposals and audit atomically
 * on a trusted backend. This workflow never activates memory or authorizes external actions.
 */
class LearningMemoryProposalWorkflow(
    private val writer: LearningProposalBatchWriter,
    private val journalWriter: LearningJournalReceiptWriter,
    private val outboxDispatcher: LearningMemoryProposalOutboxDispatcher? = null
) {
    fun approveAndPersist(
        request: CreatorLearningReviewRequest,
        consumedReviewIds: Set<String>,
        claims: List<EvidenceClaim>,
        sources: List<ResearchSource>,
        creatorPrincipalId: String,
        journalEntryId: String,
        createdAt: Instant
    ): LearningProposalWorkflowResult {
        if (journalEntryId.isBlank()) {
            return LearningProposalWorkflowResult.Rejected(LearningProposalWorkflowDenial.JOURNAL_ENTRY_INVALID)
        }
        val review = CreatorLearningReviewPolicy.evaluate(request, consumedReviewIds, createdAt)
        if (review.denial != null) {
            return LearningProposalWorkflowResult.Rejected(
                LearningProposalWorkflowDenial.REVIEW_REJECTED,
                reviewDenial = review.denial
            )
        }
        if (review.outcome == CreatorReviewOutcome.REJECTED) {
            return LearningProposalWorkflowResult.Rejected(LearningProposalWorkflowDenial.REVIEW_REJECTED)
        }

        val mapped = LearningMemoryProposalMapper.mapApprovedClaims(
            request, review, claims, sources, creatorPrincipalId, createdAt
        )
        mapped.denial?.let {
            return LearningProposalWorkflowResult.Rejected(
                LearningProposalWorkflowDenial.MAPPING_REJECTED,
                mappingDenial = it
            )
        }
        if (mapped.records.isEmpty()) {
            return LearningProposalWorkflowResult.Rejected(LearningProposalWorkflowDenial.EMPTY_PROPOSAL_SET)
        }

        val receipt = LearningJournalEvent(
            id = stableReceiptEventId(request.reviewId),
            entryId = journalEntryId,
            type = LearningJournalEventType.MEMORY_PROPOSAL_PERSISTED,
            occurredAt = request.reviewedAt,
            actor = creatorPrincipalId,
            safeDetail = "PROPOSAL_BATCH_PERSISTED"
        )

        val outboxWriter = writer as? LearningProposalOutboxBatchWriter
        if (outboxWriter != null && outboxDispatcher != null) {
            val envelope = LearningJournalReceiptEnvelopeCodec.encode(
                LearningJournalReceiptEnvelope(request.task.id, receipt)
            )
            val item = MemoryOutboxItem(receipt.id, creatorPrincipalId, envelope, createdAt)
            when (val persisted = outboxWriter.insertProposedBatchWithOutbox(mapped.records, item, creatorPrincipalId)) {
                is MemoryRepositoryResult.Failure ->
                    return LearningProposalWorkflowResult.Rejected(
                        LearningProposalWorkflowDenial.PERSISTENCE_REJECTED,
                        persistenceFailure = persisted.reason
                    )
                is MemoryRepositoryResult.Success -> Unit
            }
            return when (val delivered = outboxDispatcher.dispatch(item, creatorPrincipalId)) {
                is LearningJournalStoreResult.Success ->
                    LearningProposalWorkflowResult.Persisted(mapped.records, delivered.value)
                is LearningJournalStoreResult.Failure ->
                    LearningProposalWorkflowResult.PersistedJournalPending(mapped.records, receipt, delivered.reason)
            }
        }

        when (val persisted = writer.insertProposedBatch(mapped.records, creatorPrincipalId)) {
            is MemoryRepositoryResult.Failure ->
                return LearningProposalWorkflowResult.Rejected(
                    LearningProposalWorkflowDenial.PERSISTENCE_REJECTED,
                    persistenceFailure = persisted.reason
                )
            is MemoryRepositoryResult.Success -> Unit
        }
        return when (val journal = journalWriter.append(receipt, request.task.id)) {
            is LearningJournalStoreResult.Success ->
                LearningProposalWorkflowResult.Persisted(mapped.records, journal.value)
            is LearningJournalStoreResult.Failure ->
                LearningProposalWorkflowResult.PersistedJournalPending(mapped.records, receipt, journal.reason)
        }
    }

    private fun stableReceiptEventId(reviewId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(reviewId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "proposal-receipt-$digest"
    }
}
