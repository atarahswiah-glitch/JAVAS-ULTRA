package com.javasultra.app.core.learning

import com.javasultra.app.core.memory.MemoryLifecycle
import com.javasultra.app.core.memory.MemorySensitivity
import com.javasultra.app.core.memory.MemorySourceType
import com.javasultra.app.core.memory.MemoryVerification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LearningMemoryProposalMapperTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private val digest = "c".repeat(64)
    private val creator = "creator-1"

    private fun request() = CreatorLearningReviewRequest(
        task = LearningTask("task-1", "Research a public science topic", LearningDomain.SCIENCE,
            now.minusSeconds(60), LearningTaskState.READY_FOR_REVIEW, true, 10, 3600, true),
        creatorPrincipalId = creator,
        authenticatedReviewerPrincipalId = creator,
        reviewId = "review-1",
        decision = CreatorLearningDecision.APPROVE,
        expectedEvidenceDigestSha256 = digest,
        submittedEvidenceDigestSha256 = digest,
        claimIds = setOf("claim-1"),
        reviewedAt = now.minusSeconds(5),
        stepUpAuthenticated = true
    )

    private fun sources() = listOf(
        ResearchSource("source-a", EvidenceSourceKind.OFFICIAL_DOCUMENTATION, "Source A",
            "Publisher A", "https://a.example.org", now.minusSeconds(120), now.minusSeconds(30), 0.9),
        ResearchSource("source-b", EvidenceSourceKind.ACADEMIC_REVIEW, "Source B",
            "Publisher B", "https://b.example.org", now.minusSeconds(120), now.minusSeconds(20), 0.9)
    )

    private fun claim(status: ClaimStatus = ClaimStatus.SUPPORTED) = EvidenceClaim(
        "claim-1", "A corroborated public research statement", setOf("source-a", "source-b"),
        setOf("source-a", "source-b"), emptySet(), now.minusSeconds(60), null, status, 0.85
    )

    private fun approvedReview(req: CreatorLearningReviewRequest = request()) =
        CreatorLearningReviewPolicy.evaluate(req, emptySet(), now)

    @Test fun approvedClaimBecomesCreatorPrivateProposedMemoryOnly() {
        val req = request()
        val result = LearningMemoryProposalMapper.mapApprovedClaims(
            req, approvedReview(req), listOf(claim()), sources(), creator, now
        )
        assertNull(result.denial)
        assertEquals(1, result.records.size)
        val record = result.records.single()
        assertEquals(creator, record.ownerPrincipalId)
        assertEquals(MemoryLifecycle.PROPOSED, record.lifecycle)
        assertEquals(MemorySensitivity.CREATOR_PRIVATE, record.sensitivity)
        assertEquals(MemoryVerification.CORROBORATED, record.verification)
        assertEquals(MemorySourceType.RESEARCH_EVIDENCE, record.provenance.sourceType)
        assertTrue(record.provenance.sourceReference!!.contains("source-a,source-b"))
        assertTrue(record.provenance.notes!!.contains("review=review-1"))
    }

    @Test fun rejectedReviewCannotCreateMemoryProposal() {
        val req = request().copy(decision = CreatorLearningDecision.REJECT)
        val review = CreatorLearningReviewPolicy.evaluate(req, emptySet(), now)
        val result = LearningMemoryProposalMapper.mapApprovedClaims(
            req, review, listOf(claim()), sources(), creator, now
        )
        assertEquals(LearningProposalDenial.INVALID_REVIEW, result.denial)
        assertTrue(result.records.isEmpty())
    }

    @Test fun claimsMustExactlyMatchCreatorApprovedSet() {
        val req = request()
        val result = LearningMemoryProposalMapper.mapApprovedClaims(
            req, approvedReview(req), emptyList(), sources(), creator, now
        )
        assertEquals(LearningProposalDenial.CLAIM_SET_MISMATCH, result.denial)
    }

    @Test fun nonCreatorOwnerCannotReceiveCreatorProposal() {
        val req = request()
        val result = LearningMemoryProposalMapper.mapApprovedClaims(
            req, approvedReview(req), listOf(claim()), sources(), "other-principal", now
        )
        assertEquals(LearningProposalDenial.INVALID_REVIEW, result.denial)
    }

    @Test fun unsupportedOrDuplicateSourceEvidenceIsRejected() {
        val req = request()
        val unsupported = LearningMemoryProposalMapper.mapApprovedClaims(
            req, approvedReview(req), listOf(claim(ClaimStatus.CANDIDATE)), sources(), creator, now
        )
        assertEquals(LearningProposalDenial.INVALID_CLAIM, unsupported.denial)

        val duplicated = LearningMemoryProposalMapper.mapApprovedClaims(
            req, approvedReview(req), listOf(claim()), sources() + sources().first(), creator, now
        )
        assertEquals(LearningProposalDenial.DUPLICATE_SOURCE_ID, duplicated.denial)
    }

    @Test fun modelOutputCannotBeUsedAsMemoryEvidence() {
        val req = request()
        val modelSources = sources().map {
            if (it.id == "source-a") it.copy(kind = EvidenceSourceKind.MODEL_OUTPUT) else it
        }
        val result = LearningMemoryProposalMapper.mapApprovedClaims(
            req, approvedReview(req), listOf(claim()), modelSources, creator, now
        )
        assertEquals(LearningProposalDenial.INSUFFICIENT_PROVENANCE, result.denial)
        assertFalse(result.records.isNotEmpty())
    }
}
