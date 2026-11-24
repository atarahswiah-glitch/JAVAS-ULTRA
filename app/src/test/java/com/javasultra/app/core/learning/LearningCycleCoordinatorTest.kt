package com.javasultra.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class LearningCycleCoordinatorTest {
    private val now = Instant.parse("2026-11-24T12:00:00Z")
    private val signal = LearningSignal(
        "signal-1", "Explain basic astronomy", LearningDomain.SCIENCE,
        DiscoveryTrigger.CAPABILITY_GAP, now.minusSeconds(5), LearningPriority.NORMAL
    )
    private val settings = AutonomousLearningSettings(
        enabled = true,
        permissions = setOf(AutonomyPermission.PUBLIC_WEB_READ),
        maxTasksPerDay = 5,
        maxSourcesPerTask = 5,
        maxTaskDuration = Duration.ofMinutes(10),
        allowedDomains = setOf("nasa.gov", "esa.int")
    )

    private fun bundle(sourceCount: Int = 2): LearningResearchBundle {
        val sources = (1..sourceCount).map { index ->
            ResearchSource(
                id = "source-$index",
                kind = EvidenceSourceKind.OFFICIAL_DOCUMENTATION,
                title = "Astronomy reference $index",
                publisher = if (index == 1) "NASA" else "ESA",
                uri = "https://example-$index.org/reference",
                publishedAt = now.minusSeconds(60),
                retrievedAt = now,
                trustScore = 0.95
            )
        }
        val ids = sources.map { it.id }.toSet()
        val claim = EvidenceClaim(
            id = "claim-1",
            statement = "A testable astronomy claim",
            sourceIds = ids,
            supportingSourceIds = ids,
            contradictingSourceIds = emptySet(),
            createdAt = now,
            reviewedAt = null,
            status = ClaimStatus.SUPPORTED,
            confidence = 0.9
        )
        return LearningResearchBundle(sources, listOf(claim))
    }

    @Test fun runsBoundedCycleAndHandsEvidenceToCreatorReview() {
        val journal = FakeJournal()
        var providerCalls = 0
        val provider = LearningResearchProvider { _, domains, maxSources, maxDuration ->
            providerCalls++
            assertEquals(settings.allowedDomains, domains)
            assertEquals(settings.maxSourcesPerTask, maxSources)
            assertEquals(600L, maxDuration)
            Result.success(bundle())
        }

        val result = LearningCycleCoordinator(provider, journal)
            .run(signal, settings, now, tasksCreatedToday = 0)

        assertTrue(result is LearningCycleResult.ReadyForCreatorReview)
        val ready = result as LearningCycleResult.ReadyForCreatorReview
        assertEquals(LearningTaskState.READY_FOR_REVIEW, ready.task.state)
        assertEquals(LearningJournalState.SOURCE_REVIEW, ready.journalEntry.state)
        assertEquals(setOf("source-1", "source-2"), ready.journalEntry.sourceIds)
        assertEquals(setOf("claim-1"), ready.journalEntry.claimIds)
        assertEquals(64, ready.evidenceDigestSha256.length)
        assertEquals(1, providerCalls)
    }

    @Test fun plannerDenialStopsBeforeResearchOrJournalCreation() {
        val journal = FakeJournal()
        var providerCalls = 0
        val provider = LearningResearchProvider { _, _, _, _ ->
            providerCalls++
            Result.success(bundle())
        }
        val result = LearningCycleCoordinator(provider, journal).run(
            signal.copy(involvesPrivateData = true), settings, now, tasksCreatedToday = 0
        )

        val rejected = result as LearningCycleResult.Rejected
        assertEquals(LearningCycleDenial.PLANNING_REJECTED, rejected.reason)
        assertEquals(PlanningDenial.PRIVATE_DATA, rejected.planningDenial)
        assertEquals(0, providerCalls)
        assertTrue(journal.entries.isEmpty())
    }

    @Test fun insufficientIndependentSourcesFailClosedAndJournalFailure() {
        val journal = FakeJournal()
        val provider = LearningResearchProvider { _, _, _, _ -> Result.success(bundle(sourceCount = 1)) }

        val result = LearningCycleCoordinator(provider, journal)
            .run(signal, settings, now, tasksCreatedToday = 0)

        val rejected = result as LearningCycleResult.Rejected
        assertEquals(LearningCycleDenial.EVIDENCE_REJECTED, rejected.reason)
        assertEquals(EvidenceEvaluationDenial.INSUFFICIENT_INDEPENDENT_SOURCES, rejected.evidenceDenial)
        assertEquals(LearningJournalState.FAILED, journal.entries.values.single().state)
        assertEquals("EVIDENCE_REJECTED", journal.entries.values.single().failureCode)
    }

    @Test fun sourceBudgetIsEnforcedBeforeEvidenceReview() {
        val journal = FakeJournal()
        val provider = LearningResearchProvider { _, _, _, _ -> Result.success(bundle(sourceCount = 6)) }

        val result = LearningCycleCoordinator(provider, journal)
            .run(signal, settings, now, tasksCreatedToday = 0)

        assertEquals(
            LearningCycleDenial.SOURCE_BUDGET_EXCEEDED,
            (result as LearningCycleResult.Rejected).reason
        )
        assertEquals(LearningJournalState.FAILED, journal.entries.values.single().state)
    }

    @Test fun researchFailureIsRecordedWithoutCreatingMemory() {
        val journal = FakeJournal()
        val provider = LearningResearchProvider { _, _, _, _ ->
            Result.failure(IllegalStateException("transport unavailable"))
        }

        val result = LearningCycleCoordinator(provider, journal)
            .run(signal, settings, now, tasksCreatedToday = 0)

        assertEquals(
            LearningCycleDenial.RESEARCH_FAILED,
            (result as LearningCycleResult.Rejected).reason
        )
        assertEquals(LearningJournalState.FAILED, journal.entries.values.single().state)
        assertFalse(journal.entries.values.single().sourceIds.isNotEmpty())
    }

    private class FakeJournal : LearningCycleJournal {
        val entries = linkedMapOf<String, LearningJournalEntry>()

        override fun create(entry: LearningJournalEntry): LearningJournalStoreResult<Unit> {
            if (entries.containsKey(entry.id)) {
                return LearningJournalStoreResult.Failure(LearningJournalStoreFailure.INVALID_ENTRY)
            }
            entries[entry.id] = entry
            return LearningJournalStoreResult.Success(Unit)
        }

        override fun append(
            event: LearningJournalEvent,
            expectedTaskId: String
        ): LearningJournalStoreResult<LearningJournalEntry> {
            val current = entries[event.entryId]
                ?: return LearningJournalStoreResult.Failure(LearningJournalStoreFailure.NOT_FOUND)
            if (current.taskId != expectedTaskId) {
                return LearningJournalStoreResult.Failure(LearningJournalStoreFailure.TASK_MISMATCH)
            }
            val applied = LearningJournalPolicy.apply(current, event)
            val updated = applied.updatedEntry
                ?: return LearningJournalStoreResult.Failure(LearningJournalStoreFailure.INVALID_TRANSITION)
            entries[event.entryId] = updated
            return LearningJournalStoreResult.Success(updated)
        }
    }
}
