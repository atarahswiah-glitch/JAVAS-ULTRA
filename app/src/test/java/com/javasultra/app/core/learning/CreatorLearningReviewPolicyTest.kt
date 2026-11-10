package com.javasultra.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CreatorLearningReviewPolicyTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private val digest = "a".repeat(64)

    private fun request(
        decision: CreatorLearningDecision = CreatorLearningDecision.APPROVE,
        reviewer: String = "creator-1",
        state: LearningTaskState = LearningTaskState.READY_FOR_REVIEW,
        expectedDigest: String = digest,
        submittedDigest: String = digest,
        reviewedAt: Instant = now,
        stepUp: Boolean = true,
        claims: Set<String> = setOf("claim-b", "claim-a")
    ) = CreatorLearningReviewRequest(
        task = LearningTask(
            "task-1", "Review research", LearningDomain.SCIENCE, now,
            state, true, 10, 3600, true
        ),
        creatorPrincipalId = "creator-1",
        authenticatedReviewerPrincipalId = reviewer,
        reviewId = "review-1",
        decision = decision,
        expectedEvidenceDigestSha256 = expectedDigest,
        submittedEvidenceDigestSha256 = submittedDigest,
        claimIds = claims,
        reviewedAt = reviewedAt,
        stepUpAuthenticated = stepUp
    )

    @Test fun CreatorApprovalAuthorizesProposalOnlyAndSortsClaimIds() {
        val result = CreatorLearningReviewPolicy.evaluate(request(), emptySet(), now)
        assertEquals(CreatorReviewOutcome.APPROVED_FOR_PROPOSAL_ONLY, result.outcome)
        assertEquals(listOf("claim-a", "claim-b"), result.approvedClaimIds.toList())
        assertEquals("review-1", result.consumedReviewId)
        assertNull(result.denial)
    }

    @Test fun rejectionDoesNotReturnClaimsForPromotion() {
        val result = CreatorLearningReviewPolicy.evaluate(
            request(decision = CreatorLearningDecision.REJECT), emptySet(), now
        )
        assertEquals(CreatorReviewOutcome.REJECTED, result.outcome)
        assertTrue(result.approvedClaimIds.isEmpty())
    }

    @Test fun nonCreatorCannotReview() {
        val result = CreatorLearningReviewPolicy.evaluate(
            request(reviewer = "ordinary-member"), emptySet(), now
        )
        assertEquals(CreatorReviewDenial.INVALID_IDENTITY, result.denial)
    }

    @Test fun reviewRequiresFreshStepUpAuthentication() {
        val result = CreatorLearningReviewPolicy.evaluate(
            request(stepUp = false), emptySet(), now
        )
        assertEquals(CreatorReviewDenial.STEP_UP_REQUIRED, result.denial)
    }

    @Test fun evidenceDigestMustMatchExactReviewedEvidence() {
        val result = CreatorLearningReviewPolicy.evaluate(
            request(submittedDigest = "b".repeat(64)), emptySet(), now
        )
        assertEquals(CreatorReviewDenial.EVIDENCE_DIGEST_MISMATCH, result.denial)
    }

    @Test fun malformedEvidenceDigestIsRejected() {
        val result = CreatorLearningReviewPolicy.evaluate(
            request(expectedDigest = "not-a-digest"), emptySet(), now
        )
        assertEquals(CreatorReviewDenial.INVALID_EVIDENCE_DIGEST, result.denial)
    }

    @Test fun consumedReviewCannotBeReplayed() {
        val result = CreatorLearningReviewPolicy.evaluate(
            request(), setOf("review-1"), now
        )
        assertEquals(CreatorReviewDenial.REVIEW_ALREADY_CONSUMED, result.denial)
    }

    @Test fun reviewMustBeCurrentAndWithinFiveMinutes() {
        val old = CreatorLearningReviewPolicy.evaluate(
            request(reviewedAt = now.minus(Duration.ofMinutes(6))), emptySet(), now
        )
        val future = CreatorLearningReviewPolicy.evaluate(
            request(reviewedAt = now.plusSeconds(1)), emptySet(), now
        )
        assertEquals(CreatorReviewDenial.INVALID_REVIEW_TIME, old.denial)
        assertEquals(CreatorReviewDenial.INVALID_REVIEW_TIME, future.denial)
    }

    @Test fun onlyEvidenceReadyTasksCanBeReviewed() {
        val result = CreatorLearningReviewPolicy.evaluate(
            request(state = LearningTaskState.EVALUATING), emptySet(), now
        )
        assertEquals(CreatorReviewDenial.INVALID_TASK_STATE, result.denial)
    }

    @Test fun emptyClaimSetIsRejected() {
        val result = CreatorLearningReviewPolicy.evaluate(
            request(claims = emptySet()), emptySet(), now
        )
        assertEquals(CreatorReviewDenial.INVALID_CLAIM_SET, result.denial)
    }
}
