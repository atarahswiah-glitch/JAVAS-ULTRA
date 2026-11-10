package com.javasultra.app.core.learning

import com.javasultra.app.core.memory.MemoryLifecycle
import com.javasultra.app.core.memory.MemoryRecord
import com.javasultra.app.core.memory.MemoryRepositoryFailure
import com.javasultra.app.core.memory.MemoryRepositoryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LearningMemoryProposalWorkflowTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private val digest = "d".repeat(64)
    private val creator = "creator-1"

    private fun request(decision: CreatorLearningDecision = CreatorLearningDecision.APPROVE) =
        CreatorLearningReviewRequest(
            task = LearningTask(
                "task-workflow-1", "Research a public science topic", LearningDomain.SCIENCE,
                now.minusSeconds(60), LearningTaskState.READY_FOR_REVIEW, true, 10, 3600, true
            ),
            creatorPrincipalId = creator,
            authenticatedReviewerPrincipalId = creator,
            reviewId = "review-workflow-1",
            decision = decision,
            expectedEvidenceDigestSha256 = digest,
            submittedEvidenceDigestSha256 = digest,
            claimIds = setOf("claim-workflow-1"),
            reviewedAt = now.minusSeconds(5),
            stepUpAuthenticated = true
        )

    private fun claim() = EvidenceClaim(
        "claim-workflow-1", "A corroborated public research statement",
        setOf("source-workflow-a", "source-workflow-b"),
        setOf("source-workflow-a", "source-workflow-b"), emptySet(),
        now.minusSeconds(60), null, ClaimStatus.SUPPORTED, 0.85
    )

    private fun sources() = listOf(
        ResearchSource(
            "source-workflow-a", EvidenceSourceKind.OFFICIAL_DOCUMENTATION, "Source A",
            "Publisher A", "https://a.example.org", now.minusSeconds(120), now.minusSeconds(30), 0.9
        ),
        ResearchSource(
            "source-workflow-b", EvidenceSourceKind.ACADEMIC_REVIEW, "Source B",
            "Publisher B", "https://b.example.org", now.minusSeconds(120), now.minusSeconds(20), 0.9
        )
    )

    private class RecordingWriter(
        var result: MemoryRepositoryResult<Unit> = MemoryRepositoryResult.Success(Unit)
    ) : LearningProposalBatchWriter {
        var calls = 0
        var records = emptyList<MemoryRecord>()
        var actor: String? = null

        override fun insertProposedBatch(
            records: List<MemoryRecord>,
            actorPrincipalId: String
        ): MemoryRepositoryResult<Unit> {
            calls++
            this.records = records
            actor = actorPrincipalId
            return result
        }
    }

    @Test fun approvedClaimsArePersistedAsOneCreatorOwnedProposedBatch() {
        val req = request()
        val writer = RecordingWriter()
        val result = LearningMemoryProposalWorkflow(writer).approveAndPersist(
            req, emptySet(), listOf(claim()), sources(), creator, now
        )
        assertTrue(result is LearningProposalWorkflowResult.Persisted)
        assertEquals(1, writer.calls)
        assertEquals(creator, writer.actor)
        assertEquals(1, writer.records.size)
        assertEquals(creator, writer.records.single().ownerPrincipalId)
        assertEquals(MemoryLifecycle.PROPOSED, writer.records.single().lifecycle)
    }

    @Test fun rejectedCreatorReviewNeverCallsPersistence() {
        val req = request(CreatorLearningDecision.REJECT)
        val writer = RecordingWriter()
        val result = LearningMemoryProposalWorkflow(writer).approveAndPersist(
            req, emptySet(), listOf(claim()), sources(), creator, now
        )
        assertTrue(result is LearningProposalWorkflowResult.Rejected)
        assertEquals(LearningProposalWorkflowDenial.REVIEW_REJECTED, (result as LearningProposalWorkflowResult.Rejected).reason)
        assertEquals(0, writer.calls)
    }

    @Test fun consumedReviewIdIsRejectedBeforePersistence() {
        val req = request()
        val writer = RecordingWriter()
        val result = LearningMemoryProposalWorkflow(writer).approveAndPersist(
            req, setOf(req.reviewId), listOf(claim()), sources(), creator, now
        )
        assertTrue(result is LearningProposalWorkflowResult.Rejected)
        assertEquals(CreatorReviewDenial.REVIEW_ALREADY_CONSUMED,
            (result as LearningProposalWorkflowResult.Rejected).reviewDenial)
        assertEquals(0, writer.calls)
    }

    @Test fun wrongAuthenticatedReviewerIsRejectedBeforePersistence() {
        val req = request().copy(authenticatedReviewerPrincipalId = "other-user")
        val writer = RecordingWriter()
        val result = LearningMemoryProposalWorkflow(writer).approveAndPersist(
            req, emptySet(), listOf(claim()), sources(), creator, now
        )
        assertTrue(result is LearningProposalWorkflowResult.Rejected)
        assertEquals(CreatorReviewDenial.INVALID_IDENTITY,
            (result as LearningProposalWorkflowResult.Rejected).reviewDenial)
        assertEquals(0, writer.calls)
    }

    @Test fun creatorMismatchIsRejectedBeforePersistence() {
        val req = request()
        val writer = RecordingWriter()
        val result = LearningMemoryProposalWorkflow(writer).approveAndPersist(
            req, emptySet(), listOf(claim()), sources(), "different-creator", now
        )
        assertTrue(result is LearningProposalWorkflowResult.Rejected)
        assertEquals(LearningProposalWorkflowDenial.MAPPING_REJECTED,
            (result as LearningProposalWorkflowResult.Rejected).reason)
        assertEquals(0, writer.calls)
        assertFalse(result is LearningProposalWorkflowResult.Persisted)
    }

    @Test fun persistenceFailureIsSurfacedWithoutSuccessClaim() {
        val req = request()
        val writer = RecordingWriter(MemoryRepositoryResult.Failure(MemoryRepositoryFailure.STORAGE_ERROR))
        val result = LearningMemoryProposalWorkflow(writer).approveAndPersist(
            req, emptySet(), listOf(claim()), sources(), creator, now
        )
        assertTrue(result is LearningProposalWorkflowResult.Rejected)
        assertEquals(MemoryRepositoryFailure.STORAGE_ERROR,
            (result as LearningProposalWorkflowResult.Rejected).persistenceFailure)
        assertEquals(1, writer.calls)
    }
}
