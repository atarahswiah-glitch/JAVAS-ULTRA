package com.javasultra.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LearningJournalTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private fun entry(state: LearningJournalState = LearningJournalState.QUEUED) = LearningJournalEntry(
        "journal-1", "task-1", "Study planetary science", LearningDomain.SCIENCE,
        state, now.minusSeconds(60), now.minusSeconds(30)
    )
    private fun event(type: LearningJournalEventType, at: Instant = now, source: String? = null, claim: String? = null) =
        LearningJournalEvent("event-1", "journal-1", type, at, "javas-learning-agent", source, claim)

    @Test fun permitsNormalLearningLifecycle() {
        assertTrue(LearningJournalPolicy.canTransition(LearningJournalState.DISCOVERED, LearningJournalState.QUEUED))
        assertTrue(LearningJournalPolicy.canTransition(LearningJournalState.QUEUED, LearningJournalState.RUNNING))
        assertTrue(LearningJournalPolicy.canTransition(LearningJournalState.RUNNING, LearningJournalState.SOURCE_REVIEW))
        assertTrue(LearningJournalPolicy.canTransition(LearningJournalState.SOURCE_REVIEW, LearningJournalState.KNOWLEDGE_PROPOSED))
        assertTrue(LearningJournalPolicy.canTransition(LearningJournalState.KNOWLEDGE_PROPOSED, LearningJournalState.COMPLETED))
    }

    @Test fun appliesEventsAndCarriesEvidenceReferences() {
        val started = LearningJournalPolicy.apply(entry(), event(LearningJournalEventType.TASK_STARTED))
        assertTrue(started.accepted)
        assertEquals(LearningJournalState.RUNNING, started.updatedEntry?.state)
        val sourced = LearningJournalPolicy.apply(started.updatedEntry!!,
            event(LearningJournalEventType.SOURCE_RETRIEVED, now.plusSeconds(1), source = "source-1"))
        assertTrue(sourced.accepted)
        assertEquals(setOf("source-1"), sourced.updatedEntry?.sourceIds)
        assertEquals(LearningJournalState.SOURCE_REVIEW, sourced.updatedEntry?.state)
        val reviewed = LearningJournalPolicy.apply(sourced.updatedEntry!!,
            event(LearningJournalEventType.CLAIM_REVIEWED, now.plusSeconds(2), claim = "claim-1"))
        assertTrue(reviewed.accepted)
        assertEquals(setOf("claim-1"), reviewed.updatedEntry?.claimIds)
    }

    @Test fun acceptsMultipleSourcesDuringSourceReview() {
        val first = LearningJournalPolicy.apply(entry(LearningJournalState.RUNNING),
            event(LearningJournalEventType.SOURCE_RETRIEVED, now, source = "source-1"))
        assertTrue(first.accepted)
        val second = LearningJournalPolicy.apply(first.updatedEntry!!,
            event(LearningJournalEventType.SOURCE_RETRIEVED, now.plusSeconds(1), source = "source-2"))
        assertTrue(second.accepted)
        assertEquals(setOf("source-1", "source-2"), second.updatedEntry?.sourceIds)
        assertEquals(LearningJournalState.SOURCE_REVIEW, second.updatedEntry?.state)
    }

    @Test fun rejectsEventThatDoesNotMatchCurrentState() {
        val result = LearningJournalPolicy.apply(entry(), event(LearningJournalEventType.TASK_COMPLETED))
        assertEquals(LearningJournalDenial.INVALID_TRANSITION, result.denial)
        assertNull(result.updatedEntry)
    }

    @Test fun rejectsRestartFromCompletedState() {
        assertTrue(!LearningJournalPolicy.canTransition(LearningJournalState.COMPLETED, LearningJournalState.QUEUED))
    }

    @Test fun rejectsEventsWithTimeBeforeEntryUpdate() {
        assertEquals(LearningJournalDenial.TIME_MOVED_BACKWARDS,
            LearningJournalPolicy.validateEvent(entry(), event(LearningJournalEventType.TASK_STARTED, now.minusSeconds(31))))
    }

    @Test fun requiresSourceIdForSourceRetrievalEvent() {
        assertEquals(LearningJournalDenial.SOURCE_REQUIRED,
            LearningJournalPolicy.validateEvent(entry(), event(LearningJournalEventType.SOURCE_RETRIEVED)))
    }

    @Test fun requiresClaimIdForClaimReviewEvent() {
        assertEquals(LearningJournalDenial.CLAIM_REQUIRED,
            LearningJournalPolicy.validateEvent(entry(LearningJournalState.SOURCE_REVIEW),
                event(LearningJournalEventType.CLAIM_REVIEWED)))
    }

    @Test fun rejectsEventsForTerminalEntries() {
        assertEquals(LearningJournalDenial.TERMINAL_STATE,
            LearningJournalPolicy.validateEvent(entry(LearningJournalState.COMPLETED),
                event(LearningJournalEventType.TASK_COMPLETED)))
    }

    @Test fun rejectsDuplicateStateChangingEvents() {
        val result = LearningJournalPolicy.apply(entry(), event(LearningJournalEventType.TASK_QUEUED))
        assertEquals(LearningJournalDenial.INVALID_TRANSITION, result.denial)
    }

    @Test fun permitsNonTransitioningEvidenceReviewEvent() {
        val result = LearningJournalPolicy.apply(entry(LearningJournalState.SOURCE_REVIEW),
            event(LearningJournalEventType.CLAIM_REVIEWED, claim = "claim-2"))
        assertTrue(result.accepted)
        assertEquals(LearningJournalState.SOURCE_REVIEW, result.updatedEntry?.state)
    }

    @Test fun recordsProposalPersistenceWithoutCompletingTheLearningTask() {
        val result = LearningJournalPolicy.apply(
            entry(LearningJournalState.KNOWLEDGE_PROPOSED),
            event(LearningJournalEventType.MEMORY_PROPOSAL_PERSISTED)
        )
        assertTrue(result.accepted)
        assertEquals(LearningJournalState.KNOWLEDGE_PROPOSED, result.updatedEntry?.state)
    }

    @Test fun rejectsProposalReceiptBeforeKnowledgeHasBeenProposed() {
        assertEquals(LearningJournalDenial.INVALID_TRANSITION,
            LearningJournalPolicy.apply(entry(LearningJournalState.SOURCE_REVIEW),
                event(LearningJournalEventType.MEMORY_PROPOSAL_PERSISTED)).denial)
    }

    @Test fun sanitizesFailureDetailsBeforeStoringThemInEntry() {
        val result = LearningJournalPolicy.apply(entry(LearningJournalState.RUNNING),
            event(LearningJournalEventType.TASK_FAILED).copy(safeDetail = "NETWORK_TIMEOUT"))
        assertTrue(result.accepted)
        assertEquals("NETWORK_TIMEOUT", result.updatedEntry?.failureCode)
    }

    @Test fun rejectsFreeTextFailureDetailsThatMayContainSecrets() {
        val result = LearningJournalPolicy.apply(entry(LearningJournalState.RUNNING),
            event(LearningJournalEventType.TASK_FAILED).copy(safeDetail = "token=secret; network timeout!"))
        assertTrue(result.accepted)
        assertNull(result.updatedEntry?.failureCode)
    }

    @Test fun allowsScheduledLearningWhenDueWithoutCreatorPrompt() {
        val schedule = LearningSchedule(true, 3600, 8)
        assertNull(LearningSchedulePolicy.evaluate(schedule, now, now.minusSeconds(3600), 2))
    }

    @Test fun respectsPauseAndDailyLimits() {
        assertEquals(LearningScheduleDenial.PAUSED,
            LearningSchedulePolicy.evaluate(LearningSchedule(true, 3600, 8, now.plusSeconds(60)), now, null, 0))
        assertEquals(LearningScheduleDenial.DAILY_LIMIT_REACHED,
            LearningSchedulePolicy.evaluate(LearningSchedule(true, 3600, 8), now, null, 8))
    }
}
