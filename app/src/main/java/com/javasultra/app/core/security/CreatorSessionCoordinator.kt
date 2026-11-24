package com.javasultra.app.core.security

import java.time.Duration
import java.time.Instant
import java.util.concurrent.CancellationException

/** Provider response. Tokens must never be copied into UI state, logs or audit metadata. */
data class ProviderAuthenticationResult(
    val principalId: PrincipalId,
    val accessToken: String,
    val refreshToken: String?,
    val issuedAt: Instant,
    val authenticatedAt: Instant,
    val expiresAt: Instant,
    val strength: AuthenticationStrength,
    val verifiedFactorCount: Int
)

interface CreatorAuthenticationGateway {
    suspend fun authenticate(
        identifier: String,
        secret: CharArray
    ): Result<ProviderAuthenticationResult>

    suspend fun refresh(refreshToken: String): Result<ProviderAuthenticationResult>
    suspend fun revoke(accessToken: String, refreshToken: String?): Result<Unit>
}

interface CreatorAccountGateway {
    suspend fun loadCreator(accessToken: String): Result<CreatorIdentitySnapshot>
    suspend fun verifyDevice(accessToken: String, deviceId: DeviceId): Result<RegisteredDeviceSnapshot>
}

fun interface CreatorDeviceIdentityProvider {
    /** Return a random app-scoped device ID; do not use an IMEI, serial number or advertising ID. */
    fun getOrCreateDeviceId(): DeviceId
}

enum class CreatorSessionDenial {
    INVALID_INPUT,
    AUTHENTICATION_FAILED,
    INVALID_PROVIDER_SESSION,
    IDENTITY_SERVICE_UNAVAILABLE,
    ACCOUNT_NOT_ACTIVE,
    DEVICE_SERVICE_UNAVAILABLE,
    DEVICE_NOT_VERIFIED,
    IDENTITY_POLICY_DENIED,
    SESSION_EXPIRED,
    SESSION_REFRESH_UNAVAILABLE,
    SESSION_VAULT_UNAVAILABLE,
    SESSION_VAULT_WRITE_FAILED,
    SESSION_VAULT_CLEAR_FAILED
}

sealed interface CreatorSessionResult {
    data class Authenticated(
        val principalId: PrincipalId,
        val deviceId: DeviceId,
        val expiresAt: Instant
    ) : CreatorSessionResult

    data class DeviceVerificationRequired(val deviceId: DeviceId) : CreatorSessionResult
    data class Denied(val reason: CreatorSessionDenial) : CreatorSessionResult
    data class SignedOut(val serverRevocationConfirmed: Boolean) : CreatorSessionResult
    data object NoSession : CreatorSessionResult
}

/**
 * Coordinates provider authentication, trusted identity/device checks and encrypted local
 * session persistence. The client performs preflight only; every protected server request must
 * independently validate the access token, account, device, grants and policy.
 */
