package com.javasultra.app.core.security

import java.nio.charset.StandardCharsets

data class EnrollmentChallengePayload(
    val version: Int,
    val challengeId: String,
    val creatorId: String,
    val deviceId: String,
    val purpose: String,
    val expiresAt: String,
    val nonceBase64: String
)

data class DeviceApprovalChallengePayload(
    val version: Int,
    val challengeId: String,
    val creatorId: String,
    val approverDeviceId: String,
    val targetDeviceId: String,
    val purpose: String,
    val expiresAt: String,
    val nonceBase64: String
)

/**
 * Must remain byte-for-byte aligned with the server's canonicalChallengeBytes
 * and canonicalApprovalChallengeBytes implementations.
 */
object DeviceChallengeCanonicalizer {
    fun enrollment(challenge: EnrollmentChallengePayload): ByteArray = encode(
        "JAVAS-DEVICE-ENROLLMENT",
        challenge.version.toString(),
        challenge.challengeId,
        challenge.creatorId,
        challenge.deviceId,
        challenge.purpose,
        challenge.expiresAt,
        challenge.nonceBase64
    )

    fun approval(challenge: DeviceApprovalChallengePayload): ByteArray = encode(
        "JAVAS-DEVICE-APPROVAL",
        challenge.version.toString(),
        challenge.challengeId,
        challenge.creatorId,
        challenge.approverDeviceId,
        challenge.targetDeviceId,
        challenge.purpose,
        challenge.expiresAt,
        challenge.nonceBase64
    )

    private fun encode(vararg fields: String): ByteArray {
        require(fields.isNotEmpty() && fields.all { it.isNotEmpty() && it.length <= 512 }) {
            "Challenge contains an empty or oversized field"
        }
        require(fields.none { it.contains('\n') || it.contains('\r') }) {
            "Challenge fields cannot contain line delimiters"
        }
        return fields.joinToString("\n").toByteArray(StandardCharsets.UTF_8)
    }
}
