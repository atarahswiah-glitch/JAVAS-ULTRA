package com.javasultra.app.core.security

/**
 * Pure decision policy for JAVAS's emergency survival protocol.
 *
 * This class only produces a defensive assessment. It does not block network traffic,
 * revoke credentials, write audit records, send notifications, move workloads or restore
 * an account. Those effects must be implemented by separately authenticated services.
 *
 * The policy is deliberately fail-closed for critical integrity/monitoring failures and
 * deliberately fail-safe against permanent lockout: it never grants Creator authority.
 */
enum class GuardianMode { NORMAL, RESTRICTED, SECURE_SHELTER }

enum class GuardianThreatSeverity { INFORMATIONAL, SUSPICIOUS, HIGH, CRITICAL }

enum class GuardianSignalType {
    UNTRUSTED_DEVICE,
    AUTHENTICATION_FAILURE_BURST,
    TOKEN_REUSE,
    DISALLOWED_ORIGIN,
    PRIVILEGE_ESCALATION_ATTEMPT,
    MEMORY_ACCESS_ANOMALY,
    APP_INTEGRITY_FAILURE,
    SECURITY_MONITORING_TAMPER,
    UNKNOWN
}

data class GuardianSignal(
    val eventId: String,
    val type: GuardianSignalType,
    val severity: GuardianThreatSeverity,
    val observedAtEpochMillis: Long
) {
    fun isWellFormed(): Boolean =
        eventId.isNotBlank() &&
            eventId.length <= MAX_EVENT_ID_LENGTH &&
            eventId.none(Char::isISOControl) &&
            observedAtEpochMillis > 0

    companion object {
        const val MAX_EVENT_ID_LENGTH = 128
    }
}

enum class GuardianAction {
    RECORD_SECURITY_EVENT,
    BLOCK_CURRENT_REQUEST,
    REQUIRE_FRESH_AUTHENTICATION,
    QUEUE_CREATOR_ALERT,
    REVOKE_AFFECTED_SESSION,
    DISABLE_SENSITIVE_CAPABILITIES,
    PRESERVE_ENCRYPTED_EVIDENCE,
    ENTER_SECURE_SHELTER,
    REQUIRE_CREATOR_RECOVERY
}

data class GuardianAssessment(
    val mode: GuardianMode,
    val actions: Set<GuardianAction>,
    val acceptedEventIds: List<String>,
    val rejectedSignalCount: Int
)

data class CreatorRecoveryEvidence(
    val freshAuthenticationSucceeded: Boolean,
    val creatorIdentityVerified: Boolean,
    val replacementDeviceApproved: Boolean,
    val securityChecksPassed: Boolean,
    val incidentReviewed: Boolean,
    val creatorApprovedRestoration: Boolean
)

/**
 * Deterministic, side-effect-free policy. A higher protection mode is never lowered
 * by evaluating new signals. Leaving shelter requires a separate verified recovery.
 */
object SecurityGuardianPolicy {

    fun assess(
        currentMode: GuardianMode,
        signals: List<GuardianSignal>
    ): GuardianAssessment {
        val accepted = signals.filter(GuardianSignal::isWellFormed)
        val rejectedCount = signals.size - accepted.size

        if (accepted.isEmpty()) {
            return GuardianAssessment(
                mode = currentMode,
                actions = if (rejectedCount > 0) setOf(GuardianAction.RECORD_SECURITY_EVENT) else emptySet(),
                acceptedEventIds = emptyList(),
                rejectedSignalCount = rejectedCount
            )
        }

        val actions = linkedSetOf(GuardianAction.RECORD_SECURITY_EVENT)
        val shelterRequired = accepted.any {
            it.severity == GuardianThreatSeverity.CRITICAL ||
                it.type == GuardianSignalType.APP_INTEGRITY_FAILURE ||
                it.type == GuardianSignalType.SECURITY_MONITORING_TAMPER
        }
        val restrictionRequired = accepted.any { it.severity == GuardianThreatSeverity.HIGH }
        val suspicious = accepted.any { it.severity == GuardianThreatSeverity.SUSPICIOUS } ||
            accepted.any { it.type == GuardianSignalType.UNTRUSTED_DEVICE }

        if (suspicious) {
            actions += GuardianAction.BLOCK_CURRENT_REQUEST
            actions += GuardianAction.REQUIRE_FRESH_AUTHENTICATION
            actions += GuardianAction.QUEUE_CREATOR_ALERT
        }

        if (restrictionRequired || shelterRequired) {
            actions += GuardianAction.REVOKE_AFFECTED_SESSION
            actions += GuardianAction.DISABLE_SENSITIVE_CAPABILITIES
            actions += GuardianAction.PRESERVE_ENCRYPTED_EVIDENCE
            actions += GuardianAction.QUEUE_CREATOR_ALERT
        }

        if (shelterRequired) {
            actions += GuardianAction.ENTER_SECURE_SHELTER
            actions += GuardianAction.REQUIRE_CREATOR_RECOVERY
        }

        val targetMode = when {
            shelterRequired -> GuardianMode.SECURE_SHELTER
            restrictionRequired -> GuardianMode.RESTRICTED
            else -> currentMode
        }

        return GuardianAssessment(
            mode = moreProtective(currentMode, targetMode),
            actions = actions,
            acceptedEventIds = accepted.map(GuardianSignal::eventId),
            rejectedSignalCount = rejectedCount
        )
    }

    /**
     * A shelter cannot be exited merely because an alert disappeared or a timer expired.
     * All checks, including explicit Creator approval, must be satisfied.
     */
    fun canRestoreNormalOperation(evidence: CreatorRecoveryEvidence): Boolean =
        evidence.freshAuthenticationSucceeded &&
            evidence.creatorIdentityVerified &&
            evidence.replacementDeviceApproved &&
            evidence.securityChecksPassed &&
            evidence.incidentReviewed &&
            evidence.creatorApprovedRestoration

    private fun moreProtective(first: GuardianMode, second: GuardianMode): GuardianMode =
        if (first.ordinal >= second.ordinal) first else second
}
