package com.javasultra.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class LearningTaskPolicyTest {
    private fun task(privacySafe: Boolean = true, sourceBudget: Int = 10) = LearningTask(
        "learn-1", "Research astronomy basics", LearningDomain.SCIENCE,
        Instant.parse("2026-11-09T12:00:00Z"), LearningTaskState.PROPOSED,
        false, sourceBudget, 3600, privacySafe
    )

    @Test fun permitsBoundedPublicResearch() {
        assertNull(LearningTaskPolicy.validate(task()))
    }

    @Test fun rejectsUnapprovedPrivateDataUse() {
        assertEquals(LearningTaskDenial.PRIVATE_DATA_NOT_APPROVED, LearningTaskPolicy.validate(task(privacySafe = false)))
    }

    @Test fun rejectsUnboundedResearchBudget() {
        assertEquals(LearningTaskDenial.INVALID_BUDGET, LearningTaskPolicy.validate(task(sourceBudget = 1000)))
    }
}
