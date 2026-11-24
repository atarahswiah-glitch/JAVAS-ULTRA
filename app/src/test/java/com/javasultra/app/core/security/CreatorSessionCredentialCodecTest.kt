package com.javasultra.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CreatorSessionCredentialCodecTest {
    private val now = Instant.parse("2026-11-24T12:00:00Z")

    private fun credential(
        strength: AuthenticationStrength = AuthenticationStrength.MULTI_FACTOR,
        factorCount: Int = 2
    ) = CreatorSessionCredential(
        principalId = "creator-1",
        deviceId = "device-1",
        accessToken = "access-token-opaque",
        refreshToken = "refresh-token-opaque",
        issuedAt = now,
        authenticatedAt = now,
        expiresAt = now.plusSeconds(3_600),
        strength = strength,
        verifiedFactorCount = factorCount
    )

    @Test fun roundTripsCredentialWithoutChangingClaims() {
        val expected = credential()
        val decoded = CreatorSessionCredentialCodec.decode(CreatorSessionCredentialCodec.encode(expected))
        assertEquals(expected, decoded)
    }

    @Test fun rejectsLocalBiometricAsRemoteSessionAuthentication() {
        val value = credential(AuthenticationStrength.LOCAL_BIOMETRIC_UNLOCK, 1)
        assertFalse(value.validate())
        assertThrows(IllegalArgumentException::class.java) {
            CreatorSessionCredentialCodec.encode(value)
        }
    }

    @Test fun rejectsSingleFactorClaimWithMultipleVerifiedFactors() {
        assertFalse(credential(AuthenticationStrength.PASSWORD_OR_PIN, 2).validate())
    }

    @Test fun rejectsExpiredOrMalformedCredential() {
        val expired = credential().copy(expiresAt = now)
        assertFalse(expired.validate())
    }

    @Test fun rejectsTrailingBytesInCredentialPayload() {
        val encoded = CreatorSessionCredentialCodec.encode(credential()) + byteArrayOf(1)
        assertThrows(IllegalArgumentException::class.java) {
            CreatorSessionCredentialCodec.decode(encoded)
        }
    }

    @Test fun rejectsUnknownCredentialFormatVersion() {
        val encoded = CreatorSessionCredentialCodec.encode(credential())
        encoded[3] = 2
        assertThrows(IllegalArgumentException::class.java) {
            CreatorSessionCredentialCodec.decode(encoded)
        }
    }
}
