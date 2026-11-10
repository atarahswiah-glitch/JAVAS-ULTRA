package com.javasultra.app.core.learning

import com.javasultra.app.core.memory.MemoryId
import com.javasultra.app.core.memory.MemoryLifecycle
import com.javasultra.app.core.memory.MemoryRepositoryFailure
import com.javasultra.app.core.memory.MemoryRepositoryResult
import com.javasultra.app.core.memory.MemoryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LearningMemoryProposalWorkflowTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private val creator = "creator-1"
    private val digest = "c".repeat(64)

    private fun request(decision: CreatorLearningDecision = CreatorLearningDecision.APPROVE) =
        CreatorLearningReviewRequest(
            task = LearningTask(
                "task-1", "Research a public science topic", LearningDomain.SCIENCE,
                now.minusSeconds(60), LearningTaskState.READY_FOR_REVIEW, true, 10, 3600, true
            ),
            creatorPrincipalId = creator,
            authenticatedReviewerPrincipalId = creator,
            reviewId = "review-1",
            decision = decision,
            expectedEvidenceDigestSha256 = digest,
            submittedEvidenceDigestSha256 = digest,
            claimIds = setOf("claim-1"),
            reviewedAt = now.minusSeconds(5),
            stepUpAuthenticated = true
        )

    private val sources = listOf(
        ResearchSource("source-a", EvidenceSourceKind.OFFICIAL_DOCUMENTATION, "Source A",
            "Publisher A", "https://a.example.org", now.minusSeconds(120), now.minusSeconds(30), 0.9),
        ResearchSource("source-b", EvidenceSourceKind.ACADEMIC_REVIEW, "Source B",
            "Publisher B", "https://b.example.org", now.minusSeconds(120), now.minusSeconds(20), 0.9)
    )

    private val claims = listOf(
        EvidenceClaim("claim-1", "A corroborated public research statement",
            setOf("source-a", "source-b"), setOf("source-a", "source-b"), emptySet(),
            now.minusSeconds(60), null, ClaimStatus.SUPPORTED, 0.85)
    )

    @Test fun approvedReviewPersistsProposalWithoutActivatingIt() {
        val stored = mutableListOf<MemoryRecord>()
        val result = LearningMemoryProposalWorkflow.approveAndStore(
            request(), emptySet(), claims, sources, creator, now
        ) { record, actor ->
            assertEquals(creator, actor)
            stored += record
            MemoryRepositoryResult.Success(Unit)
        }

        assertEquals(LearningMemoryWorkflowStatus.PERSISTED, result.status)
        assertEquals(1, result.acceptedProposalIds.size)
        assertEquals(MemoryLifecycle.PROPOSED, stored.single().lifecycle)
    }

    @Test fun consumedReviewCannotCreateAnotherProposal() {
        var insertCalls = 0
        val result = LearningMemoryProposalWorkflow.approveAndStore(
            request(), setOf("review-1"), claims, sources, creator, now
        ) { _, _ ->
            insertCalls++
            MemoryRepositoryResult.Success(Unit)
        }

        assertEquals(LearningMemoryWorkflowStatus.DENIED, result.status)
        assertEquals(CreatorReviewDenial.REVIEW_ALREADY_CONSUMED, result.reviewDenial)
        assertEquals(0, insertCalls)
    }

    @Test fun rejectedCreatorReviewNeverPersists() {
        var insertCalls = 0
        val result = LearningMemoryProposalWorkflow.approveAndStore(
            request(CreatorLearningDecision.REJECT), emptySet(), claims, sources, creator, now
        ) { _, _ ->
            insertCalls++
            MemoryRepositoryResult.Success(Unit)
        }

        assertEquals(LearningMemoryWorkflowStatus.REVIEW_REJECTED, result.status)
        assertEquals(0, insertCalls)
    }

    @Test fun wrongAuthenticatedPrincipalIsDeniedBeforePersistence() {
        var insertCalls = 0
        val result = LearningMemoryProposalWorkflow.approveAndStore(
            request().copy(authenticatedReviewerPrincipalId = "other-user"),
            emptySet(), claims, sources, creator, now
        ) { _, _ ->
            insertCalls++
            MemoryRepositoryResult.Success(Unit)
        }

        assertEquals(LearningMemoryWorkflowStatus.DENIED, result.status)
        assertEquals(CreatorReviewDenial.INVALID_IDENTITY, result.reviewDenial)
        assertEquals(0, insertCalls)
    }

    @Test fun partialPersistenceReportsAcceptedAndFailedIdsForSafeRetry() {
        val twoClaims = claims + claims.first().copy(
            id = "claim-2", statement = "A second supported fact"
        )
        val req = request().copy(claimIds = setOf("claim-1", "claim-2"))
        var calls = 0
        val result = LearningMemoryProposalWorkflow.approveAndStore(
            req, emptySet(), twoClaims, sources, creator, now
        ) { _, _ ->
            calls++
            if (calls == 1) MemoryRepositoryResult.Success(Unit)
            else MemoryRepositoryResult.Failure(MemoryRepositoryFailure.STORAGE_ERROR)
        }

        assertEquals(LearningMemoryWorkflowStatus.PARTIALLY_PERSISTED, result.status)
        assertEquals(listOf(MemoryId("learning-task-1-claim-1")), result.acceptedProposalIds)
        assertEquals(MemoryId("learning-task-1-claim-2"), result.failedProposalId)
        assertEquals(MemoryRepositoryFailure.STORAGE_ERROR, result.persistenceFailure)
    }

    @Test fun persistenceFailureBeforeAnyProposalIsReported() {
        val result = LearningMemoryProposalWorkflow.approveAndStore(
            request(), emptySet(), claims, sources, creator, now
        ) { _, _ -> MemoryRepositoryResult.Failure(MemoryRepositoryFailure.STORAGE_ERROR) }

        assertEquals(LearningMemoryWorkflowStatus.PERSISTENCE_FAILED, result.status)
        assertTrue(result.acceptedProposalIds.isEmpty())
    }
}
