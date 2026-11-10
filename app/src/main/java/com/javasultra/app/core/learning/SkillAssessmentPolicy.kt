package com.javasultra.app.core.learning

import java.time.Duration
import java.time.Instant

enum class SkillEvidenceKind {
    STUDY_MATERIAL,
    EXPLANATION,
    SANDBOX_PRACTICE,
    INTEGRATION_VERIFICATION,
    AUTHORIZED_EXECUTION
}

enum class SkillCompetenceLevel {
    UNKNOWN,
    AWARENESS,
    UNDERSTANDING,
    DEMONSTRATED_SKILL,
    OPERATIONAL_CAPABILITY
}

data class SkillEvidence(
    val id: String,
    val kind: SkillEvidenceKind,
    val passed: Boolean,
    val observedAt: Instant,
    val evidenceReference: String,
    val acceptanceCriteriaId: String,
    val sandboxRequest: PracticeSandboxRequest? = null,
    val sandboxReceipt: PracticeSandboxReceipt? = null,
    val trustedAttestationVerifiedByHost: Boolean = false
)

data class SkillAssessmentRequest(
    val skillId: String,
    val evidence: List<SkillEvidence>,
    val now: Instant,
    val maxEvidenceAge: Duration = Duration.ofDays(180),
    val hasOperationalPermission: Boolean = false
)

enum class SkillAssessmentDenial {
    EMPTY_SKILL_ID,
    INVALID_FRESHNESS_WINDOW,
    DUPLICATE_EVIDENCE_ID,
    INVALID_EVIDENCE,
    FUTURE_EVIDENCE,
    STALE_EVIDENCE,
    INVALID_SANDBOX_EVIDENCE,
    INVALID_OPERATIONAL_EVIDENCE,
    TRUSTED_SANDBOX_VERIFIER_UNAVAILABLE,
    TRUSTED_OPERATIONAL_VERIFIER_UNAVAILABLE
}

data class SkillAssessmentDecision(
    val level: SkillCompetenceLevel,
    val denial: SkillAssessmentDenial? = null,
    val acceptedEvidenceIds: Set<String> = emptySet()
)

/**
 * Separates study, explanation, practice and operational evidence.
 *
 * This client-side policy deliberately fails closed for practice and operational evidence
 * until a cryptographic trusted-host verifier is integrated. Client booleans and local
 * receipt-shaped objects are not accepted as proof of execution.
 */
