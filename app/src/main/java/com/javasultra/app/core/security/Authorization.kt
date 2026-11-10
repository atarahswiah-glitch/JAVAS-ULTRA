package com.javasultra.app.core.security

import java.time.Instant

@JvmInline
value class PrincipalId(val value: String)

@JvmInline
value class DeviceId(val value: String)

@JvmInline
value class Capability(val value: String)

@JvmInline
value class ResourceScope(val value: String)

data class AuthenticatedSession(
    val principalId: PrincipalId,
    val deviceId: DeviceId,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant? = null,
    val authenticated: Boolean = true,
    val deviceRegistered: Boolean = true
)

data class CapabilityGrant(
    val principalId: PrincipalId,
    val capability: Capability,
    val scope: ResourceScope,
    val validFrom: Instant,
    val validUntil: Instant,
    val revokedAt: Instant? = null
)

data class AuthorizationRequest(
    val principalId: PrincipalId,
    val capability: Capability,
    val targetScope: ResourceScope
)

enum class AuthorizationDenial {
    NOT_AUTHENTICATED,
    PRINCIPAL_MISMATCH,
    SESSION_NOT_YET_VALID,
    SESSION_EXPIRED,
    SESSION_REVOKED,
    DEVICE_NOT_REGISTERED,
    CAPABILITY_NOT_GRANTED,
    RESOURCE_SCOPE_MISMATCH,
    GRANT_NOT_YET_VALID,
    GRANT_EXPIRED,
    GRANT_REVOKED
}

sealed interface AuthorizationDecision {
    data object Allowed : AuthorizationDecision
    data class Denied(val reason: AuthorizationDenial) : AuthorizationDecision
}

/**
 * Deterministic domain policy for unit tests and client-side preflight only.
 * A trusted server must repeat every authorisation check before remote side effects.
 */
object CapabilityAuthorizer {
    fun evaluate(
        session: AuthenticatedSession,
        grants: Collection<CapabilityGrant>,
        request: AuthorizationRequest,
        now: Instant
    ): AuthorizationDecision {
        if (!session.authenticated) return denied(AuthorizationDenial.NOT_AUTHENTICATED)
        if (session.principalId != request.principalId) return denied(AuthorizationDenial.PRINCIPAL_MISMATCH)
        if (now.isBefore(session.issuedAt)) return denied(AuthorizationDenial.SESSION_NOT_YET_VALID)
        if (!now.isBefore(session.expiresAt)) return denied(AuthorizationDenial.SESSION_EXPIRED)
        if (session.revokedAt != null && !now.isBefore(session.revokedAt)) {
            return denied(AuthorizationDenial.SESSION_REVOKED)
        }
        if (!session.deviceRegistered) return denied(AuthorizationDenial.DEVICE_NOT_REGISTERED)

        val matchingCapability = grants.filter {
            it.principalId == request.principalId && it.capability == request.capability
        }
        if (matchingCapability.isEmpty()) return denied(AuthorizationDenial.CAPABILITY_NOT_GRANTED)

        val matchingScope = matchingCapability.filter { it.scope == request.targetScope }
        if (matchingScope.isEmpty()) return denied(AuthorizationDenial.RESOURCE_SCOPE_MISMATCH)

        val activeGrant = matchingScope.firstOrNull {
            !now.isBefore(it.validFrom) &&
                now.isBefore(it.validUntil) &&
                (it.revokedAt == null || now.isBefore(it.revokedAt))
        }
        if (activeGrant != null) return AuthorizationDecision.Allowed

        val nonRevoked = matchingScope.filter { it.revokedAt == null || now.isBefore(it.revokedAt) }
        if (nonRevoked.isEmpty()) return denied(AuthorizationDenial.GRANT_REVOKED)
        if (nonRevoked.none { !now.isBefore(it.validFrom) }) {
            return denied(AuthorizationDenial.GRANT_NOT_YET_VALID)
        }
        if (nonRevoked.none { now.isBefore(it.validUntil) }) {
            return denied(AuthorizationDenial.GRANT_EXPIRED)
        }
        return denied(AuthorizationDenial.GRANT_REVOKED)
    }

    private fun denied(reason: AuthorizationDenial) = AuthorizationDecision.Denied(reason)
}

enum class ProtectedActionState {
    PROPOSED,
    VALIDATED,
    AWAITING_APPROVAL,
    AUTHORIZED,
    EXECUTING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    UNKNOWN
}

/**
 * Immutable approval snapshot. This local match is only a preflight check;
 * the trusted server must atomically consume approval with the action claim
 * to guarantee single-use behaviour under concurrent requests.
 */
data class ApprovalBinding(
    val actionId: String,
    val canonicalPayloadDigest: String,
    val approver: PrincipalId,
    val approvedAt: Instant,
    val expiresAt: Instant,
    val consumedAt: Instant? = null
) {
    fun matches(actionId: String, payloadDigest: String, now: Instant): Boolean =
        this.actionId == actionId &&
            canonicalPayloadDigest == payloadDigest &&
            !now.isBefore(approvedAt) &&
            now.isBefore(expiresAt) &&
            consumedAt == null
}

/**
 * Minimal audit record shape. Callers must whitelist safe metadata and must never
 * attach credentials, tokens, private keys, biometric material or full private memory.
 */
data class SecurityAuditEvent(
    val eventId: String,
    val actor: PrincipalId,
    val device: DeviceId?,
    val action: String,
    val target: String,
    val outcome: AuditOutcome,
    val occurredAt: Instant,
    val correlationId: String,
    val policyVersion: String,
    val safeMetadata: Map<String, String> = emptyMap()
)

enum class AuditOutcome {
    ALLOWED,
    DENIED,
    APPROVED,
    REJECTED,
    STARTED,
    SUCCEEDED,
    FAILED,
    UNKNOWN
}

fun ProtectedActionState.canTransitionTo(next: ProtectedActionState): Boolean = when (this) {
    ProtectedActionState.PROPOSED ->
        next == ProtectedActionState.VALIDATED || next == ProtectedActionState.CANCELLED
    ProtectedActionState.VALIDATED ->
        next == ProtectedActionState.AWAITING_APPROVAL ||
            next == ProtectedActionState.AUTHORIZED ||
            next == ProtectedActionState.CANCELLED
    ProtectedActionState.AWAITING_APPROVAL ->
        next == ProtectedActionState.AUTHORIZED || next == ProtectedActionState.CANCELLED
    ProtectedActionState.AUTHORIZED ->
        next == ProtectedActionState.EXECUTING || next == ProtectedActionState.CANCELLED
    ProtectedActionState.EXECUTING ->
        next == ProtectedActionState.SUCCEEDED ||
            next == ProtectedActionState.FAILED ||
            next == ProtectedActionState.UNKNOWN
    ProtectedActionState.SUCCEEDED,
    ProtectedActionState.FAILED,
    ProtectedActionState.CANCELLED,
    ProtectedActionState.UNKNOWN -> false
}
