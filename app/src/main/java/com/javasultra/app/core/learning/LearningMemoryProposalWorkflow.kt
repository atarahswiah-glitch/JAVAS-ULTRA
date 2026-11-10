package com.javasultra.app.core.learning

import com.javasultra.app.core.memory.MemoryId
import com.javasultra.app.core.memory.MemoryRecord
import com.javasultra.app.core.memory.MemoryRepositoryFailure
import com.javasultra.app.core.memory.MemoryRepositoryResult
import java.time.Instant

enum class LearningMemoryWorkflowStatus {
    DENIED,
    REVIEW_REJECTED,
    PERSISTED,
    PARTIALLY_PERSISTED,
    PERSISTENCE_FAILED
}

data class LearningMemoryWorkflowResult(
    val status: LearningMemoryWorkflowStatus,
    val reviewDenial: CreatorReviewDenial? = null,
    val proposalDenial: LearningProposalDenial? = null,
    val acceptedProposalIds: List<MemoryId> = emptyList(),
    val failedProposalId: MemoryId? = null,
    val persistenceFailure: MemoryRepositoryFailure? = null
)

/**
 * Coordinates the local learning-review -> proposal -> encrypted-repository boundary.
 *
 * The coordinator never activates memory. Every record remains PROPOSED after insertion.
 * It uses the same deterministic record ids on retry; MemoryRepository implementations
 * must accept exact-content retries and reject same-id/different-content collisions.
 *
 * This is client-side orchestration, not a trusted authorization service. Production must
 * derive principal ids from an authenticated session, persist/consume review ids atomically,
 * and repeat the review decision and owner checks on a trusted backend.
 */
object LearningMemoryProposalWorkflow {
    fun approveAndStore(
        request: CreatorLearningReviewRequest,
        consumedReviewIds: Set<String>,
        claims: List<EvidenceClaim>,
        sources: List<ResearchSource>,
        creatorPrincipalId: String,
        now: Instant,
        insertProposed: (MemoryRecord, String) -> MemoryRepositoryResult<Unit>
    ): LearningMemoryWorkflowResult {
        val review = CreatorLearningReviewPolicy.evaluate(request, consumedReviewIds, now)
        if (review.denial != null) {
            return LearningMemoryWorkflowResult(
                status = LearningMemoryWorkflowStatus.DENIED,
                reviewDenial = review.denial
            )
        }
        if (review.outcome == CreatorReviewOutcome.REJECTED) {
            return LearningMemoryWorkflowResult(status = LearningMemoryWorkflowStatus.REVIEW_REJECTED)
        }

        val proposal = LearningMemoryProposalMapper.mapApprovedClaims(
            request = request,
            review = review,
            claims = claims,
            sources = sources,
            creatorPrincipalId = creatorPrincipalId,
            createdAt = now
        )
        if (proposal.denial != null || proposal.records.isEmpty()) {
            return LearningMemoryWorkflowResult(
                status = LearningMemoryWorkflowStatus.DENIED,
                proposalDenial = proposal.denial ?: LearningProposalDenial.CLAIM_SET_MISMATCH
            )
        }

        val accepted = mutableListOf<MemoryId>()
        for (record in proposal.records) {
            when (val stored = insertProposed(record, creatorPrincipalId)) {
                is MemoryRepositoryResult.Success -> accepted += record.id
                is MemoryRepositoryResult.Failure -> {
                    return LearningMemoryWorkflowResult(
                        status = if (accepted.isEmpty()) {
                            LearningMemoryWorkflowStatus.PERSISTENCE_FAILED
                        } else {
                            LearningMemoryWorkflowStatus.PARTIALLY_PERSISTED
                        },
                        acceptedProposalIds = accepted.toList(),
                        failedProposalId = record.id,
                        persistenceFailure = stored.reason
                    )
                }
            }
        }
        return LearningMemoryWorkflowResult(
            status = LearningMemoryWorkflowStatus.PERSISTED,
            acceptedProposalIds = accepted.toList()
        )
    }
}