class CreatorSessionCoordinator(
    private val authentication: CreatorAuthenticationGateway,
    private val accounts: CreatorAccountGateway,
    private val deviceIdentity: CreatorDeviceIdentityProvider,
    private val vault: CreatorSessionVault
) {

    suspend fun signIn(
        identifier: String,
        secret: CharArray,
        now: Instant
    ): CreatorSessionResult {
        if (identifier.isBlank() || identifier.length > 320 || secret.isEmpty() || secret.size > 1_024) {
            secret.fill('\u0000')
            return CreatorSessionResult.Denied(CreatorSessionDenial.INVALID_INPUT)
        }

        val providerResult = try {
            authentication.authenticate(identifier.trim(), secret)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Authentication provider unavailable"))
        } finally {
            secret.fill('\u0000')
        }
        val providerSession = providerResult.getOrElse {
            return CreatorSessionResult.Denied(CreatorSessionDenial.AUTHENTICATION_FAILED)
        }

        val deviceId = try {
            deviceIdentity.getOrCreateDeviceId()
        } catch (_: Exception) {
            revokeQuietly(providerSession.accessToken, providerSession.refreshToken)
            return CreatorSessionResult.Denied(CreatorSessionDenial.INVALID_INPUT)
        }
        val credential = CreatorSessionCredential(
            principalId = providerSession.principalId.value,
            deviceId = deviceId.value,
            accessToken = providerSession.accessToken,
            refreshToken = providerSession.refreshToken,
            issuedAt = providerSession.issuedAt,
            authenticatedAt = providerSession.authenticatedAt,
            expiresAt = providerSession.expiresAt,
            strength = providerSession.strength,
            verifiedFactorCount = providerSession.verifiedFactorCount
        )
        if (!credential.validate() || now.isBefore(credential.issuedAt) || !now.isBefore(credential.expiresAt)) {
            revokeQuietly(providerSession.accessToken)
            return CreatorSessionResult.Denied(CreatorSessionDenial.INVALID_PROVIDER_SESSION)
        }

        val identity = try {
            accounts.loadCreator(credential.accessToken).getOrElse {
                revokeQuietly(credential.accessToken, credential.refreshToken)
                return CreatorSessionResult.Denied(CreatorSessionDenial.IDENTITY_SERVICE_UNAVAILABLE)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            revokeQuietly(credential.accessToken)
            return CreatorSessionResult.Denied(CreatorSessionDenial.IDENTITY_SERVICE_UNAVAILABLE)
        }
        if (identity.principalId != providerSession.principalId ||
            identity.accountState != CreatorAccountState.ACTIVE
        ) {
            revokeQuietly(credential.accessToken)
            return CreatorSessionResult.Denied(CreatorSessionDenial.ACCOUNT_NOT_ACTIVE)
        }

        val device = try {
            accounts.verifyDevice(credential.accessToken, deviceId).getOrElse {
                revokeQuietly(credential.accessToken)
                return CreatorSessionResult.Denied(CreatorSessionDenial.DEVICE_SERVICE_UNAVAILABLE)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            revokeQuietly(credential.accessToken)
            return CreatorSessionResult.Denied(CreatorSessionDenial.DEVICE_SERVICE_UNAVAILABLE)
        }
        if (device.deviceId != deviceId) {
            revokeQuietly(credential.accessToken)
            return CreatorSessionResult.Denied(CreatorSessionDenial.DEVICE_NOT_VERIFIED)
        }
        if (device.state != RegisteredDeviceState.VERIFIED) {
            revokeQuietly(credential.accessToken)
            return CreatorSessionResult.DeviceVerificationRequired(deviceId)
        }

        val decision = evaluateCredential(credential, identity, device, now, Duration.ofMinutes(5))
        if (decision != IdentityDecision.Allowed) {
            revokeQuietly(credential.accessToken)
            return CreatorSessionResult.Denied(CreatorSessionDenial.IDENTITY_POLICY_DENIED)
        }

        return when (vault.write(credential)) {
            is CreatorSessionVaultResult.Success ->
                CreatorSessionResult.Authenticated(providerSession.principalId, deviceId, credential.expiresAt)
            is CreatorSessionVaultResult.Failure -> {
                revokeQuietly(credential.accessToken)
                CreatorSessionResult.Denied(CreatorSessionDenial.SESSION_VAULT_WRITE_FAILED)
            }
        }
    }

    /** Revalidates the stored session with the trusted identity and device services. */
    suspend fun restore(now: Instant): CreatorSessionResult {
        val credential = when (val stored = vault.read()) {
            is CreatorSessionVaultResult.Success -> stored.value ?: return CreatorSessionResult.NoSession
            is CreatorSessionVaultResult.Failure ->
                return CreatorSessionResult.Denied(CreatorSessionDenial.SESSION_VAULT_UNAVAILABLE)
        }
        if (!credential.validate()) {
            vault.clear()
            return CreatorSessionResult.Denied(CreatorSessionDenial.SESSION_EXPIRED)
        }
        if (!now.isBefore(credential.expiresAt)) {
            val refreshToken = credential.refreshToken
                ?: run {
                    vault.clear()
                    return CreatorSessionResult.Denied(CreatorSessionDenial.SESSION_EXPIRED)
                }
            val refreshed = try {
                authentication.refresh(refreshToken).getOrElse {
                    return CreatorSessionResult.Denied(CreatorSessionDenial.SESSION_REFRESH_UNAVAILABLE)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return CreatorSessionResult.Denied(CreatorSessionDenial.SESSION_REFRESH_UNAVAILABLE)
            }
            val refreshedCredential = CreatorSessionCredential(
                principalId = refreshed.principalId.value,
                deviceId = credential.deviceId,
                accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken,
                issuedAt = refreshed.issuedAt,
                authenticatedAt = credential.authenticatedAt,
                expiresAt = refreshed.expiresAt,
                strength = credential.strength,
                verifiedFactorCount = credential.verifiedFactorCount
            )
            if (refreshed.principalId.value != credential.principalId ||
                refreshed.strength != credential.strength ||
                refreshed.verifiedFactorCount != credential.verifiedFactorCount ||
                refreshed.issuedAt.isBefore(credential.issuedAt) ||
                !refreshedCredential.validate() ||
                now.isBefore(refreshedCredential.issuedAt) ||
                !now.isBefore(refreshedCredential.expiresAt)
            ) {
                revokeQuietly(refreshed.accessToken, refreshed.refreshToken)
                vault.clear()
                return CreatorSessionResult.Denied(CreatorSessionDenial.IDENTITY_POLICY_DENIED)
            }
            if (vault.write(refreshedCredential) is CreatorSessionVaultResult.Failure) {
                return CreatorSessionResult.Denied(CreatorSessionDenial.SESSION_VAULT_WRITE_FAILED)
            }
            credential = refreshedCredential
        }

        val identity = try {
            accounts.loadCreator(credential.accessToken).getOrElse {
                return CreatorSessionResult.Denied(CreatorSessionDenial.IDENTITY_SERVICE_UNAVAILABLE)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return CreatorSessionResult.Denied(CreatorSessionDenial.IDENTITY_SERVICE_UNAVAILABLE)
        }
        if (identity.principalId.value != credential.principalId ||
            identity.accountState != CreatorAccountState.ACTIVE
        ) {
            vault.clear()
            return CreatorSessionResult.Denied(CreatorSessionDenial.ACCOUNT_NOT_ACTIVE)
        }

        val deviceId = DeviceId(credential.deviceId)
        val device = try {
            accounts.verifyDevice(credential.accessToken, deviceId).getOrElse {
                return CreatorSessionResult.Denied(CreatorSessionDenial.DEVICE_SERVICE_UNAVAILABLE)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return CreatorSessionResult.Denied(CreatorSessionDenial.DEVICE_SERVICE_UNAVAILABLE)
        }
        if (device.deviceId != deviceId || device.state != RegisteredDeviceState.VERIFIED) {
            vault.clear()
            return CreatorSessionResult.Denied(CreatorSessionDenial.DEVICE_NOT_VERIFIED)
        }

        val maximumAge = Duration.between(credential.authenticatedAt, credential.expiresAt).plusSeconds(1)
        val decision = evaluateCredential(credential, identity, device, now, maximumAge)
        if (decision != IdentityDecision.Allowed) {
            vault.clear()
            return CreatorSessionResult.Denied(CreatorSessionDenial.IDENTITY_POLICY_DENIED)
        }
        return CreatorSessionResult.Authenticated(
            PrincipalId(credential.principalId),
            deviceId,
            credential.expiresAt
        )
    }

    /** Local credentials are cleared even if server revocation is temporarily unavailable. */
    suspend fun signOut(): CreatorSessionResult {
        val credential = when (val stored = vault.read()) {
            is CreatorSessionVaultResult.Success -> stored.value
            is CreatorSessionVaultResult.Failure ->
                return CreatorSessionResult.Denied(CreatorSessionDenial.SESSION_VAULT_UNAVAILABLE)
        }
        if (vault.clear() is CreatorSessionVaultResult.Failure) {
            return CreatorSessionResult.Denied(CreatorSessionDenial.SESSION_VAULT_CLEAR_FAILED)
        }
        if (credential == null) return CreatorSessionResult.NoSession
        val revoked = try {
            authentication.revoke(credential.accessToken, credential.refreshToken).isSuccess
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        return CreatorSessionResult.SignedOut(serverRevocationConfirmed = revoked)
    }


    private suspend fun revokeQuietly(accessToken: String, refreshToken: String?) {
        try {
            authentication.revoke(accessToken, refreshToken)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The short-lived provider token is not persisted when local checks fail.
        }
    }

    private fun evaluateCredential(
        credential: CreatorSessionCredential,
        identity: CreatorIdentitySnapshot,
        device: RegisteredDeviceSnapshot,
        now: Instant,
        maximumAuthenticationAge: Duration
    ): IdentityDecision {
        val session = IdentitySessionSnapshot(
            principalId = PrincipalId(credential.principalId),
            deviceId = DeviceId(credential.deviceId),
            issuedAt = credential.issuedAt,
            expiresAt = credential.expiresAt
        )
        val proof = AuthenticationProof(
            principalId = PrincipalId(credential.principalId),
            strength = credential.strength,
            verifiedFactorCount = credential.verifiedFactorCount,
            authenticatedAt = credential.authenticatedAt,
            localOnly = false
        )
        return CreatorIdentityGate.evaluate(
            identity = identity,
            device = device,
            session = session,
            proof = proof,
            request = IdentityAccessRequest(
                principalId = PrincipalId(credential.principalId),
                deviceId = DeviceId(credential.deviceId),
                maximumAuthenticationAge = maximumAuthenticationAge
            ),
            now = now
        )
    }
}
