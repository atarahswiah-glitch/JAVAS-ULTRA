package com.javasultra.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceChallengeCanonicalizerTest {
    @Test
    fun enrollmentPayloadMatchesServerFieldOrder() {
        val payload = EnrollmentChallengePayload(
            version = 1,
            challengeId = "challenge-id",
            creatorId = "creator-id",
            deviceId = "device-id",
            purpose = "ENROLL_DEVICE",
            expiresAt = "2026-11-27T08:00:00.000Z",
            nonceBase64 = "A".repeat(44)
        )
        val actual = String(DeviceChallengeCanonicalizer.enrollment(payload), Charsets.UTF_8)
        assertEquals(
            listOf(
                "JAVAS-DEVICE-ENROLLMENT",
                "1",
                "challenge-id",
                "creator-id",
                "device-id",
                "ENROLL_DEVICE",
                "2026-11-27T08:00:00.000Z",
                "A".repeat(44)
            ).joinToString("\n"),
            actual
        )
    }

    @Test
    fun approvalPayloadBindsApproverAndTargetInServerFieldOrder() {
        val payload = DeviceApprovalChallengePayload(
            version = 1,
            challengeId = "challenge-id",
            creatorId = "creator-id",
            approverDeviceId = "trusted-device",
            targetDeviceId = "pending-device",
            purpose = "APPROVE_DEVICE",
            expiresAt = "2026-11-27T08:00:00.000Z",
            nonceBase64 = "B".repeat(44)
        )
        val actual = String(DeviceChallengeCanonicalizer.approval(payload), Charsets.UTF_8)
        assertEquals(
            listOf(
                "JAVAS-DEVICE-APPROVAL",
                "1",
                "challenge-id",
                "creator-id",
                "trusted-device",
                "pending-device",
                "APPROVE_DEVICE",
                "2026-11-27T08:00:00.000Z",
                "B".repeat(44)
            ).joinToString("\n"),
            actual
        )
    }

    @Test
    fun rejectsDelimiterInjection() {
        val payload = EnrollmentChallengePayload(
            version = 1,
            challengeId = "challenge-id",
            creatorId = "creator\nattacker",
            deviceId = "device-id",
            purpose = "ENROLL_DEVICE",
            expiresAt = "2026-11-27T08:00:00.000Z",
            nonceBase64 = "A".repeat(44)
        )
        assertThrows(IllegalArgumentException::class.java) {
            DeviceChallengeCanonicalizer.enrollment(payload)
        }
    }
}
