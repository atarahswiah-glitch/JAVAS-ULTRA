package com.javasultra.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityGuardianPolicyTest {

    private fun signal(
        id: String = "event-001",
        type: GuardianSignalType = GuardianSignalType.AUTHENTICATION_FAILURE_BURST,
        severity: GuardianThreatSeverity = GuardianThreatSeverity.SUSPICIOUS
    ) = GuardianSignal(
        eventId = id,
        type = type,
        severity = severity,
        observedAtEpochMillis = 1_800_000_000_000
    )

    @Test
    fun suspiciousActivityBlocksRequestAndRequiresFreshAuthentication() {
        val result = SecurityGuardianPolicy.assess(
            GuardianMode.NORMAL,
            listOf(signal(type = GuardianSignalType.UNTRUSTED_DEVICE))
        )

        assertEquals(GuardianMode.NORMAL, result.mode)
        assertTrue(GuardianAction.BLOCK_CURRENT_REQUEST in result.actions)
        assertTrue(GuardianAction.REQUIRE_FRESH_AUTHENTICATION in result.actions)
        assertTrue(GuardianAction.QUEUE_CREATOR_ALERT in result.actions)
        assertFalse(GuardianAction.ENTER_SECURE_SHELTER in result.actions)
    }

    @Test
    fun highSeverityActivityRestrictsSensitiveCapabilities() {
        val result = SecurityGuardianPolicy.assess(
            GuardianMode.NORMAL,
            listOf(signal(severity = GuardianThreatSeverity.HIGH))
        )

        assertEquals(GuardianMode.RESTRICTED, result.mode)
        assertTrue(GuardianAction.REVOKE_AFFECTED_SESSION in result.actions)
        assertTrue(GuardianAction.DISABLE_SENSITIVE_CAPABILITIES in result.actions)
        assertTrue(GuardianAction.PRESERVE_ENCRYPTED_EVIDENCE in result.actions)
    }

    @Test
    fun criticalIntegrityFailureEntersSecureShelter() {
        val result = SecurityGuardianPolicy.assess(
            GuardianMode.NORMAL,
            listOf(
                signal(
                    type = GuardianSignalType.SECURITY_MONITORING_TAMPER,
                    severity = GuardianThreatSeverity.HIGH
                )
            )
        )

        assertEquals(GuardianMode.SECURE_SHELTER, result.mode)
        assertTrue(GuardianAction.ENTER_SECURE_SHELTER in result.actions)
        assertTrue(GuardianAction.REQUIRE_CREATOR_RECOVERY in result.actions)
    }

    @Test
    fun assessmentNeverDowngradesAnExistingProtectionMode() {
        val result = SecurityGuardianPolicy.assess(
            GuardianMode.SECURE_SHELTER,
            listOf(signal(severity = GuardianThreatSeverity.INFORMATIONAL))
        )

        assertEquals(GuardianMode.SECURE_SHELTER, result.mode)
    }

    @Test
    fun malformedSignalsAreNotUsedAsThreatEvidence() {
        val malformed = signal(id = "bad\nidentifier")
        val result = SecurityGuardianPolicy.assess(GuardianMode.NORMAL, listOf(malformed))

        assertEquals(GuardianMode.NORMAL, result.mode)
        assertEquals(1, result.rejectedSignalCount)
        assertTrue(result.acceptedEventIds.isEmpty())
    }

    @Test
    fun normalOperationRequiresEveryCreatorRecoveryCheck() {
        val complete = CreatorRecoveryEvidence(
            freshAuthenticationSucceeded = true,
            creatorIdentityVerified = true,
            replacementDeviceApproved = true,
            securityChecksPassed = true,
            incidentReviewed = true,
            creatorApprovedRestoration = true
        )
        assertTrue(SecurityGuardianPolicy.canRestoreNormalOperation(complete))

        assertFalse(
            SecurityGuardianPolicy.canRestoreNormalOperation(
                complete.copy(creatorApprovedRestoration = false)
            )
        )
        assertFalse(
            SecurityGuardianPolicy.canRestoreNormalOperation(
                complete.copy(replacementDeviceApproved = false)
            )
        )
    }
}
