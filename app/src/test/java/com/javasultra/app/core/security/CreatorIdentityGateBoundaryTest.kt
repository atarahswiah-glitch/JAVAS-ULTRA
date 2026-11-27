package com.javasultra.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Additional boundary and adversarial cases for the client-side identity preflight. */
class CreatorIdentityGateBoundaryTest {
    private val now = Instant.parse("2026-11-26T12:00:00Z")
    private val creator = PrincipalId("creator")
    private val other = PrincipalId("other")
    private val deviceId = DeviceId("trusted-device")
    private val otherDevice = DeviceId("untrusted-device")

    private fun evaluate(
        identityId: PrincipalId = creator,
        accountState: CreatorAccountState = CreatorAccountState.ACTIVE,
        sessionPrincipal: PrincipalId = creator,
        sessionDevice: DeviceId = deviceId,
        device: DeviceId = deviceId,
        deviceState: RegisteredDeviceState = RegisteredDeviceState.VERIFIED,
        issuedAt: Instant = now.minusSeconds(60),
        expiresAt: Instant = now.plusSeconds(600),
        revokedAt: Instant? = null,
        proofPrincipal: PrincipalId = creator,
        proofStrength: AuthenticationStrength = AuthenticationStrength.PASSWORD_OR_PIN,
        factors: Int = 1,
        authenticatedAt: Instant = now.minusSeconds(30),
        localOnly: Boolean = false,
        stepUp: Boolean = false,
        maxAge: Duration = Duration.ofMinutes(5),
        at: Instant = now
    ): IdentityDecision = CreatorIdentityGate.evaluate(
        CreatorIdentitySnapshot(identityId, accountState),
        RegisteredDeviceSnapshot(device, deviceState),
        IdentitySessionSnapshot(sessionPrincipal, sessionDevice, issuedAt, expiresAt, revokedAt),
        AuthenticationProof(proofPrincipal, proofStrength, factors, authenticatedAt, localOnly),
        IdentityAccessRequest(creator, deviceId, stepUp, maxAge),
        at
    )

    private fun denied(decision: IdentityDecision): IdentityDenial {
        assertTrue("Expected denial, got $decision", decision is IdentityDecision.Denied)
        return (decision as IdentityDecision.Denied).reason
    }

    @Test fun deniesIdentitySnapshotForDifferentPrincipal() {
        assertEquals(IdentityDenial.SESSION_PRINCIPAL_MISMATCH, denied(evaluate(identityId = other)))
    }

    @Test fun deniesSessionForDifferentPrincipal() {
        assertEquals(IdentityDenial.SESSION_PRINCIPAL_MISMATCH, denied(evaluate(sessionPrincipal = other)))
    }

    @Test fun deniesSessionBoundToDifferentDevice() {
        assertEquals(IdentityDenial.SESSION_DEVICE_MISMATCH, denied(evaluate(sessionDevice = otherDevice)))
    }

    @Test fun deniesDifferentRegisteredDevice() {
        assertEquals(IdentityDenial.SESSION_DEVICE_MISMATCH, denied(evaluate(device = otherDevice)))
    }

    @Test fun deniesSessionBeforeItsIssuedAtBoundary() {
        assertEquals(
            IdentityDenial.SESSION_NOT_YET_VALID,
            denied(evaluate(issuedAt = now.plusNanos(1)))
        )
    }

    @Test fun deniesSessionAtExactExpiryBoundary() {
        assertEquals(IdentityDenial.SESSION_EXPIRED, denied(evaluate(expiresAt = now)))
    }

    @Test fun deniesAuthenticationFromFuture() {
        assertEquals(
            IdentityDenial.AUTHENTICATION_TOO_OLD,
            denied(evaluate(authenticatedAt = now.plusNanos(1)))
        )
    }

    @Test fun deniesProofBelongingToDifferentPrincipal() {
        assertEquals(
            IdentityDenial.AUTHENTICATION_PRINCIPAL_MISMATCH,
            denied(evaluate(proofPrincipal = other))
        )
    }

    @Test fun deniesMfaStepUpWhenOnlyOneFactorIsVerified() {
        assertEquals(
            IdentityDenial.INVALID_AUTHENTICATION_PROOF,
            denied(evaluate(proofStrength = AuthenticationStrength.MULTI_FACTOR, factors = 1, stepUp = true))
        )
    }

    @Test fun allowsFreshMfaAtExactMaximumAuthenticationAge() {
        assertEquals(
            IdentityDecision.Allowed,
            evaluate(
                proofStrength = AuthenticationStrength.MULTI_FACTOR,
                factors = 2,
                authenticatedAt = now.minus(Duration.ofMinutes(5)),
                stepUp = true
            )
        )
    }

    @Test fun deniesMfaOneNanosecondOlderThanMaximumAge() {
        assertEquals(
            IdentityDenial.AUTHENTICATION_TOO_OLD,
            denied(evaluate(
                proofStrength = AuthenticationStrength.MULTI_FACTOR,
                factors = 2,
                authenticatedAt = now.minus(Duration.ofMinutes(5)).minusNanos(1),
                stepUp = true
            ))
        )
    }

    @Test fun deniesEveryNonActiveAccountState() {
        listOf(
            CreatorAccountState.SUSPENDED,
            CreatorAccountState.RECOVERY_PENDING,
            CreatorAccountState.LOCKED,
            CreatorAccountState.DELETED
        ).forEach { state ->
            assertEquals(
                "Account state $state must fail closed",
                IdentityDenial.ACCOUNT_NOT_ACTIVE,
                denied(evaluate(accountState = state))
            )
        }
    }

    @Test fun rejectsSessionRevokedBeforeCurrentTime() {
        assertEquals(
            IdentityDenial.SESSION_REVOKED,
            denied(evaluate(revokedAt = now.minusNanos(1)))
        )
    }
}
