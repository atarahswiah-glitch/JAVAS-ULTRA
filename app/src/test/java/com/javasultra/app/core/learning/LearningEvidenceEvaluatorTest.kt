package com.javasultra.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LearningEvidenceEvaluatorTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private fun task(state: LearningTaskState = LearningTaskState.EVALUATING) = LearningTask(
        "task-1", "Research a public science topic", LearningDomain.SCIENCE, now,
        state, false, 10, 3600, true
    )
    private fun source(id: String, publisher: String, kind: EvidenceSourceKind = EvidenceSourceKind.OFFICIAL_DOCUMENTATION,
                       retrievedAt: Instant = now) = ResearchSource(
        id, kind, "Source $id", publisher, "https://$id.example.org/page", null, retrievedAt, 0.8
    )
    private fun claim(ids: Set<String>, contradictions: Set<String> = emptySet()) = EvidenceClaim(
        "claim-1", "A verifiable statement", ids, ids - contradictions, contradictions,
        now, null, ClaimStatus.CANDIDATE, 0.7
    )

    @Test fun independentPublicSourcesCanReachCreatorReviewOnly() {
        val decision = LearningEvidenceEvaluator.evaluate(
            task(), listOf(source("a", "Publisher A"), source("b", "Publisher B")),
            listOf(claim(setOf("a", "b"))), now
        )
        assertTrue(decision.readyForCreatorReview)
        assertEquals(setOf("claim-1"), decision.reviewedClaimIds)
    }

    @Test fun modelOutputCannotCountAsEvidence() {
        val decision = LearningEvidenceEvaluator.evaluate(
            task(), listOf(source("a", "Publisher A", EvidenceSourceKind.MODEL_OUTPUT), source("b", "Publisher B")),
            listOf(claim(setOf("a", "b"))), now
        )
        assertFalse(decision.readyForCreatorReview)
        assertEquals(EvidenceEvaluationDenial.MODEL_OUTPUT_CANNOT_BE_EVIDENCE, decision.denial)
    }

    @Test fun contradictoryEvidenceIsNotPromotedToReviewReady() {
        val decision = LearningEvidenceEvaluator.evaluate(
            task(), listOf(source("a", "Publisher A"), source("b", "Publisher B")),
            listOf(claim(setOf("a", "b"), setOf("b"))), now
        )
        assertEquals(EvidenceEvaluationDenial.CONTRADICTIONS_REQUIRE_REVIEW, decision.denial)
    }

    @Test fun staleEvidenceIsRejected() {
        val old = now.minusSeconds(366L * 24 * 60 * 60)
        val decision = LearningEvidenceEvaluator.evaluate(
            task(), listOf(source("a", "Publisher A", retrievedAt = old), source("b", "Publisher B")),
            listOf(claim(setOf("a", "b"))), now
        )
        assertEquals(EvidenceEvaluationDenial.STALE_OR_FUTURE_SOURCE, decision.denial)
    }

    @Test fun duplicatedPublisherDoesNotCountAsIndependentEvidence() {
        val decision = LearningEvidenceEvaluator.evaluate(
            task(), listOf(source("a", "Same Publisher"), source("b", "same publisher")),
            listOf(claim(setOf("a", "b"))), now
        )
        assertEquals(EvidenceEvaluationDenial.INSUFFICIENT_INDEPENDENT_SOURCES, decision.denial)
    }

    @Test fun duplicateSourceIdentifiersAreRejectedInsteadOfSilentlyOverwriting() {
        val duplicate = source("a", "Publisher B")
        val decision = LearningEvidenceEvaluator.evaluate(
            task(), listOf(source("a", "Publisher A"), duplicate, source("b", "Publisher C")),
            listOf(claim(setOf("a", "b"))), now
        )
        assertEquals(EvidenceEvaluationDenial.DUPLICATE_SOURCE_ID, decision.denial)
    }

    @Test fun malformedSourceMetadataIsRejected() {
        val invalid = source("a", "Publisher A").copy(trustScore = 1.5)
        val decision = LearningEvidenceEvaluator.evaluate(
            task(), listOf(invalid, source("b", "Publisher B")),
            listOf(claim(setOf("a", "b"))), now
        )
        assertEquals(EvidenceEvaluationDenial.INVALID_SOURCE, decision.denial)
    }

    @Test fun taskMustBeInEvaluationState() {
        val decision = LearningEvidenceEvaluator.evaluate(
            task(LearningTaskState.APPROVED),
            listOf(source("a", "Publisher A"), source("b", "Publisher B")),
            listOf(claim(setOf("a", "b"))), now
        )
        assertEquals(EvidenceEvaluationDenial.INVALID_TASK, decision.denial)
    }

    @Test fun missingClaimsAreRejected() {
        val decision = LearningEvidenceEvaluator.evaluate(task(), emptyList(), emptyList(), now)
        assertEquals(EvidenceEvaluationDenial.NO_CLAIMS, decision.denial)
    }
}
