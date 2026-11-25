package com.javasultra.app.core.agent

import com.javasultra.app.core.security.CreatorSessionResult
import java.time.Instant

/**
 * Converts only a successful, coordinator-issued session result into a short-lived local
 * conversation proof. This is an in-process guard, not server authentication or attestation.
 */
object CreatorConversationAuthorizationFactory {
    fun fromVerifiedSession(
        result: CreatorSessionResult,
        now: Instant
    ): CreatorConversationAuthorization? {
        val session = result as? CreatorSessionResult.Authenticated ?: return null
        if (session.principalId.value.isBlank() ||
            session.deviceId.value.isBlank() ||
            session.verifiedFactorCount !in 1..3 ||
            now.isBefore(session.authenticatedAt) ||
            !now.isBefore(session.expiresAt) ||
            session.expiresAt.isAfter(now.plus(MAX_AUTHORIZATION_LIFETIME))
        ) return null

        return CreatorConversationAuthorization(
            principalId = session.principalId.value,
            deviceId = session.deviceId.value,
            authenticatedAt = session.authenticatedAt,
            expiresAt = session.expiresAt,
            verifiedFactorCount = session.verifiedFactorCount,
            localOnly = false
        )
    }

    private val MAX_AUTHORIZATION_LIFETIME = java.time.Duration.ofHours(24)
}
