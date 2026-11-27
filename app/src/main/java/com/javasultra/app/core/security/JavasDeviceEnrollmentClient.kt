package com.javasultra.app.core.security

import com.javasultra.app.core.ai.JavasAccessTokenProvider
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import org.json.JSONObject

/**
 * Client for the javas-device-enrollment Edge Function.
 *
 * Calls are synchronous and must run off the main/UI thread. The Creator access
 * token is supplied at call time and is never stored by this class.
 */
class JavasDeviceEnrollmentClient(
    private val functionUrl: String,
    private val publishableKey: String,
    private val accessTokenProvider: JavasAccessTokenProvider,
    private val deviceKeyManager: DeviceKeyManager,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000
) {
    fun enrollThisDevice(): EnrollmentResult {
        val material = deviceKeyManager.getOrCreateKeyMaterial()
        val challengeJson = post(
            JSONObject()
                .put("action", "challenge")
                .put("deviceId", material.deviceId)
        ).getJSONObject("challenge")
        val challenge = EnrollmentChallengePayload(
            version = challengeJson.getInt("version"),
            challengeId = challengeJson.getString("challengeId"),
            creatorId = challengeJson.getString("creatorId"),
            deviceId = challengeJson.getString("deviceId"),
            purpose = challengeJson.getString("purpose"),
            expiresAt = challengeJson.getString("expiresAt"),
            nonceBase64 = challengeJson.getString("nonceBase64")
        )
        require(challenge.deviceId == material.deviceId) { "Server challenge device binding mismatch" }
        val signature = deviceKeyManager.signServerChallenge(
            DeviceChallengeCanonicalizer.enrollment(challenge)
        )
        val proof = JSONObject()
            .put("challenge", challengeJson)
            .put("publicKeySpkiBase64", material.publicKeyBase64)
            .put("signatureDerBase64", Base64.getEncoder().encodeToString(signature))
        val response = post(JSONObject().put("action", "prove").put("proof", proof))
        return EnrollmentResult(
            deviceId = response.getString("deviceId"),
            state = response.getString("state")
        )
    }

    fun approvePendingDevice(targetDeviceId: String): ApprovalResult {
        require(targetDeviceId.isNotBlank()) { "Target device ID is required" }
        val material = deviceKeyManager.getOrCreateKeyMaterial()
        val challengeJson = post(
            JSONObject()
                .put("action", "approval-challenge")
                .put("approverDeviceId", material.deviceId)
                .put("targetDeviceId", targetDeviceId)
        ).getJSONObject("challenge")
        val challenge = DeviceApprovalChallengePayload(
            version = challengeJson.getInt("version"),
            challengeId = challengeJson.getString("challengeId"),
            creatorId = challengeJson.getString("creatorId"),
            approverDeviceId = challengeJson.getString("approverDeviceId"),
            targetDeviceId = challengeJson.getString("targetDeviceId"),
            purpose = challengeJson.getString("purpose"),
            expiresAt = challengeJson.getString("expiresAt"),
            nonceBase64 = challengeJson.getString("nonceBase64")
        )
        require(challenge.approverDeviceId == material.deviceId) {
            "Server challenge signer binding mismatch"
        }
        require(challenge.targetDeviceId == targetDeviceId) {
            "Server challenge target binding mismatch"
        }
        val signature = deviceKeyManager.signServerChallenge(
            DeviceChallengeCanonicalizer.approval(challenge)
        )
        val proof = JSONObject()
            .put("challenge", challengeJson)
            .put("signatureDerBase64", Base64.getEncoder().encodeToString(signature))
        val response = post(JSONObject().put("action", "approve").put("proof", proof))
        return ApprovalResult(
            deviceId = response.getString("deviceId"),
            state = response.getString("state")
        )
    }

    private fun post(body: JSONObject): JSONObject {
        require(functionUrl.startsWith("https://")) { "Enrollment endpoint must use HTTPS" }
        require(publishableKey.isNotBlank()) { "Supabase publishable key is not configured" }
        val token = accessTokenProvider.currentAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Sign in to JAVAS before managing trusted devices")
        val connection = (URL(functionUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Cache-Control", "no-store")
        }
        try {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).optString("error") }.getOrNull()
                throw IllegalStateException(message?.takeIf { it.isNotBlank() }
                    ?: "Device security request failed (HTTP $status)")
            }
            return JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    data class EnrollmentResult(val deviceId: String, val state: String)
    data class ApprovalResult(val deviceId: String, val state: String)
}
