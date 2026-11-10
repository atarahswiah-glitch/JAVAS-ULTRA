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

    private fun sandboxEvidence(
        id: String,
        at: Instant = now,
        passed: Boolean = true
    ): SkillEvidence {
        val code = "a".repeat(64)
        val image = "b".repeat(64)
        val suite = "d".repeat(64)
        val sandboxRequest = PracticeSandboxRequest(
            id, "task-$id", "safe-web-research", code, image,
            at.minusSeconds(5), at.plusSeconds(120), 1,
            PracticeSandboxLimits(60, 256, 64, 256, 100, 2)
        )
        val sandboxReceipt = PracticeSandboxReceipt(
            id, code, image, "trusted-runner", at, "c".repeat(64), true,
            0, false, false, false, false, 0, true, suite, 5, if (passed) 5 else 4
        )
        val decision = PracticeSandboxPolicy.evaluateReceipt(sandboxRequest, sandboxReceipt, now)
        return SkillEvidence(
            id, SkillEvidenceKind.SANDBOX_PRACTICE, passed, at,
            requireNotNull(decision.evidenceReference), suite, sandboxRequest, sandboxReceipt
        )
    }

    private fun operationalEvidence(
        id: String,
        kind: SkillEvidenceKind
    ) = SkillEvidence(
        id, kind, true, now,
        (if (kind == SkillEvidenceKind.INTEGRATION_VERIFICATION) "integration:" else "execution:") +
            "trusted-host:" + "e".repeat(64),
        "criteria-$id",
        trustedAttestationVerifiedByHost = true
    )

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
                sandboxEvidence("practice")
            )).level
        )
    }

    @Test fun sandboxPracticeAloneCannotClaimOperationalCapability() {
        val result = SkillAssessmentPolicy.evaluate(request(
            evidence("read", SkillEvidenceKind.STUDY_MATERIAL),
            evidence("explain", SkillEvidenceKind.EXPLANATION),
            sandboxEvidence("practice"),
            operationalEvidence("integration", SkillEvidenceKind.INTEGRATION_VERIFICATION),
            operationalEvidence("execution", SkillEvidenceKind.AUTHORIZED_EXECUTION)
        ))
        assertEquals(SkillCompetenceLevel.DEMONSTRATED_SKILL, result.level)
    }

    @Test fun operationalCapabilityRequiresIntegrationExecutionAndPermission() {
        val result = SkillAssessmentPolicy.evaluate(request(
            evidence("read", SkillEvidenceKind.STUDY_MATERIAL),
            evidence("explain", SkillEvidenceKind.EXPLANATION),
            sandboxEvidence("practice"),
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
            sandboxEvidence("practice-pass", at = now.minusSeconds(60)),
            sandboxEvidence("practice-fail", passed = false)
        ))
        assertEquals(SkillCompetenceLevel.UNDERSTANDING, result.level)
    }


    @Test fun arbitraryPracticeReferenceCannotEstablishDemonstratedSkill() {
        val result = SkillAssessmentPolicy.evaluate(request(
            evidence("read", SkillEvidenceKind.STUDY_MATERIAL),
            evidence("explain", SkillEvidenceKind.EXPLANATION),
            sandboxEvidence("practice")
        ))
        assertEquals(SkillAssessmentDenial.INVALID_SANDBOX_EVIDENCE, result.denial)
        assertEquals(SkillCompetenceLevel.UNKNOWN, result.level)
    }

    @Test fun sandboxReceiptForAnotherSkillCannotBeReused() {
        val practiceEvidence = sandboxEvidence("practice").copy(
            sandboxRequest = sandboxEvidence("other").sandboxRequest?.copy(skillId = "different-skill")
        )
        val result = SkillAssessmentPolicy.evaluate(request(
            evidence("read", SkillEvidenceKind.STUDY_MATERIAL),
            evidence("explain", SkillEvidenceKind.EXPLANATION),
            practiceEvidence
        ))
        assertEquals(SkillAssessmentDenial.INVALID_SANDBOX_EVIDENCE, result.denial)
    }


    @Test fun arbitraryOperationalEvidenceCannotClaimOperationalCapability() {
        val result = SkillAssessmentPolicy.evaluate(request(
            evidence("read", SkillEvidenceKind.STUDY_MATERIAL),
            evidence("explain", SkillEvidenceKind.EXPLANATION),
            sandboxEvidence("practice"),
            evidence("integration", SkillEvidenceKind.INTEGRATION_VERIFICATION),
            evidence("execution", SkillEvidenceKind.AUTHORIZED_EXECUTION),
            permission = true
        ))
        assertEquals(SkillAssessmentDenial.INVALID_OPERATIONAL_EVIDENCE, result.denial)
        assertEquals(SkillCompetenceLevel.UNKNOWN, result.level)
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
