package com.javasultra.app.core.security

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CreatorSessionCoordinatorTest {
    private val now = Instant.parse("2026-11-24T12:00:00Z")
    private val providerSession = ProviderAuthenticationResult(
        principalId = PrincipalId("creator-1"),
        accessToken = "access-token-opaque",
        refreshToken = "refresh-token-opaque",
        issuedAt = now,
        authenticatedAt = now,
        expiresAt = now.plusSeconds(3_600),
        strength = AuthenticationStrength.MULTI_FACTOR,
        verifiedFactorCount = 2
    )

    private fun coordinator(
        vault: FakeVault = FakeVault(),
        accountState: CreatorAccountState = CreatorAccountState.ACTIVE,
        deviceState: RegisteredDeviceState = RegisteredDeviceState.VERIFIED,
        remoteRevokeSucceeds: Boolean = true,
        refreshSession: ProviderAuthenticationResult? = null,
        refreshFailure: Throwable = IllegalStateException("refresh unavailable")
    ): Triple<CreatorSessionCoordinator, FakeVault, FakeAuthentication> {
        val auth = FakeAuthentication(providerSession, remoteRevokeSucceeds, refreshSession, refreshFailure)
        val accounts = object : CreatorAccountGateway {
            override suspend fun loadCreator(accessToken: String) =
                Result.success(CreatorIdentitySnapshot(PrincipalId("creator-1"), accountState))

            override suspend fun verifyDevice(accessToken: String, deviceId: DeviceId) =
                Result.success(RegisteredDeviceSnapshot(deviceId, deviceState))
        }
        val manager = CreatorSessionCoordinator(
            authentication = auth,
            accounts = accounts,
            deviceIdentity = CreatorDeviceIdentityProvider { DeviceId("device-1") },
            vault = vault
        )
        return Triple(manager, vault, auth)
    }

    @Test fun signsInOnlyAfterIdentityAndDeviceAreVerified() = runBlocking {
        val (manager, vault, _) = coordinator()
        val secret = "creator-secret".toCharArray()

        val result = manager.signIn("creator@example.test", secret, now)

        assertTrue(result is CreatorSessionResult.Authenticated)
        assertTrue(secret.all { it == '\u0000' })
        assertTrue(vault.credential != null)
    }

    @Test fun rejectsSuspendedAccountAndDoesNotPersistSession() = runBlocking {
        val (manager, vault, auth) = coordinator(accountState = CreatorAccountState.SUSPENDED)

        val result = manager.signIn("creator@example.test", "secret".toCharArray(), now)

        assertEquals(
            CreatorSessionDenial.ACCOUNT_NOT_ACTIVE,
            (result as CreatorSessionResult.Denied).reason
        )
        assertEquals(null, vault.credential)
        assertEquals(1, auth.revocations)
    }

    @Test fun requiresDeviceVerificationBeforePersistingSession() = runBlocking {
        val (manager, vault, auth) = coordinator(deviceState = RegisteredDeviceState.PENDING_VERIFICATION)

        val result = manager.signIn("creator@example.test", "secret".toCharArray(), now)

        assertTrue(result is CreatorSessionResult.DeviceVerificationRequired)
        assertEquals(null, vault.credential)
        assertEquals(1, auth.revocations)
    }

    @Test fun restoresOnlyCurrentServerVerifiedSession() = runBlocking {
        val vault = FakeVault().apply { credential = providerSession.toCredential("device-1") }
        val (manager, _, _) = coordinator(vault)

        val result = manager.restore(now.plusSeconds(60))

        assertTrue(result is CreatorSessionResult.Authenticated)
    }

    @Test fun clearsExpiredLocalSession() = runBlocking {
        val expired = providerSession.copy(expiresAt = now)
        val vault = FakeVault().apply { credential = expired.toCredential("device-1") }
        val (manager, _, _) = coordinator(vault)

        val result = manager.restore(now.plusSeconds(1))

        assertEquals(
            CreatorSessionDenial.SESSION_EXPIRED,
            (result as CreatorSessionResult.Denied).reason
        )
        assertEquals(null, vault.credential)
    }


    @Test fun refreshesExpiredSessionWithoutElevatingAuthenticationStrength() = runBlocking {
        val expired = providerSession.copy(
            issuedAt = now.minusSeconds(7_200),
            authenticatedAt = now.minusSeconds(7_200),
            expiresAt = now
        )
        val refreshed = providerSession.copy(
            accessToken = "new-access-token",
            refreshToken = "new-refresh-token",
            issuedAt = now,
            authenticatedAt = now,
            expiresAt = now.plusSeconds(3_600)
        )
        val vault = FakeVault().apply { credential = expired.toCredential("device-1") }
        val (manager, _, _) = coordinator(vault, refreshSession = refreshed)

        val result = manager.restore(now.plusSeconds(1))

        assertTrue(result is CreatorSessionResult.Authenticated)
        assertEquals("new-access-token", vault.credential?.accessToken)
        assertEquals(AuthenticationStrength.MULTI_FACTOR, vault.credential?.strength)
        assertEquals(2, vault.credential?.verifiedFactorCount)
        assertEquals(expired.authenticatedAt, vault.credential?.authenticatedAt)
    }


    @Test fun revokedRefreshTokenClearsExpiredLocalCredential() = runBlocking {
        val expired = providerSession.copy(expiresAt = now)
        val vault = FakeVault().apply { credential = expired.toCredential("device-1") }
        val (manager, _, _) = coordinator(
            vault = vault,
            refreshFailure = CreatorAuthenticationException(CreatorAuthenticationFailureKind.SESSION_REVOKED)
        )

        val result = manager.restore(now.plusSeconds(1))

        assertEquals(CreatorSessionDenial.SESSION_REVOKED, (result as CreatorSessionResult.Denied).reason)
        assertEquals(null, vault.credential)
    }

    @Test fun temporaryRefreshFailureKeepsCredentialForRetryButDoesNotAuthenticate() = runBlocking {
        val expired = providerSession.copy(expiresAt = now)
        val vault = FakeVault().apply { credential = expired.toCredential("device-1") }
        val (manager, _, _) = coordinator(vault = vault)

        val result = manager.restore(now.plusSeconds(1))

        assertEquals(CreatorSessionDenial.SESSION_REFRESH_UNAVAILABLE, (result as CreatorSessionResult.Denied).reason)
        assertEquals(expired.accessToken, vault.credential?.accessToken)
    }
    @Test fun signOutClearsLocalCredentialEvenWhenRemoteRevocationFails() = runBlocking {
        val vault = FakeVault().apply { credential = providerSession.toCredential("device-1") }
        val (manager, _, _) = coordinator(vault, remoteRevokeSucceeds = false)

        val result = manager.signOut()

        assertTrue(result is CreatorSessionResult.SignedOut)
        assertFalse((result as CreatorSessionResult.SignedOut).serverRevocationConfirmed)
        assertEquals(null, vault.credential)
    }

    @Test fun signOutClearsUnreadableVaultAndReportsRevocationUnconfirmed() = runBlocking {
        val vault = FakeVault().apply {
            credential = providerSession.toCredential("device-1")
            readFailure = true
        }
        val (manager, _, auth) = coordinator(vault)

        val result = manager.signOut()

        assertTrue(result is CreatorSessionResult.SignedOut)
        assertFalse((result as CreatorSessionResult.SignedOut).serverRevocationConfirmed)
        assertEquals(null, vault.credential)
        assertEquals(0, auth.revocations)
    }

    private class FakeAuthentication(
        private val session: ProviderAuthenticationResult,
        private val revokeSucceeds: Boolean,
        private val refreshedSession: ProviderAuthenticationResult?,
        private val refreshFailure: Throwable
    ) : CreatorAuthenticationGateway {
        var revocations = 0
        override suspend fun authenticate(identifier: String, secret: CharArray) =
            Result.success(session)

        override suspend fun refresh(refreshToken: String): Result<ProviderAuthenticationResult> =
            refreshedSession?.let { Result.success(it) }
                ?: Result.failure(refreshFailure)

        override suspend fun revoke(accessToken: String, refreshToken: String?): Result<Unit> {
            revocations++
            return if (revokeSucceeds) Result.success(Unit)
            else Result.failure(IllegalStateException("remote revoke unavailable"))
        }
    }

    private class FakeVault : CreatorSessionVault {
        var credential: CreatorSessionCredential? = null
        var readFailure: Boolean = false

        override fun read(): CreatorSessionVaultResult<CreatorSessionCredential?> =
            if (readFailure) CreatorSessionVaultResult.Failure(CreatorSessionVaultFailure.DECRYPTION_FAILED)
            else CreatorSessionVaultResult.Success(credential)

        override fun write(credential: CreatorSessionCredential): CreatorSessionVaultResult<Unit> {
            if (!credential.validate()) {
                return CreatorSessionVaultResult.Failure(CreatorSessionVaultFailure.INVALID_CREDENTIAL)
            }
            this.credential = credential
            return CreatorSessionVaultResult.Success(Unit)
        }

        override fun clear(): CreatorSessionVaultResult<Unit> {
            credential = null
            return CreatorSessionVaultResult.Success(Unit)
        }
    }

    private fun ProviderAuthenticationResult.toCredential(deviceId: String) = CreatorSessionCredential(
        principalId = principalId.value,
        deviceId = deviceId,
        accessToken = accessToken,
        refreshToken = refreshToken,
        issuedAt = issuedAt,
        authenticatedAt = authenticatedAt,
        expiresAt = expiresAt,
        strength = strength,
        verifiedFactorCount = verifiedFactorCount
    )
}
