package com.javasultra.app.core.security

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DeviceKeyManagerInstrumentationTest {
    @Test fun keystoreKeySignsFreshChallengeAndPublicKeyVerifiesIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val creatorId = "device-test-${UUID.randomUUID()}"
        val manager = DeviceKeyManager(context, creatorId)
        try {
            val material = manager.getOrCreateKeyMaterial()
            val challenge = ByteArray(32) { (it * 7).toByte() }
            val signature = manager.signServerChallenge(challenge)
            val publicKeyBytes = Base64.decode(material.publicKeyBase64, Base64.NO_WRAP)
            val publicKey = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val verified = Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(challenge)
                verify(signature)
            }

            assertTrue(material.deviceId.isNotBlank())
            assertEquals("EC_P256_SHA256", material.algorithm)
            assertTrue(verified)
            assertEquals(material.deviceId, manager.getOrCreateKeyMaterial().deviceId)
            assertEquals(material.publicKeyBase64, manager.getOrCreateKeyMaterial().publicKeyBase64)
        } finally {
            manager.deleteRegistrationKey()
        }
    }

    @Test fun rejectsShortAndOversizedServerChallenges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val creatorId = "device-test-${UUID.randomUUID()}"
        val manager = DeviceKeyManager(context, creatorId)
        try {
            try {
                manager.signServerChallenge(ByteArray(31))
                throw AssertionError("Short challenge must be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
            try {
                manager.signServerChallenge(ByteArray(513))
                throw AssertionError("Oversized challenge must be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        } finally {
            manager.deleteRegistrationKey()
        }
    }

    @Test fun deletingLocalRegistrationKeyCreatesANewInstallationIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val creatorId = "device-test-${UUID.randomUUID()}"
        val manager = DeviceKeyManager(context, creatorId)
        try {
            val old = manager.getOrCreateKeyMaterial()
            manager.deleteRegistrationKey()
            val fresh = manager.getOrCreateKeyMaterial()
            assertNotEquals(old.deviceId, fresh.deviceId)
            assertNotEquals(old.publicKeyBase64, fresh.publicKeyBase64)
        } finally {
            manager.deleteRegistrationKey()
        }
    }
}
