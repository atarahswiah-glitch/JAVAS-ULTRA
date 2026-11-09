package com.javasultra.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AutonomousLearningPlannerTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private val signal = LearningSignal("gap-1", "basic astronomy", LearningDomain.SCIENCE, DiscoveryTrigger.CAPABILITY_GAP, now, LearningPriority.NORMAL)
    private fun settings(enabled: Boolean = true, privateRead: Boolean = false) = AutonomousLearningSettings(
        enabled, if (privateRead) emptySet() else setOf(AutonomyPermission.PUBLIC_WEB_READ),
        8, 12, Duration.ofMinutes(30), setOf("nasa.gov")
    )

    @Test fun queuesLearningWithoutCreatorPromptWhenPermitted() {
        val result = AutonomousLearningPlanner.evaluate(signal, settings(), now, 0)
        assertTrue(result.approvedForQueue)
    }
    @Test fun stopsWhenAutonomousLearningDisabled() {
        assertEquals(PlanningDenial.DISABLED, AutonomousLearningPlanner.evaluate(signal, settings(false), now, 0).denial)
    }
    @Test fun pausesBackgroundLearningDuringPauseWindow() {
        val paused = settings().copy(pausedUntil = now.plusSeconds(60))
        assertEquals(PlanningDenial.PAUSED, AutonomousLearningPlanner.evaluate(signal, paused, now, 0).denial)
    }
    @Test fun neverUsesPrivateDataForUnapprovedLearning() {
        assertEquals(PlanningDenial.PRIVATE_DATA, AutonomousLearningPlanner.evaluate(signal.copy(involvesPrivateData = true), settings(), now, 0).denial)
    }
    @Test fun respectsDailyTaskBudget() {
        assertEquals(PlanningDenial.DAILY_LIMIT_REACHED, AutonomousLearningPlanner.evaluate(signal, settings(), now, 8).denial)
    }
    @Test fun rejectsDuplicateDiscoverySignals() {
        assertEquals(
            PlanningDenial.DUPLICATE_OR_EMPTY_TOPIC,
            AutonomousLearningPlanner.evaluate(signal, settings(), now, 0, setOf(signal.id)).denial
        )
    }

    @Test fun rejectsSignalsFromTheFuture() {
        assertEquals(
            PlanningDenial.FUTURE_SIGNAL,
            AutonomousLearningPlanner.evaluate(signal.copy(detectedAt = now.plusSeconds(1)), settings(), now, 0).denial
        )
    }

    @Test fun rejectsInvalidOrEmptyWebDomainAllowlist() {
        assertEquals(
            PlanningDenial.INVALID_DOMAIN_POLICY,
            AutonomousLearningPlanner.evaluate(signal, settings().copy(allowedDomains = emptySet()), now, 0).denial
        )
        assertEquals(
            PlanningDenial.INVALID_DOMAIN_POLICY,
            AutonomousLearningPlanner.evaluate(signal, settings().copy(allowedDomains = setOf("https://evil.example/path")), now, 0).denial
        )
    }

    @Test fun rejectsOversizedLearningObjective() {
        assertEquals(
            PlanningDenial.DUPLICATE_OR_EMPTY_TOPIC,
            AutonomousLearningPlanner.evaluate(signal.copy(topic = "x".repeat(501)), settings(), now, 0).denial
        )
    }

    @Test fun allowsDocumentationOnlyLearningWithoutWebDomainAllowlist() {
        val documentationOnly = settings().copy(
            permissions = setOf(AutonomyPermission.PUBLIC_DOCUMENTATION_READ),
            allowedDomains = emptySet()
        )
        assertTrue(AutonomousLearningPlanner.evaluate(signal, documentationOnly, now, 0).approvedForQueue)
    }

    @Test fun requiresPublicResearchPermission() {
        assertEquals(PlanningDenial.NO_PERMISSION, AutonomousLearningPlanner.evaluate(signal, settings(privateRead = true), now, 0).denial)
    }
}
