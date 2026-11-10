package com.javasultra.app.core.learning

import com.javasultra.app.core.memory.MemoryRecord
import com.javasultra.app.core.memory.MemoryRepository
import com.javasultra.app.core.memory.MemoryRepositoryFailure
import com.javasultra.app.core.memory.MemoryRepositoryResult
import java.time.Instant

enum class LearningProposalWorkflowDenial {
    REVIEW_REJECTED,
    MAPPING_REJECTED,
    EMPTY_PROPOSAL_SET,
    PERSISTENCE_REJECTED
}

sealed interface LearningProposalWorkflowResult {
    data class Persisted(val proposedRecords: List<MemoryRecord>) : LearningProposalWorkflowResult
    data class Rejected(
        val reason: LearningProposalWorkflowDenial,
        val reviewDenial: CreatorReviewDenial? = null,
        val mappingDenial: LearningProposalDenial? = null,
        val persistenceFailure: MemoryRepositoryFailure? = null
    ) : LearningProposalWorkflowResult
}

/** Narrow port so orchestration can be tested without Android storage. */
fun interface LearningProposalBatchWriter {
    fun insertProposedBatch(
        records: List<MemoryRecord>,
        actorPrincipalId: String
    ): MemoryRepositoryResult<Unit>
}

/** Production adapter; encrypted repository insertion remains the persistence boundary. */
class MemoryRepositoryLearningProposalBatchWriter(
    private val repository: MemoryRepository
) : LearningProposalBatchWriter {
    override fun insertProposedBatch(
        records: List<MemoryRecord>,
        actorPrincipalId: String
    ): MemoryRepositoryResult<Unit> = repository.insertProposedBatch(records, actorPrincipalId)
}

/**
 * Coordinates Creator review, evidence-to-proposal mapping and one atomic encrypted batch.
 *
 * The supplied consumed-review set is a local snapshot, not durable replay protection.
 * Production must derive principal ids from a trusted session and atomically consume the
 * review id and persist proposals on a trusted backend. This client workflow never activates
 * memory or authorizes external actions.
 */
class LearningMemoryProposalWorkflow(
    private val writer: LearningProposalBatchWriter
) {
    fun approveAndPersist(
        request: CreatorLearningReviewRequest,
        consumedReviewIds: Set<String>,
        claims: List<EvidenceClaim>,
        sources: List<ResearchSource>,
        creatorPrincipalId: String,
        createdAt: Instant
    ): LearningProposalWorkflowResult {
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
            return LearningProposalWorkflowResult.Rejected(
                LearningProposalWorkflowDenial.EMPTY_PROPOSAL_SET
            )
        }
        return when (val persisted = writer.insertProposedBatch(mapped.records, creatorPrincipalId)) {
            is MemoryRepositoryResult.Success ->
                LearningProposalWorkflowResult.Persisted(mapped.records)
            is MemoryRepositoryResult.Failure ->
                LearningProposalWorkflowResult.Rejected(
                    LearningProposalWorkflowDenial.PERSISTENCE_REJECTED,
                    persistenceFailure = persisted.reason
                )
        }
    }
}
