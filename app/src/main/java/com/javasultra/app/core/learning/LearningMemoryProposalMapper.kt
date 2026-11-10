package com.javasultra.app.core.learning

import com.javasultra.app.core.memory.MemoryId
import com.javasultra.app.core.memory.MemoryKind
import com.javasultra.app.core.memory.MemoryLifecycle
import com.javasultra.app.core.memory.MemoryProvenance
import com.javasultra.app.core.memory.MemoryRecord
import com.javasultra.app.core.memory.MemorySensitivity
import com.javasultra.app.core.memory.MemorySourceType
import com.javasultra.app.core.memory.MemoryVerification
import java.time.Instant

enum class LearningProposalDenial {
    INVALID_REVIEW,
    TASK_MISMATCH,
    CLAIM_SET_MISMATCH,
    DUPLICATE_SOURCE_ID,
    INVALID_CLAIM,
    INSUFFICIENT_PROVENANCE
}

data class LearningProposalResult(
    val records: List<MemoryRecord> = emptyList(),
    val denial: LearningProposalDenial? = null
)

/**
 * Converts only Creator-approved, supported research claims into PROPOSED memory records.
 *
 * This mapper does not persist, activate, complete tasks, or establish trusted identity.
 * Callers must pass a result produced by CreatorLearningReviewPolicy and must still use
 * the memory repository's owner-bound insertProposed operation.
 */
object LearningMemoryProposalMapper {
    private val sha256Pattern = Regex("^[a-fA-F0-9]{64}$")

    fun mapApprovedClaims(
        request: CreatorLearningReviewRequest,
        review: CreatorLearningReviewResult,
        claims: List<EvidenceClaim>,
        sources: List<ResearchSource>,
        creatorPrincipalId: String,
        createdAt: Instant
    ): LearningProposalResult {
        fun denied(reason: LearningProposalDenial) = LearningProposalResult(denial = reason)

        if (review.outcome != CreatorReviewOutcome.APPROVED_FOR_PROPOSAL_ONLY ||
            review.denial != null ||
            review.consumedReviewId != request.reviewId ||
            request.decision != CreatorLearningDecision.APPROVE ||
            !request.stepUpAuthenticated ||
            request.creatorPrincipalId != creatorPrincipalId ||
            request.authenticatedReviewerPrincipalId != creatorPrincipalId ||
            request.reviewId.isBlank() ||
            !sha256Pattern.matches(request.expectedEvidenceDigestSha256) ||
            !request.expectedEvidenceDigestSha256.equals(request.submittedEvidenceDigestSha256, true)
        ) return denied(LearningProposalDenial.INVALID_REVIEW)

        if (request.task.id != review.taskId ||
            request.task.state != LearningTaskState.READY_FOR_REVIEW ||
            LearningTaskPolicy.validate(request.task) != null
        ) return denied(LearningProposalDenial.TASK_MISMATCH)

        val claimIds = claims.map { it.id }
        if (claimIds.size != claimIds.toSet().size ||
            claimIds.toSet() != request.claimIds ||
            claimIds.toSet() != review.approvedClaimIds
        ) return denied(LearningProposalDenial.CLAIM_SET_MISMATCH)

        val sourceIds = sources.map { it.id }
        if (sourceIds.size != sourceIds.toSet().size) {
            return denied(LearningProposalDenial.DUPLICATE_SOURCE_ID)
        }
        val sourceMap = sources.associateBy { it.id }
        val records = mutableListOf<MemoryRecord>()

        for (claim in claims) {
            if (claim.validate(sourceMap.keys).isNotEmpty() ||
                claim.status != ClaimStatus.SUPPORTED ||
                claim.reviewedAt == null ||
                claim.reviewedAt.isAfter(createdAt) ||
                claim.contradictingSourceIds.isNotEmpty()
            ) return denied(LearningProposalDenial.INVALID_CLAIM)

            val supporting = claim.supportingSourceIds.mapNotNull(sourceMap::get)
            val publishers = supporting.mapNotNull {
                it.publisher?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
            }.toSet()
            if (supporting.size < 2 || publishers.size < 2 ||
                supporting.any { it.kind == EvidenceSourceKind.MODEL_OUTPUT || it.retrievedAt.isAfter(createdAt) }
            ) return denied(LearningProposalDenial.INSUFFICIENT_PROVENANCE)

            val sourceReference = "javas-learning:${request.task.id}:${claim.id}:" +
                claim.supportingSourceIds.sorted().joinToString(",")
            val publisherSummary = publishers.sorted().joinToString(",")
            val record = MemoryRecord(
                id = MemoryId("learning-${request.task.id}-${claim.id}"),
                ownerPrincipalId = creatorPrincipalId,
                kind = MemoryKind.SEMANTIC,
                content = claim.statement,
                provenance = MemoryProvenance(
                    sourceType = MemorySourceType.RESEARCH_EVIDENCE,
                    sourceReference = sourceReference,
                    observedAt = claim.reviewedAt,
                    publisherOrActor = publisherSummary,
                    notes = "task=${request.task.id};review=${request.reviewId};evidence_sha256=${request.submittedEvidenceDigestSha256.lowercase()}"
                ),
                createdAt = createdAt,
                updatedAt = createdAt,
                confidence = claim.confidence,
                verification = MemoryVerification.CORROBORATED,
                sensitivity = MemorySensitivity.CREATOR_PRIVATE,
                lifecycle = MemoryLifecycle.PROPOSED,
                tags = setOf("learning", request.task.domain.name.lowercase())
            )
            if (record.validate().isNotEmpty()) return denied(LearningProposalDenial.INVALID_CLAIM)
            records += record
        }
        return LearningProposalResult(records = records)
    }
}
