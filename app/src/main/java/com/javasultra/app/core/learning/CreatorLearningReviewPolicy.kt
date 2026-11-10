package com.javasultra.app.core.learning

import java.time.Duration
import java.time.Instant

enum class CreatorLearningDecision { APPROVE, REJECT }
enum class CreatorReviewOutcome { APPROVED_FOR_PROPOSAL_ONLY, REJECTED }
enum class CreatorReviewDenial {
    INVALID_TASK_STATE,
    INVALID_IDENTITY,
    STEP_UP_REQUIRED,
    INVALID_REVIEW_ID,
    REVIEW_ALREADY_CONSUMED,
    INVALID_EVIDENCE_DIGEST,
    EVIDENCE_DIGEST_MISMATCH,
    INVALID_CLAIM_SET,
    INVALID_REVIEW_TIME
}

data class CreatorLearningReviewRequest(
    val task: LearningTask,
    val creatorPrincipalId: String,
    val authenticatedReviewerPrincipalId: String,
    val reviewId: String,
    val decision: CreatorLearningDecision,
    val expectedEvidenceDigestSha256: String,
    val submittedEvidenceDigestSha256: String,
    val claimIds: Set<String>,
    val reviewedAt: Instant,
    val stepUpAuthenticated: Boolean
)

data class CreatorLearningReviewResult(
    val outcome: CreatorReviewOutcome? = null,
    val denial: CreatorReviewDenial? = null,
    val taskId: String? = null,
    val approvedClaimIds: Set<String> = emptySet(),
    val consumedReviewId: String? = null
)

/**
 * Local domain preflight only. Production callers must derive both principal IDs
 * from a trusted authenticated session and repeat this decision transactionally
 * on a trusted server with replay protection.
 *
 * Approval authorizes proposal creation only; this policy never activates memory.
 */
object CreatorLearningReviewPolicy {
    private val digestPattern = Regex("^[a-fA-F0-9]{64}$")

    fun evaluate(
        request: CreatorLearningReviewRequest,
        consumedReviewIds: Set<String>,
        now: Instant,
        maxReviewAge: Duration = Duration.ofMinutes(5)
    ): CreatorLearningReviewResult {
        fun deny(reason: CreatorReviewDenial) =
            CreatorLearningReviewResult(denial = reason)

        if (request.task.state != LearningTaskState.READY_FOR_REVIEW) {
            return deny(CreatorReviewDenial.INVALID_TASK_STATE)
        }
        if (request.creatorPrincipalId.isBlank() ||
            request.authenticatedReviewerPrincipalId.isBlank() ||
            request.authenticatedReviewerPrincipalId != request.creatorPrincipalId
        ) return deny(CreatorReviewDenial.INVALID_IDENTITY)

        if (!request.stepUpAuthenticated) return deny(CreatorReviewDenial.STEP_UP_REQUIRED)
        if (request.reviewId.isBlank()) return deny(CreatorReviewDenial.INVALID_REVIEW_ID)
        if (request.reviewId in consumedReviewIds) {
            return deny(CreatorReviewDenial.REVIEW_ALREADY_CONSUMED)
        }
        if (!digestPattern.matches(request.expectedEvidenceDigestSha256) ||
            !digestPattern.matches(request.submittedEvidenceDigestSha256)
        ) return deny(CreatorReviewDenial.INVALID_EVIDENCE_DIGEST)
        if (!request.expectedEvidenceDigestSha256.equals(
                request.submittedEvidenceDigestSha256, ignoreCase = true
            )
        ) return deny(CreatorReviewDenial.EVIDENCE_DIGEST_MISMATCH)
        if (request.claimIds.isEmpty() || request.claimIds.any { it.isBlank() }) {
            return deny(CreatorReviewDenial.INVALID_CLAIM_SET)
        }
        if (maxReviewAge.isNegative || maxReviewAge.isZero ||
            request.reviewedAt.isAfter(now) ||
            Duration.between(request.reviewedAt, now) > maxReviewAge
        ) return deny(CreatorReviewDenial.INVALID_REVIEW_TIME)

        return CreatorLearningReviewResult(
            outcome = if (request.decision == CreatorLearningDecision.APPROVE) {
                CreatorReviewOutcome.APPROVED_FOR_PROPOSAL_ONLY
            } else {
                CreatorReviewOutcome.REJECTED
            },
            taskId = request.task.id,
            approvedClaimIds = if (request.decision == CreatorLearningDecision.APPROVE) {
                request.claimIds.toSortedSet()
            } else emptySet(),
            consumedReviewId = request.reviewId
        )
    }
}
