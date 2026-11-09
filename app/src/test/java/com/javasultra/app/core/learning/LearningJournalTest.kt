package com.javasultra.app.core.learning

import org.junit.Assert.assertEquals
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
    private fun event(type: LearningJournalEventType, at: Instant = now) =
        LearningJournalEvent("event-1", "journal-1", type, at, "javas-learning-agent")

    @Test fun permitsNormalLearningLifecycle() {
        assertTrue(LearningJournalPolicy.canTransition(LearningJournalState.DISCOVERED, LearningJournalState.QUEUED))
        assertTrue(LearningJournalPolicy.canTransition(LearningJournalState.QUEUED, LearningJournalState.RUNNING))
        assertTrue(LearningJournalPolicy.canTransition(LearningJournalState.RUNNING, LearningJournalState.SOURCE_REVIEW))
        assertTrue(LearningJournalPolicy.canTransition(LearningJournalState.SOURCE_REVIEW, LearningJournalState.KNOWLEDGE_PROPOSED))
        assertTrue(LearningJournalPolicy.canTransition(LearningJournalState.KNOWLEDGE_PROPOSED, LearningJournalState.COMPLETED))
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

    @Test fun rejectsEventsForTerminalEntries() {
        assertEquals(LearningJournalDenial.TERMINAL_STATE,
            LearningJournalPolicy.validateEvent(entry(LearningJournalState.COMPLETED), event(LearningJournalEventType.TASK_COMPLETED)))
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
