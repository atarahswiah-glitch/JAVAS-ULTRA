package com.javasultra.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SkillAssessmentPolicyTest {
    private val now = Instant.parse("2026-11-10T10:00:00Z")
    private fun evidence(
        id: String,
        kind: SkillEvidenceKind,
        passed: Boolean = true,
        at: Instant = now
    ) = SkillEvidence(id, kind, passed, at, "test://$id", "criteria-$id")

    private fun request(vararg evidence: SkillEvidence, permission: Boolean = false) =
        SkillAssessmentRequest("safe-web-research", evidence.toList(), now, hasOperationalPermission = permission)

    @Test fun noEvidenceDoesNotClaimAwareness() {
        assertEquals(SkillCompetenceLevel.UNKNOWN, SkillAssessmentPolicy.evaluate(request()).level)
    }

    @Test fun readingOnlyEstablishesAwareness() {
        assertEquals(
            SkillCompetenceLevel.AWARENESS,
            SkillAssessmentPolicy.evaluate(request(evidence("read", SkillEvidenceKind.STUDY_MATERIAL))).level
        )
    }

    @Test fun explanationEstablishesUnderstanding() {
        assertEquals(
            SkillCompetenceLevel.UNDERSTANDING,
            SkillAssessmentPolicy.evaluate(request(
                evidence("read", SkillEvidenceKind.STUDY_MATERIAL),
                evidence("explain", SkillEvidenceKind.EXPLANATION)
            )).level
        )
    }

    @Test fun successfulSandboxPracticeEstablishesDemonstratedSkill() {
        assertEquals(
            SkillCompetenceLevel.DEMONSTRATED_SKILL,
            SkillAssessmentPolicy.evaluate(request(
                evidence("read", SkillEvidenceKind.STUDY_MATERIAL),
                evidence("explain", SkillEvidenceKind.EXPLANATION),
                evidence("practice", SkillEvidenceKind.SANDBOX_PRACTICE)
            )).level
        )
    }

    @Test fun sandboxPracticeAloneCannotClaimOperationalCapability() {
        val result = SkillAssessmentPolicy.evaluate(request(
            evidence("read", SkillEvidenceKind.STUDY_MATERIAL),
            evidence("explain", SkillEvidenceKind.EXPLANATION),
            evidence("practice", SkillEvidenceKind.SANDBOX_PRACTICE),
            evidence("integration", SkillEvidenceKind.INTEGRATION_VERIFICATION),
            evidence("execution", SkillEvidenceKind.AUTHORIZED_EXECUTION)
        ))
        assertEquals(SkillCompetenceLevel.DEMONSTRATED_SKILL, result.level)
    }

    @Test fun operationalCapabilityRequiresIntegrationExecutionAndPermission() {
        val result = SkillAssessmentPolicy.evaluate(request(
            evidence("read", SkillEvidenceKind.STUDY_MATERIAL),
            evidence("explain", SkillEvidenceKind.EXPLANATION),
            evidence("practice", SkillEvidenceKind.SANDBOX_PRACTICE),
            evidence("integration", SkillEvidenceKind.INTEGRATION_VERIFICATION),
            evidence("execution", SkillEvidenceKind.AUTHORIZED_EXECUTION),
            permission = true
        ))
        assertEquals(SkillCompetenceLevel.OPERATIONAL_CAPABILITY, result.level)
        assertEquals(5, result.acceptedEvidenceIds.size)
    }

    @Test fun latestFailedPracticeInvalidatesEarlierPassingPractice() {
        val result = SkillAssessmentPolicy.evaluate(request(
            evidence("read", SkillEvidenceKind.STUDY_MATERIAL),
            evidence("explain", SkillEvidenceKind.EXPLANATION),
            evidence("practice-pass", SkillEvidenceKind.SANDBOX_PRACTICE, at = now.minusSeconds(60)),
            evidence("practice-fail", SkillEvidenceKind.SANDBOX_PRACTICE, passed = false, at = now)
        ))
        assertEquals(SkillCompetenceLevel.UNDERSTANDING, result.level)
    }

    @Test fun futureEvidenceIsRejected() {
        val result = SkillAssessmentPolicy.evaluate(request(evidence("future", SkillEvidenceKind.STUDY_MATERIAL, at = now.plusSeconds(1))))
        assertEquals(SkillAssessmentDenial.FUTURE_EVIDENCE, result.denial)
    }

    @Test fun duplicateEvidenceIdentifiersAreRejected() {
        val result = SkillAssessmentPolicy.evaluate(request(
            evidence("same", SkillEvidenceKind.STUDY_MATERIAL),
            evidence("same", SkillEvidenceKind.EXPLANATION)
        ))
        assertEquals(SkillAssessmentDenial.DUPLICATE_EVIDENCE_ID, result.denial)
    }

    @Test fun blankEvidenceReferenceIsRejected() {
        val result = SkillAssessmentPolicy.evaluate(request(
            SkillEvidence("bad", SkillEvidenceKind.STUDY_MATERIAL, true, now, "", "criteria")
        ))
        assertEquals(SkillAssessmentDenial.INVALID_EVIDENCE, result.denial)
        assertTrue(result.acceptedEvidenceIds.isEmpty())
    }
}
