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

    private class InMemoryLearningCycleJournalPort(
        private val failCreate: Boolean = false
    ) : LearningCycleJournalPort {
        private val entries = mutableMapOf<String, LearningJournalEntry>()
        private val events = mutableMapOf<String, LearningJournalEvent>()

        override fun create(entry: LearningJournalEntry): LearningJournalStoreResult<Unit> {
            if (failCreate) return LearningJournalStoreResult.Failure(LearningJournalStoreFailure.STORAGE_ERROR)
            if (entry.id in entries || LearningJournalPolicy.validateEntry(entry) != null) {
                return LearningJournalStoreResult.Failure(LearningJournalStoreFailure.INVALID_ENTRY)
            }
            entries[entry.id] = entry
            return LearningJournalStoreResult.Success(Unit)
        }

        override fun append(event: LearningJournalEvent, expectedTaskId: String): LearningJournalStoreResult<LearningJournalEntry> {
            events[event.id]?.let { prior ->
                val current = entries[event.entryId]
                    ?: return LearningJournalStoreResult.Failure(LearningJournalStoreFailure.NOT_FOUND)
                return if (prior == event && current.taskId == expectedTaskId) {
                    LearningJournalStoreResult.Success(current)
                } else LearningJournalStoreResult.Failure(LearningJournalStoreFailure.EVENT_ID_COLLISION)
            }
            val current = entries[event.entryId]
                ?: return LearningJournalStoreResult.Failure(LearningJournalStoreFailure.NOT_FOUND)
            if (current.taskId != expectedTaskId) {
                return LearningJournalStoreResult.Failure(LearningJournalStoreFailure.TASK_MISMATCH)
            }
            val updated = LearningJournalPolicy.apply(current, event).updatedEntry
                ?: return LearningJournalStoreResult.Failure(LearningJournalStoreFailure.INVALID_TRANSITION)
            events[event.id] = event
            entries[event.entryId] = updated
            return LearningJournalStoreResult.Success(updated)
        }

        fun entry(id: String) = entries[id]
        fun eventTypes() = events.values.map { it.type }
    }

    private fun coordinator(
        isGap: Boolean = true,
        bundle: LearningResearchBundle = LearningResearchBundle(sources, claims)
    ) = LearningCycleCoordinator(
        LearningGapInspectionPort { LearningGapInspection(isGap, "No verified knowledge found for this objective") },
        LearningResourceDiscoveryPort { _, _ -> bundle },
        LearningMemoryProposalWorkflow(NoopWriter(), NoopJournal()),
        InMemoryLearningCycleJournalPort()
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
        val denied = result as LearningCycleResult.Denied
        assertEquals(LearningCycleDenial.PLANNING_REJECTED, denied.reason)
        assertEquals(PlanningDenial.DISABLED, denied.planningDenial)
    }

    @Test fun insufficientIndependentSourcesCannotReachCreatorReview() {
        val oneSource = LearningResearchBundle(
            sources.take(1),
            listOf(claims.single().copy(sourceIds = setOf("source-a"),
                supportingSourceIds = setOf("source-a")))
        )
        val result = coordinator(bundle = oneSource).inspectPlanResearchAndEvaluate(signal, settings, now, 0)
        val denied = result as LearningCycleResult.Denied
        assertEquals(LearningCycleDenial.EVIDENCE_REJECTED, denied.reason)
        assertEquals(EvidenceEvaluationDenial.INSUFFICIENT_INDEPENDENT_SOURCES, denied.evidenceDenial)
    }

    @Test fun resourceDiscoveryCannotExceedTheConfiguredSourceBudget() {
        val oversized = LearningResearchBundle(
            sources +
                sources.map { it.copy(id = it.id + "-extra") } +
                sources.map { it.copy(id = it.id + "-extra-2") },
            claims
        )
        val result = coordinator(bundle = oversized).inspectPlanResearchAndEvaluate(signal, settings, now, 0)
        assertEquals(LearningCycleDenial.RESEARCH_BUDGET_EXCEEDED, (result as LearningCycleResult.Denied).reason)
    }

    @Test fun creatorReviewMustMatchTheExactEvaluatedTaskAndEvidenceDigest() {
        val cycle = coordinator()
        val packet = cycle.inspectPlanResearchAndEvaluate(signal, settings, now, 0)
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
        val result = cycle.authorizeAndPropose(
            packet, request, emptySet(), "creator-1", packet.journalEntryId, now
        )
        assertTrue(result is LearningCycleAuthorizationResult.Denied)
        assertEquals(LearningCycleDenial.REVIEW_PACKET_MISMATCH,
            (result as LearningCycleAuthorizationResult.Denied).reason)
    }

    @Test fun creatorApprovalSubmitsOnlyTheExactEvaluatedPacketToProposalWorkflow() {
        val cycle = coordinator()
        val packet = cycle.inspectPlanResearchAndEvaluate(signal, settings, now, 0)
            as LearningCycleResult.ReadyForCreatorReview
        val creator = "creator-1"
        val request = CreatorLearningReviewRequest(
            task = packet.task,
            creatorPrincipalId = creator,
            authenticatedReviewerPrincipalId = creator,
            reviewId = "review-accepted",
            decision = CreatorLearningDecision.APPROVE,
            expectedEvidenceDigestSha256 = packet.evidenceDigestSha256,
            submittedEvidenceDigestSha256 = packet.evidenceDigestSha256,
            claimIds = packet.claims.map { it.id }.toSet(),
            reviewedAt = now,
            stepUpAuthenticated = true
        )
        val result = cycle.authorizeAndPropose(
            packet, request, emptySet(), creator, packet.journalEntryId, now
        )
        assertTrue(result is LearningCycleAuthorizationResult.ProposalResult)
        val workflow = (result as LearningCycleAuthorizationResult.ProposalResult).result
        assertTrue(workflow is LearningProposalWorkflowResult.PersistedJournalPending)
    }


    @Test fun reviewPrincipalMustMatchThePrincipalUsedForPersistence() {
        val cycle = coordinator()
        val packet = cycle.inspectPlanResearchAndEvaluate(signal, settings, now, 0)
            as LearningCycleResult.ReadyForCreatorReview
        val requestPrincipal = "creator-authenticated"
        val persistencePrincipal = "different-principal"
        val request = CreatorLearningReviewRequest(
            task = packet.task,
            creatorPrincipalId = requestPrincipal,
            authenticatedReviewerPrincipalId = requestPrincipal,
            reviewId = "review-principal-mismatch",
            decision = CreatorLearningDecision.APPROVE,
            expectedEvidenceDigestSha256 = packet.evidenceDigestSha256,
            submittedEvidenceDigestSha256 = packet.evidenceDigestSha256,
            claimIds = packet.claims.map { it.id }.toSet(),
            reviewedAt = now,
            stepUpAuthenticated = true
        )
        val result = cycle.authorizeAndPropose(
            packet, request, emptySet(), persistencePrincipal, packet.journalEntryId, now
        )
        assertTrue(result is LearningCycleAuthorizationResult.Denied)
        assertEquals(
            LearningCycleDenial.REVIEW_PACKET_MISMATCH,
            (result as LearningCycleAuthorizationResult.Denied).reason
        )
    }
    @Test fun privateDataIsRejectedBeforeAnyGapOrResourceAdapterRuns() {
        var inspectionCalls = 0
        var discoveryCalls = 0
        val privateSignal = signal.copy(involvesPrivateData = true)
        val guarded = LearningCycleCoordinator(
            LearningGapInspectionPort {
                inspectionCalls++
                LearningGapInspection(true, "should never run")
            },
            LearningResourceDiscoveryPort { _, _ ->
                discoveryCalls++
                LearningResearchBundle(sources, claims)
            },
            LearningMemoryProposalWorkflow(NoopWriter(), NoopJournal()),
            InMemoryLearningCycleJournalPort()
        )
        val result = guarded.inspectPlanResearchAndEvaluate(privateSignal, settings, now, 0)
        val denied = result as LearningCycleResult.Denied
        assertEquals(LearningCycleDenial.PLANNING_REJECTED, denied.reason)
        assertEquals(PlanningDenial.PRIVATE_DATA, denied.planningDenial)
        assertEquals(0, inspectionCalls)
        assertEquals(0, discoveryCalls)
    }

    @Test fun noGapDoesNotStartResourceDiscovery() {
        var discoveryCalls = 0
        val guarded = LearningCycleCoordinator(
            LearningGapInspectionPort { LearningGapInspection(false, "No gap") },
            LearningResourceDiscoveryPort { _, _ ->
                discoveryCalls++
                LearningResearchBundle(sources, claims)
            },
            LearningMemoryProposalWorkflow(NoopWriter(), NoopJournal()),
            InMemoryLearningCycleJournalPort()
        )
        val result = guarded.inspectPlanResearchAndEvaluate(signal, settings, now, 0)
        assertEquals(LearningCycleDenial.NOT_A_KNOWLEDGE_GAP, (result as LearningCycleResult.Denied).reason)
        assertEquals(0, discoveryCalls)
    }


    @Test fun journalCreationFailureStopsBeforeResourceDiscovery() {
        var discoveryCalls = 0
        val guarded = LearningCycleCoordinator(
            LearningGapInspectionPort { LearningGapInspection(true, "Gap confirmed") },
            LearningResourceDiscoveryPort { _, _ ->
                discoveryCalls++
                LearningResearchBundle(sources, claims)
            },
            LearningMemoryProposalWorkflow(NoopWriter(), NoopJournal()),
            InMemoryLearningCycleJournalPort(failCreate = true)
        )
        val result = guarded.inspectPlanResearchAndEvaluate(signal, settings, now, 0)
        assertEquals(
            LearningCycleDenial.JOURNAL_PERSISTENCE_FAILED,
            (result as LearningCycleResult.Denied).reason
        )
        assertEquals(0, discoveryCalls)
    }

    @Test fun invalidSourceIdentifiersAreRejectedByEvidenceGateBeforeJournalAppend() {
        val invalidSources = listOf(sources.first().copy(id = ""))
        val invalidBundle = LearningResearchBundle(invalidSources, claims)
        val result = coordinator(bundle = invalidBundle)
            .inspectPlanResearchAndEvaluate(signal, settings, now, 0)
        val denied = result as LearningCycleResult.Denied
        assertEquals(LearningCycleDenial.EVIDENCE_REJECTED, denied.reason)
        assertEquals(EvidenceEvaluationDenial.INVALID_SOURCE, denied.evidenceDenial)
    }

    @Test fun successfulResearchStagesArePersistedInTheEncryptedJournalPort() {
        val journal = InMemoryLearningCycleJournalPort()
        val wired = LearningCycleCoordinator(
            LearningGapInspectionPort { LearningGapInspection(true, "Gap confirmed") },
            LearningResourceDiscoveryPort { _, _ -> LearningResearchBundle(sources, claims) },
            LearningMemoryProposalWorkflow(NoopWriter(), NoopJournal()),
            journal
        )
        val result = wired.inspectPlanResearchAndEvaluate(signal, settings, now, 0)
            as LearningCycleResult.ReadyForCreatorReview
        val entry = journal.entry(result.journalEntryId)
        assertEquals(LearningJournalState.SOURCE_REVIEW, entry?.state)
        assertEquals(setOf("source-a", "source-b"), entry?.sourceIds)
        assertEquals(setOf("claim-a"), entry?.claimIds)
        assertTrue(journal.eventTypes().containsAll(listOf(
            LearningJournalEventType.SIGNAL_DETECTED,
            LearningJournalEventType.TASK_QUEUED,
            LearningJournalEventType.TASK_STARTED,
            LearningJournalEventType.SOURCE_RETRIEVED,
            LearningJournalEventType.CLAIM_REVIEWED
        )))
    }

    @Test fun doesNotMarkUntrustedSandboxOrOperationalPracticeAsVerified() {
        val decision = SkillAssessmentPolicy.evaluate(
            SkillAssessmentRequest("moon-phases", emptyList(), now)
        )
        assertEquals(SkillCompetenceLevel.UNKNOWN, decision.level)
        assertFalse(decision.acceptedEvidenceIds.isNotEmpty())
    }
}
