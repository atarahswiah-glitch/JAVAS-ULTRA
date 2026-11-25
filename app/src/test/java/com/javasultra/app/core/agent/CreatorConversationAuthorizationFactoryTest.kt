package com.javasultra.app.core.agent

import com.javasultra.app.core.security.CreatorSessionResult
import com.javasultra.app.core.security.DeviceId
import com.javasultra.app.core.security.PrincipalId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class CreatorConversationAuthorizationFactoryTest {
    private val now = Instant.parse("2026-11-25T12:00:00Z")

    private fun authenticated(
        authenticatedAt: Instant = now.minusSeconds(60),
        expiresAt: Instant = now.plusSeconds(3_600),
        factors: Int = 2
    ) = CreatorSessionResult.Authenticated(
        principalId = PrincipalId("creator-1"),
        deviceId = DeviceId("device-1"),
        authenticatedAt = authenticatedAt,
        expiresAt = expiresAt,
        verifiedFactorCount = factors
    )

    @Test fun createsAuthorizationOnlyFromCurrentVerifiedSession() {
        val authorization = CreatorConversationAuthorizationFactory.fromVerifiedSession(authenticated(), now)

        assertNotNull(authorization)
        assertEquals("creator-1", authorization?.principalId)
        assertEquals("device-1", authorization?.deviceId)
        assertEquals(2, authorization?.verifiedFactorCount)
        assertFalse(authorization!!.localOnly)
    }

    @Test fun rejectsExpiredFutureDatedOrOverlongSession() {
        assertNull(
            CreatorConversationAuthorizationFactory.fromVerifiedSession(
                authenticated(expiresAt = now),
                now
            )
        )
        assertNull(
            CreatorConversationAuthorizationFactory.fromVerifiedSession(
                authenticated(authenticatedAt = now.plusSeconds(1)),
                now
            )
        )
        assertNull(
            CreatorConversationAuthorizationFactory.fromVerifiedSession(
                authenticated(expiresAt = now.plusSeconds(24 * 60 * 60 + 1)),
                now
            )
        )
    }

    @Test fun rejectsNonAuthenticatedSessionResults() {
        assertNull(CreatorConversationAuthorizationFactory.fromVerifiedSession(CreatorSessionResult.NoSession, now))
        assertNull(
            CreatorConversationAuthorizationFactory.fromVerifiedSession(
                CreatorSessionResult.Denied(com.javasultra.app.core.security.CreatorSessionDenial.AUTHENTICATION_FAILED),
                now
            )
        )
    }

    @Test fun rejectsInvalidFactorCount() {
        assertNull(
            CreatorConversationAuthorizationFactory.fromVerifiedSession(
                authenticated(factors = 0),
                now
            )
        )
    }
}
