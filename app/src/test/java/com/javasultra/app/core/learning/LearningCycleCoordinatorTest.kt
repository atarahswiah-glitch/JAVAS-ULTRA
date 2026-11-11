package com.javasultra.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class LearningCycleCoordinatorTest {
    private val now = Instant.parse("2026-11-11T12:00:00Z")
    private val signal = LearningSignal(
        "gap-astronomy", "Explain the phases of the Moon", LearningDomain.SCIENCE,
        DiscoveryTrigger.CAPABILITY_GAP, now.minusSeconds(10), LearningPriority.NORMAL
    )
    private val settings = AutonomousLearningSettings(
        enabled = true,
        permissions = setOf(AutonomyPermission.PUBLIC_WEB_READ),
        maxTasksPerDay = 5,
        maxSourcesPerTask = 5,
        maxTaskDuration = Duration.ofMinutes(20),
        allowedDomains = setOf("nasa.gov", "esa.int")
    )
    private val sources = listOf(
        ResearchSource("source-a", EvidenceSourceKind.OFFICIAL_DOCUMENTATION, "NASA Moon phases",
            "NASA", "https://nasa.gov/moon", now.minusSeconds(60), now.minusSeconds(20), 0.95),
        ResearchSource("source-b", EvidenceSourceKind.ACADEMIC_REVIEW, "ESA lunar overview",
            "European Space Agency", "https://esa.int/moon", now.minusSeconds(60), now.minusSeconds(15), 0.9)
    )
    private val claims = listOf(
        EvidenceClaim("claim-a", "The Moon's phases result from the changing view of its sunlit half.",
            setOf("source-a", "source-b"), setOf("source-a", "source-b"), emptySet(),
            now.minusSeconds(10), null, ClaimStatus.SUPPORTED, 0.9)
    )

    private class NoopWriter : LearningProposalBatchWriter {
        override fun insertProposedBatch(
            records: List<com.javasultra.app.core.memory.MemoryRecord>,
            actorPrincipalId: String
        ) = com.javasultra.app.core.memory.MemoryRepositoryResult.Success(Unit)
    }

    private class NoopJournal : LearningJournalReceiptWriter {
        override fun append(event: LearningJournalEvent, expectedTaskId: String) =
            LearningJournalStoreResult.Failure(LearningJournalStoreFailure.STORAGE_ERROR)
    }

    private fun coordinator(
        isGap: Boolean = true,
        bundle: LearningResearchBundle = LearningResearchBundle(sources, claims)
    ) = LearningCycleCoordinator(
        LearningGapInspectionPort { LearningGapInspection(isGap, "No verified knowledge found for this objective") },
        LearningResourceDiscoveryPort { _, _ -> bundle },
        LearningMemoryProposalWorkflow(NoopWriter(), NoopJournal())
    )

    @Test fun completesInspectPlanResearchAndEvidenceEvaluationStages() {
        val result = coordinator().inspectPlanResearchAndEvaluate(signal, settings, now, 0)
        assertTrue(result is LearningCycleResult.ReadyForCreatorReview)
        val packet = result as LearningCycleResult.ReadyForCreatorReview
        assertEquals(LearningTaskState.READY_FOR_REVIEW, packet.task.state)
        assertEquals(setOf("claim-a"), packet.claims.map { it.id }.toSet())
        assertEquals(64, packet.evidenceDigestSha256.length)
    }

    @Test fun doesNotResearchWhenInspectionFindsNoGap() {
        val result = coordinator(isGap = false).inspectPlanResearchAndEvaluate(signal, settings, now, 0)
        assertEquals(LearningCycleDenial.NOT_A_KNOWLEDGE_GAP, (result as LearningCycleResult.Denied).reason)
    }

    @Test fun plannerDenialStopsTheCycleBeforeResearch() {
        val disabled = settings.copy(enabled = false)
        val result = coordinator().inspectPlanResearchAndEvaluate(signal, disabled, now, 0)
        assertEquals(LearningCycleDenial.PLANNING_REJECTED, (result as LearningCycleResult.Denied).reason)
        assertEquals(PlanningDenial.DISABLED, result.planningDenial)
    }

    @Test fun insufficientIndependentSourcesCannotReachCreatorReview() {
        val oneSource = LearningResearchBundle(
            sources.take(1),
            listOf(claims.single().copy(sourceIds = setOf("source-a"),
                supportingSourceIds = setOf("source-a")))
        )
        val result = coordinator(bundle = oneSource).inspectPlanResearchAndEvaluate(signal, settings, now, 0)
        assertEquals(LearningCycleDenial.EVIDENCE_REJECTED, (result as LearningCycleResult.Denied).reason)
        assertEquals(EvidenceEvaluationDenial.INSUFFICIENT_INDEPENDENT_SOURCES, result.evidenceDenial)
    }

    @Test fun resourceDiscoveryCannotExceedTheConfiguredSourceBudget() {
        val oversized = LearningResearchBundle(sources + sources.map { it.copy(id = it.id + "-extra") }, claims)
        val result = coordinator(bundle = oversized).inspectPlanResearchAndEvaluate(signal, settings, now, 0)
        assertEquals(LearningCycleDenial.RESEARCH_BUDGET_EXCEEDED, (result as LearningCycleResult.Denied).reason)
    }

    @Test fun creatorReviewMustMatchTheExactEvaluatedTaskAndEvidenceDigest() {
        val packet = coordinator().inspectPlanResearchAndEvaluate(signal, settings, now, 0)
            as LearningCycleResult.ReadyForCreatorReview
        val request = CreatorLearningReviewRequest(
            task = packet.task.copy(objective = "different objective"),
            creatorPrincipalId = "creator-1",
            authenticatedReviewerPrincipalId = "creator-1",
            reviewId = "review-1",
            decision = CreatorLearningDecision.APPROVE,
            expectedEvidenceDigestSha256 = packet.evidenceDigestSha256,
            submittedEvidenceDigestSha256 = packet.evidenceDigestSha256,
            claimIds = setOf("claim-a"),
            reviewedAt = now,
            stepUpAuthenticated = true
        )
        val result = coordinator().authorizeAndPropose(
            packet, request, emptySet(), "creator-1", "journal-1", now
        )
        assertTrue(result is LearningCycleAuthorizationResult.Denied)
        assertEquals(LearningCycleDenial.REVIEW_PACKET_MISMATCH,
            (result as LearningCycleAuthorizationResult.Denied).reason)
    }

    @Test fun doesNotMarkUntrustedSandboxOrOperationalPracticeAsVerified() {
        val decision = SkillAssessmentPolicy.evaluate(
            SkillAssessmentRequest("moon-phases", emptyList(), now)
        )
        assertEquals(SkillCompetenceLevel.UNKNOWN, decision.level)
        assertFalse(decision.acceptedEvidenceIds.isNotEmpty())
    }
}
