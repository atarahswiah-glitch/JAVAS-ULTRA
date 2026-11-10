package com.javasultra.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CreatorIdentityGateTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private val creator = PrincipalId("creator")
    private val deviceId = DeviceId("device")

    private fun identity(state: CreatorAccountState = CreatorAccountState.ACTIVE) =
        CreatorIdentitySnapshot(creator, state)

    private fun device(state: RegisteredDeviceState = RegisteredDeviceState.VERIFIED) =
        RegisteredDeviceSnapshot(deviceId, state)

    private fun session(revokedAt: Instant? = null) =
        IdentitySessionSnapshot(creator, deviceId, now.minusSeconds(60), now.plusSeconds(600), revokedAt)

    private fun proof(
        strength: AuthenticationStrength = AuthenticationStrength.PASSWORD_OR_PIN,
        factors: Int = 1,
        authenticatedAt: Instant = now.minusSeconds(30),
        localOnly: Boolean = false
    ) = AuthenticationProof(creator, strength, factors, authenticatedAt, localOnly)

    private fun request(stepUp: Boolean = false) =
        IdentityAccessRequest(creator, deviceId, stepUp, Duration.ofMinutes(5))

    private fun reason(result: IdentityDecision) =
        (result as IdentityDecision.Denied).reason

    @Test fun allowsActiveCreatorOnVerifiedDeviceWithCurrentSession() {
        assertEquals(
            IdentityDecision.Allowed,
            CreatorIdentityGate.evaluate(identity(), device(), session(), proof(), request(), now)
        )
    }

    @Test fun deniesSuspendedAccount() {
        assertEquals(
            IdentityDenial.ACCOUNT_NOT_ACTIVE,
            reason(CreatorIdentityGate.evaluate(identity(CreatorAccountState.SUSPENDED), device(), session(), proof(), request(), now))
        )
    }

    @Test fun deniesUnverifiedDevice() {
        assertEquals(
            IdentityDenial.DEVICE_NOT_VERIFIED,
            reason(CreatorIdentityGate.evaluate(identity(), device(RegisteredDeviceState.PENDING_VERIFICATION), session(), proof(), request(), now))
        )
    }

    @Test fun deniesRevokedDevice() {
        assertEquals(
            IdentityDenial.DEVICE_NOT_VERIFIED,
            reason(CreatorIdentityGate.evaluate(identity(), device(RegisteredDeviceState.REVOKED), session(), proof(), request(), now))
        )
    }

    @Test fun deniesRevokedSession() {
        assertEquals(
            IdentityDenial.SESSION_REVOKED,
            reason(CreatorIdentityGate.evaluate(identity(), device(), session(now), proof(), request(), now))
        )
    }

    @Test fun deniesMissingAuthenticationProof() {
        assertEquals(
            IdentityDenial.AUTHENTICATION_MISSING,
            reason(CreatorIdentityGate.evaluate(identity(), device(), session(), null, request(), now))
        )
    }

    @Test fun deniesStaleAuthenticationProof() {
        assertEquals(
            IdentityDenial.AUTHENTICATION_TOO_OLD,
            reason(CreatorIdentityGate.evaluate(identity(), device(), session(), proof(authenticatedAt = now.minusSeconds(301)), request(), now))
        )
    }

    @Test fun stepUpRequiresMultiFactorProof() {
        assertEquals(
            IdentityDenial.STEP_UP_REQUIRED,
            reason(CreatorIdentityGate.evaluate(identity(), device(), session(), proof(), request(stepUp = true), now))
        )
    }

    @Test fun localBiometricUnlockCannotAuthenticateRemoteAccess() {
        assertEquals(
            IdentityDenial.LOCAL_ONLY_AUTHENTICATION,
            reason(CreatorIdentityGate.evaluate(
                identity(), device(), session(),
                proof(AuthenticationStrength.MULTI_FACTOR, 2, localOnly = true),
                request(stepUp = true), now
            ))
        )
    }

    @Test fun localOnlyFlagCannotAuthenticateEvenWithTwoFactors() {
        assertEquals(
            IdentityDenial.LOCAL_ONLY_AUTHENTICATION,
            reason(CreatorIdentityGate.evaluate(
                identity(), device(), session(),
                proof(AuthenticationStrength.MULTI_FACTOR, 2, localOnly = true),
                request(), now
            ))
        )
    }

    @Test fun biometricStrengthCannotAuthenticateRemoteAccessWithoutLocalOnlyFlag() {
        assertEquals(
            IdentityDenial.LOCAL_ONLY_AUTHENTICATION,
            reason(CreatorIdentityGate.evaluate(
                identity(), device(), session(),
                proof(AuthenticationStrength.LOCAL_BIOMETRIC_UNLOCK, 1, localOnly = false),
                request(), now
            ))
        )
    }

    @Test fun rejectsPasswordProofWithImpossibleFactorCount() {
        assertEquals(
            IdentityDenial.INVALID_AUTHENTICATION_PROOF,
            reason(CreatorIdentityGate.evaluate(
                identity(), device(), session(),
                proof(AuthenticationStrength.PASSWORD_OR_PIN, 2),
                request(), now
            ))
        )
    }

    @Test fun rejectsMultiFactorProofWithOnlyOneVerifiedFactor() {
        assertEquals(
            IdentityDenial.INVALID_AUTHENTICATION_PROOF,
            reason(CreatorIdentityGate.evaluate(
                identity(), device(), session(),
                proof(AuthenticationStrength.MULTI_FACTOR, 1),
                request(), now
            ))
        )
    }

    @Test fun rejectsProofWithoutAnyVerifiedFactor() {
        assertEquals(
            IdentityDenial.INVALID_AUTHENTICATION_PROOF,
            reason(CreatorIdentityGate.evaluate(
                identity(), device(), session(),
                proof(AuthenticationStrength.PASSWORD_OR_PIN, 0),
                request(), now
            ))
        )
    }

    @Test fun permitsFreshTwoFactorStepUp() {
        assertEquals(
            IdentityDecision.Allowed,
            CreatorIdentityGate.evaluate(
                identity(), device(), session(),
                proof(AuthenticationStrength.MULTI_FACTOR, 2),
                request(stepUp = true), now
            )
        )
    }
}
