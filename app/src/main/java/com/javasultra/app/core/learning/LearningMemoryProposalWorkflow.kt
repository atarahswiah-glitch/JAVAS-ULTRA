package com.javasultra.app.core.learning

import com.javasultra.app.core.memory.MemoryRecord
import com.javasultra.app.core.memory.MemoryRepository
import com.javasultra.app.core.memory.MemoryRepositoryFailure
import com.javasultra.app.core.memory.MemoryRepositoryResult

enum class LearningProposalWorkflowDenial {
    MAPPING_REJECTED,
    EMPTY_PROPOSAL_SET,
    PERSISTENCE_REJECTED
}

sealed interface LearningProposalWorkflowResult {
    data class Persisted(val proposedRecords: List<MemoryRecord>) : LearningProposalWorkflowResult
    data class Rejected(
        val reason: LearningProposalWorkflowDenial,
        val mappingDenial: LearningProposalDenial? = null,
        val persistenceFailure: MemoryRepositoryFailure? = null
    ) : LearningProposalWorkflowResult
}

/** Narrow port so the orchestration can be tested without Android storage. */
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
 * First persistence step in the learning-to-memory path.
 *
 * It accepts only the mapper's Creator-approved proposals and writes the complete proposal
 * set through one atomic repository batch. It never activates memory, completes a learning
 * task, or authorizes an external action. A failed batch is reported as failed, not partial.
 */
class LearningMemoryProposalWorkflow(
    private val writer: LearningProposalBatchWriter
) {
    fun approveAndPersist(
        request: CreatorLearningReviewRequest,
        review: CreatorLearningReviewResult,
        claims: List<EvidenceClaim>,
        sources: List<ResearchSource>,
        creatorPrincipalId: String,
        createdAt: java.time.Instant
    ): LearningProposalWorkflowResult {
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