object SkillAssessmentPolicy {
    fun evaluate(request: SkillAssessmentRequest): SkillAssessmentDecision {
        if (request.skillId.isBlank()) return denied(SkillAssessmentDenial.EMPTY_SKILL_ID)
        if (request.maxEvidenceAge.isNegative || request.maxEvidenceAge.isZero) {
            return denied(SkillAssessmentDenial.INVALID_FRESHNESS_WINDOW)
        }
        if (request.evidence.map { it.id }.toSet().size != request.evidence.size) {
            return denied(SkillAssessmentDenial.DUPLICATE_EVIDENCE_ID)
        }
        if (request.evidence.any {
                it.id.isBlank() || it.evidenceReference.isBlank() || it.acceptanceCriteriaId.isBlank()
            }) return denied(SkillAssessmentDenial.INVALID_EVIDENCE)
        for (item in request.evidence) {
            if (item.kind == SkillEvidenceKind.SANDBOX_PRACTICE) {
                val sandboxRequest = item.sandboxRequest
                    ?: return denied(SkillAssessmentDenial.INVALID_SANDBOX_EVIDENCE)
                val sandboxReceipt = item.sandboxReceipt
                    ?: return denied(SkillAssessmentDenial.INVALID_SANDBOX_EVIDENCE)
                val sandboxDecision = PracticeSandboxPolicy.evaluateReceipt(
                    sandboxRequest, sandboxReceipt, request.now
                )
                if (!sandboxDecision.accepted ||
                    item.passed != sandboxDecision.passedAcceptanceCriteria ||
                    sandboxRequest.skillId != request.skillId ||
                    item.observedAt != sandboxReceipt.completedAt ||
                    item.acceptanceCriteriaId != sandboxReceipt.acceptanceSuiteSha256 ||
                    item.evidenceReference != sandboxDecision.evidenceReference
                ) return denied(SkillAssessmentDenial.INVALID_SANDBOX_EVIDENCE)
                // A local boolean/receipt is not a cryptographic host verification.
                // Fail closed until an authenticated trusted-host verifier is integrated.
                return denied(SkillAssessmentDenial.TRUSTED_SANDBOX_VERIFIER_UNAVAILABLE)
            } else if (item.sandboxRequest != null || item.sandboxReceipt != null) {
                return denied(SkillAssessmentDenial.INVALID_EVIDENCE)
            }
            if (item.kind == SkillEvidenceKind.INTEGRATION_VERIFICATION ||
                item.kind == SkillEvidenceKind.AUTHORIZED_EXECUTION
            ) {
                val expectedPrefix = if (item.kind == SkillEvidenceKind.INTEGRATION_VERIFICATION) {
                    "integration:"
                } else {
                    "execution:"
                }
                if (!item.trustedAttestationVerifiedByHost ||
                    !item.evidenceReference.startsWith(expectedPrefix)
                ) return denied(SkillAssessmentDenial.INVALID_OPERATIONAL_EVIDENCE)
                // The client-side flag is descriptive only, never a trust decision.
                return denied(SkillAssessmentDenial.TRUSTED_OPERATIONAL_VERIFIER_UNAVAILABLE)
            } else if (item.trustedAttestationVerifiedByHost) {
                return denied(SkillAssessmentDenial.INVALID_EVIDENCE)
            }
        }
        if (request.evidence.any { it.observedAt.isAfter(request.now) }) {
            return denied(SkillAssessmentDenial.FUTURE_EVIDENCE)
        }
        if (request.evidence.any { it.observedAt.isBefore(request.now.minus(request.maxEvidenceAge)) }) {
            return denied(SkillAssessmentDenial.STALE_EVIDENCE)
        }

        fun latestPassed(kind: SkillEvidenceKind): SkillEvidence? =
            request.evidence.filter { it.kind == kind }.maxByOrNull { it.observedAt }?.takeIf { it.passed }

        val accepted = linkedSetOf<String>()
        val study = latestPassed(SkillEvidenceKind.STUDY_MATERIAL)
        if (study == null) return SkillAssessmentDecision(SkillCompetenceLevel.UNKNOWN)
        accepted += study.id

        val explanation = latestPassed(SkillEvidenceKind.EXPLANATION)
            ?: return SkillAssessmentDecision(SkillCompetenceLevel.AWARENESS, acceptedEvidenceIds = accepted)
        accepted += explanation.id

        val practice = latestPassed(SkillEvidenceKind.SANDBOX_PRACTICE)
            ?: return SkillAssessmentDecision(SkillCompetenceLevel.UNDERSTANDING, acceptedEvidenceIds = accepted)
        accepted += practice.id

        val integration = latestPassed(SkillEvidenceKind.INTEGRATION_VERIFICATION)
        val execution = latestPassed(SkillEvidenceKind.AUTHORIZED_EXECUTION)
        if (integration != null && execution != null && request.hasOperationalPermission) {
            accepted += integration.id
            accepted += execution.id
            return SkillAssessmentDecision(
                SkillCompetenceLevel.OPERATIONAL_CAPABILITY,
                acceptedEvidenceIds = accepted
            )
        }
        return SkillAssessmentDecision(
            SkillCompetenceLevel.DEMONSTRATED_SKILL,
            acceptedEvidenceIds = accepted
        )
    }

    private fun denied(reason: SkillAssessmentDenial) =
        SkillAssessmentDecision(SkillCompetenceLevel.UNKNOWN, denial = reason)
}
