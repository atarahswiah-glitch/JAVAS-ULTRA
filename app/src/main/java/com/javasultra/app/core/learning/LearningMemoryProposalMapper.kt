package com.javasultra.app.core.learning

import com.javasultra.app.core.memory.MemoryId
import com.javasultra.app.core.memory.MemoryKind
import com.javasultra.app.core.memory.MemoryLifecycle
import com.javasultra.app.core.memory.MemoryProvenance
import com.javasultra.app.core.memory.MemoryRecord
import com.javasultra.app.core.memory.MemoryRetentionAction
import com.javasultra.app.core.memory.MemoryRetentionCandidate
import com.javasultra.app.core.memory.MemoryRetentionProposalFactory
import com.javasultra.app.core.memory.MemoryRetentionType
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
    INSUFFICIENT_PROVENANCE,
    RETENTION_POLICY_REJECTED
}

data class LearningProposalResult(
    val records: List<MemoryRecord> = emptyList(),
    val denial: LearningProposalDenial? = null
)

/**
 * Converts only Creator-approved, supported research claims into PROPOSED memory records.
 *
 * Retention is evaluated by the shared MemoryRetentionPolicy adapter before a record
 * can leave this mapper. This mapper does not persist, activate, complete tasks, or
 * establish trusted identity. Callers must still use owner-bound encrypted repository
 * insertion, and the memory activation policy remains a separate gate.
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
                claim.createdAt.isAfter(createdAt) ||
                claim.reviewedAt?.isAfter(createdAt) == true ||
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
                    observedAt = supporting.maxOf { it.retrievedAt },
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

            // Creator approval authorizes a proposal only, not activation. Treat it as
            // explicit permission to prepare a durable candidate and retain source provenance.
            val retention = MemoryRetentionProposalFactory.prepare(
                MemoryRetentionCandidate(
                    type = MemoryRetentionType.RESEARCH_FINDING,
                    futureUseLikelihood = 0.5,
                    uniqueness = 0.5,
                    reResearchCost = 0.5,
                    sourceVerified = true,
                    sourceReferenceAvailable = true,
                    explicitlyRequestedToRetain = true
                ),
                record
            )
            if (retention.retention.action != MemoryRetentionAction.STORE_KNOWLEDGE ||
                retention.recordToPropose == null
            ) return denied(LearningProposalDenial.RETENTION_POLICY_REJECTED)
            records += retention.recordToPropose
        }
        return LearningProposalResult(records = records)
    }
}
