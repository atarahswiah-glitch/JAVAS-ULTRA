package com.javasultra.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CapabilityAuthorizerTest {
    private val now = Instant.parse("2026-11-09T09:00:00Z")
    private val creator = PrincipalId("creator-1")
    private val device = DeviceId("device-1")
    private val capability = Capability("memory.read")
    private val scope = ResourceScope("creator-private-memory")

    private fun session(
        authenticated: Boolean = true,
        registered: Boolean = true,
        revokedAt: Instant? = null
    ) = AuthenticatedSession(
        principalId = creator,
        deviceId = device,
        issuedAt = now.minusSeconds(60),
        expiresAt = now.plusSeconds(600),
        revokedAt = revokedAt,
        authenticated = authenticated,
        deviceRegistered = registered
    )

    private fun grant(
        grantScope: ResourceScope = scope,
        validFrom: Instant = now.minusSeconds(60),
        validUntil: Instant = now.plusSeconds(600),
        revokedAt: Instant? = null
    ) = CapabilityGrant(creator, capability, grantScope, validFrom, validUntil, revokedAt)

    private fun request(principal: PrincipalId = creator) =
        AuthorizationRequest(principal, capability, scope)

    @Test fun allowsActiveSessionWithExactCapabilityAndScope() {
        assertEquals(
            AuthorizationDecision.Allowed,
            CapabilityAuthorizer.evaluate(session(), listOf(grant()), request(), now)
        )
    }

    @Test fun deniesUnauthenticatedSession() {
        assertEquals(
            AuthorizationDenial.NOT_AUTHENTICATED,
            (CapabilityAuthorizer.evaluate(session(authenticated = false), listOf(grant()), request(), now)
                as AuthorizationDecision.Denied).reason
        )
    }

    @Test fun deniesUnregisteredDevice() {
        assertEquals(
            AuthorizationDenial.DEVICE_NOT_REGISTERED,
            (CapabilityAuthorizer.evaluate(session(registered = false), listOf(grant()), request(), now)
                as AuthorizationDecision.Denied).reason
        )
    }

    @Test fun deniesPrincipalMismatch() {
        assertEquals(
            AuthorizationDenial.PRINCIPAL_MISMATCH,
            (CapabilityAuthorizer.evaluate(session(), listOf(grant()), request(PrincipalId("other")), now)
                as AuthorizationDecision.Denied).reason
        )
    }

    @Test fun deniesWrongResourceScope() {
        assertEquals(
            AuthorizationDenial.RESOURCE_SCOPE_MISMATCH,
            (CapabilityAuthorizer.evaluate(session(), listOf(grant(ResourceScope("other"))), request(), now)
                as AuthorizationDecision.Denied).reason
        )
    }

    @Test fun deniesExpiredSession() {
        val expired = session().copy(expiresAt = now)
        assertEquals(
            AuthorizationDenial.SESSION_EXPIRED,
            (CapabilityAuthorizer.evaluate(expired, listOf(grant()), request(), now)
                as AuthorizationDecision.Denied).reason
        )
    }

    @Test fun deniesRevokedSession() {
        assertEquals(
            AuthorizationDenial.SESSION_REVOKED,
            (CapabilityAuthorizer.evaluate(session(revokedAt = now), listOf(grant()), request(), now)
                as AuthorizationDecision.Denied).reason
        )
    }

    @Test fun approvalIsBoundToExactPayloadAndExpiry() {
        val approval = ApprovalBinding(
            actionId = "action-1",
            canonicalPayloadDigest = "digest-A",
            approver = creator,
            approvedAt = now.minusSeconds(1),
            expiresAt = now.plusSeconds(30)
        )
        assertTrue(approval.matches("action-1", "digest-A", now))
        assertFalse(approval.matches("action-1", "digest-B", now))
        assertFalse(approval.matches("action-2", "digest-A", now))
        assertFalse(approval.matches("action-1", "digest-A", now.plusSeconds(30)))
    }

    @Test fun consumedApprovalCannotBeReused() {
        val approval = ApprovalBinding(
            actionId = "action-1",
            canonicalPayloadDigest = "digest-A",
            approver = creator,
            approvedAt = now.minusSeconds(1),
            expiresAt = now.plusSeconds(30),
            consumedAt = now
        )
        assertFalse(approval.matches("action-1", "digest-A", now))
    }
}
