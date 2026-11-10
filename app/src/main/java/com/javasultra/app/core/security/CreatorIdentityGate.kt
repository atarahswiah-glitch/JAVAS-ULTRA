package com.javasultra.app.core.security

import java.time.Duration
import java.time.Instant

enum class CreatorAccountState {
    ACTIVE,
    SUSPENDED,
    RECOVERY_PENDING,
    LOCKED,
    DELETED
}

enum class RegisteredDeviceState {
    PENDING_VERIFICATION,
    VERIFIED,
    REVOKED
}

enum class AuthenticationStrength {
    NONE,
    PASSWORD_OR_PIN,
    MULTI_FACTOR,
    LOCAL_BIOMETRIC_UNLOCK
}

data class CreatorIdentitySnapshot(
    val principalId: PrincipalId,
    val accountState: CreatorAccountState
)

data class RegisteredDeviceSnapshot(
    val deviceId: DeviceId,
    val state: RegisteredDeviceState
)

data class IdentitySessionSnapshot(
    val principalId: PrincipalId,
    val deviceId: DeviceId,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant? = null
)

data class AuthenticationProof(
    val principalId: PrincipalId,
    val strength: AuthenticationStrength,
    val verifiedFactorCount: Int,
    val authenticatedAt: Instant,
    /** A local biometric unlock must never be treated as remote MFA. */
    val localOnly: Boolean = false
)

data class IdentityAccessRequest(
    val principalId: PrincipalId,
    val deviceId: DeviceId,
    val requiresStepUp: Boolean = false,
    val maximumAuthenticationAge: Duration = Duration.ofMinutes(5)
)

enum class IdentityDenial {
    ACCOUNT_NOT_ACTIVE,
    SESSION_PRINCIPAL_MISMATCH,
    SESSION_DEVICE_MISMATCH,
    SESSION_NOT_YET_VALID,
    SESSION_EXPIRED,
    SESSION_REVOKED,
    DEVICE_NOT_VERIFIED,
    AUTHENTICATION_MISSING,
    AUTHENTICATION_PRINCIPAL_MISMATCH,
    LOCAL_ONLY_AUTHENTICATION,
    AUTHENTICATION_TOO_OLD,
    STEP_UP_REQUIRED
}

sealed interface IdentityDecision {
    data object Allowed : IdentityDecision
    data class Denied(val reason: IdentityDenial) : IdentityDecision
}

/**
 * Deterministic client/domain preflight only. A trusted server must independently
 * load account, device, session and authentication state before protected operations.
 */
object CreatorIdentityGate {
    fun evaluate(
        identity: CreatorIdentitySnapshot,
        device: RegisteredDeviceSnapshot,
        session: IdentitySessionSnapshot,
        proof: AuthenticationProof?,
        request: IdentityAccessRequest,
        now: Instant
    ): IdentityDecision {
        if (identity.accountState != CreatorAccountState.ACTIVE) {
            return denied(IdentityDenial.ACCOUNT_NOT_ACTIVE)
        }
        if (identity.principalId != request.principalId ||
            session.principalId != request.principalId
        ) {
            return denied(IdentityDenial.SESSION_PRINCIPAL_MISMATCH)
        }
        if (device.deviceId != request.deviceId || session.deviceId != request.deviceId) {
            return denied(IdentityDenial.SESSION_DEVICE_MISMATCH)
        }
        if (now.isBefore(session.issuedAt)) return denied(IdentityDenial.SESSION_NOT_YET_VALID)
        if (!now.isBefore(session.expiresAt)) return denied(IdentityDenial.SESSION_EXPIRED)
        if (session.revokedAt != null && !now.isBefore(session.revokedAt)) {
            return denied(IdentityDenial.SESSION_REVOKED)
        }
        if (device.state != RegisteredDeviceState.VERIFIED) {
            return denied(IdentityDenial.DEVICE_NOT_VERIFIED)
        }
        if (proof == null || proof.strength == AuthenticationStrength.NONE) {
            return denied(IdentityDenial.AUTHENTICATION_MISSING)
        }
        if (proof.principalId != request.principalId) {
            return denied(IdentityDenial.AUTHENTICATION_PRINCIPAL_MISMATCH)
        }
        // A local biometric can unlock local keys, but cannot authenticate a remote session.
        if (proof.localOnly || proof.strength == AuthenticationStrength.LOCAL_BIOMETRIC_UNLOCK) {
            return denied(IdentityDenial.LOCAL_ONLY_AUTHENTICATION)
        }
        if (now.isBefore(proof.authenticatedAt) ||
            Duration.between(proof.authenticatedAt, now) > request.maximumAuthenticationAge
        ) {
            return denied(IdentityDenial.AUTHENTICATION_TOO_OLD)
        }
        if (request.requiresStepUp &&
            (proof.localOnly ||
                proof.strength != AuthenticationStrength.MULTI_FACTOR ||
                proof.verifiedFactorCount < 2)
        ) {
            return denied(IdentityDenial.STEP_UP_REQUIRED)
        }
        return IdentityDecision.Allowed
    }

    private fun denied(reason: IdentityDenial) = IdentityDecision.Denied(reason)
}
