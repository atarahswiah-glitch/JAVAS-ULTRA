package com.javasultra.app.core.learning

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

/**
 * Transport-neutral boundary between JAVAS and an independently operated practice host.
 *
 * This is a protocol contract only: no runner, network client, signature verifier, or
 * server is implemented here. The Android process must submit content-addressed,
 * non-secret artifacts and must never execute the submitted code itself.
 */
data class PracticeSandboxSubmission(
    val submissionId: String,
    val request: PracticeSandboxRequest,
    val codeArtifactReference: String,
    val acceptanceSuiteArtifactReference: String,
    val idempotencyKey: String,
    val submittedAt: Instant
)

enum class PracticeSandboxHostState { ACCEPTED, RUNNING, COMPLETED, REJECTED }

data class PracticeSandboxHostStatus(
    val submissionId: String,
    val requestId: String,
    val state: PracticeSandboxHostState,
    val updatedAt: Instant,
    val receipt: PracticeSandboxReceipt? = null,
    val rejectionCode: String? = null,
    /**
     * Opaque signature metadata returned by the trusted host API. It is not proof by
     * itself. A future authenticated gateway must verify the signature against a
     * pinned trust root before converting a receipt into skill evidence.
     */
    val signedAttestation: SignedPracticeSandboxAttestation? = null
)

data class SignedPracticeSandboxAttestation(
    val issuer: String,
    val keyId: String,
    val algorithm: String,
    val requestDigestSha256: String,
    val receiptDigestSha256: String,
    val signatureBase64: String,
    val issuedAt: Instant
)

/**
 * Implemented only by a future authenticated backend adapter. The adapter must enforce
 * Creator/task authorization, idempotency, server-side request validation, isolated
 * execution, signature verification, quotas, audit, and receipt persistence.
 */
interface PracticeSandboxGateway {
    suspend fun submit(submission: PracticeSandboxSubmission): PracticeSandboxHostStatus
    suspend fun getStatus(submissionId: String): PracticeSandboxHostStatus
}


/**
 * Versioned canonical digests used by the host signature protocol. The trusted host
 * signs these digests; clients still must verify the signature against a pinned key.
 */
object PracticeSandboxAttestationDigest {
    fun requestDigest(submission: PracticeSandboxSubmission): String {
        val request = submission.request
        val limits = request.limits
        return sha256(
            "javas-practice-submission-v1",
            submission.submissionId,
            submission.idempotencyKey,
            submission.submittedAt.toString(),
            request.id,
            request.taskId,
            request.skillId,
            request.codeSha256.lowercase(),
            request.runnerImageSha256.lowercase(),
            request.createdAt.toString(),
            request.deadline.toString(),
            request.attemptNumber.toString(),
            limits.maxRuntimeSeconds.toString(),
            limits.maxMemoryMiB.toString(),
            limits.maxWorkspaceMiB.toString(),
            limits.maxOutputKiB.toString(),
            limits.maxFiles.toString(),
            limits.maxAttempts.toString(),
            request.network.name,
            request.filesystem.name,
            request.secrets.name,
            request.privileges.name,
            submission.codeArtifactReference.lowercase(),
            submission.acceptanceSuiteArtifactReference.lowercase()
        )
    }

    fun receiptDigest(receipt: PracticeSandboxReceipt): String = sha256(
        "javas-practice-receipt-v1",
        receipt.requestId,
        receipt.codeSha256.lowercase(),
        receipt.runnerImageSha256.lowercase(),
        receipt.runnerId,
        receipt.completedAt.toString(),
        receipt.attestationSha256.lowercase(),
        receipt.exitCode.toString(),
        receipt.timedOut.toString(),
        receipt.memoryLimitExceeded.toString(),
        receipt.workspaceLimitExceeded.toString(),
        receipt.outputLimitExceeded.toString(),
        receipt.establishedNetworkConnections.toString(),
        receipt.workspaceCleaned.toString(),
        receipt.acceptanceSuiteSha256.lowercase(),
        receipt.acceptanceTestsRun.toString(),
        receipt.acceptanceTestsPassed.toString()
    )

    private fun sha256(vararg fields: String): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(fields.size)
            fields.forEach { field ->
                val encoded = field.toByteArray(StandardCharsets.UTF_8)
                output.writeInt(encoded.size)
                output.write(encoded)
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

enum class PracticeSandboxHostContractDenial {
    INVALID_SUBMISSION_METADATA,
    INVALID_ARTIFACT_REFERENCE,
    CODE_ARTIFACT_MISMATCH,
    INVALID_IDEMPOTENCY_KEY,
    SUBMISSION_TIME_INVALID,
    STATUS_CORRELATION_MISMATCH,
    INVALID_STATUS_SHAPE,
    INVALID_ATTESTATION_METADATA,
    INVALID_RECEIPT
}

/**
 * Local consistency checks only. Passing this policy does not authenticate the host,
 * verify a signature, authorize a task, or make a runner trustworthy.
 */
object PracticeSandboxHostContract {
    private val digestReference = Regex("^sha256:([a-fA-F0-9]{64})$")
    private val digest = Regex("^[a-fA-F0-9]{64}$")

    fun validateSubmission(
        submission: PracticeSandboxSubmission,
        now: Instant
    ): PracticeSandboxHostContractDenial? {
        if (submission.submissionId.isBlank() || submission.request.id.isBlank()) {
            return PracticeSandboxHostContractDenial.INVALID_SUBMISSION_METADATA
        }
        val codeDigest = digestReference.matchEntire(submission.codeArtifactReference)
            ?.groupValues?.get(1)
            ?: return PracticeSandboxHostContractDenial.INVALID_ARTIFACT_REFERENCE
        if (!codeDigest.equals(submission.request.codeSha256, ignoreCase = true)) {
            return PracticeSandboxHostContractDenial.CODE_ARTIFACT_MISMATCH
        }
        val suiteDigest = digestReference.matchEntire(submission.acceptanceSuiteArtifactReference)
            ?.groupValues?.get(1)
            ?: return PracticeSandboxHostContractDenial.INVALID_ARTIFACT_REFERENCE
        if (!digest.matches(suiteDigest)) {
            return PracticeSandboxHostContractDenial.INVALID_ARTIFACT_REFERENCE
        }
        if (submission.idempotencyKey.length !in 16..128 ||
            submission.idempotencyKey.any { it.isWhitespace() }
        ) return PracticeSandboxHostContractDenial.INVALID_IDEMPOTENCY_KEY
        if (submission.submittedAt.isAfter(now) ||
            submission.submittedAt.isBefore(submission.request.createdAt) ||
            !submission.submittedAt.isBefore(submission.request.deadline)
        ) return PracticeSandboxHostContractDenial.SUBMISSION_TIME_INVALID
        if (PracticeSandboxPolicy.validateRequest(submission.request, now) != null) {
            return PracticeSandboxHostContractDenial.INVALID_SUBMISSION_METADATA
        }
        return null
    }

    fun validateStatus(
        submission: PracticeSandboxSubmission,
        status: PracticeSandboxHostStatus,
        now: Instant
    ): PracticeSandboxHostContractDenial? {
        if (status.submissionId != submission.submissionId ||
            status.requestId != submission.request.id
        ) return PracticeSandboxHostContractDenial.STATUS_CORRELATION_MISMATCH
        if (status.updatedAt.isAfter(now) || status.updatedAt.isBefore(submission.submittedAt)) {
            return PracticeSandboxHostContractDenial.INVALID_STATUS_SHAPE
        }

        return when (status.state) {
            PracticeSandboxHostState.ACCEPTED,
            PracticeSandboxHostState.RUNNING -> {
                if (status.receipt != null || status.signedAttestation != null ||
                    status.rejectionCode != null
                ) PracticeSandboxHostContractDenial.INVALID_STATUS_SHAPE else null
            }
            PracticeSandboxHostState.REJECTED -> {
                if (status.rejectionCode.isNullOrBlank() ||
                    status.receipt != null || status.signedAttestation != null
                ) PracticeSandboxHostContractDenial.INVALID_STATUS_SHAPE else null
            }
            PracticeSandboxHostState.COMPLETED -> {
                val receipt = status.receipt
                    ?: return PracticeSandboxHostContractDenial.INVALID_STATUS_SHAPE
                val attestation = status.signedAttestation
                    ?: return PracticeSandboxHostContractDenial.INVALID_ATTESTATION_METADATA
                val suiteDigest = digestReference.matchEntire(submission.acceptanceSuiteArtifactReference)
                    ?.groupValues?.get(1)
                if (status.rejectionCode != null ||
                    attestation.issuer.isBlank() || attestation.keyId.isBlank() ||
                    attestation.algorithm.isBlank() || attestation.signatureBase64.isBlank() ||
                    !digest.matches(attestation.requestDigestSha256) ||
                    !digest.matches(attestation.receiptDigestSha256) ||
                    !attestation.requestDigestSha256.equals(PracticeSandboxAttestationDigest.requestDigest(submission), true) ||
                    !attestation.receiptDigestSha256.equals(PracticeSandboxAttestationDigest.receiptDigest(receipt), true) ||
                    attestation.issuedAt.isAfter(now) ||
                    attestation.issuedAt.isBefore(submission.submittedAt)
                ) return PracticeSandboxHostContractDenial.INVALID_ATTESTATION_METADATA
                if (receipt.requestId != submission.request.id ||
                    !receipt.codeSha256.equals(submission.request.codeSha256, true) ||
                    !receipt.runnerImageSha256.equals(submission.request.runnerImageSha256, true) ||
                    !receipt.acceptanceSuiteSha256.equals(suiteDigest, true) ||
                    receipt.completedAt.isBefore(submission.submittedAt) ||
                    receipt.completedAt.isAfter(status.updatedAt) ||
                    receipt.completedAt.isAfter(now)
                ) return PracticeSandboxHostContractDenial.INVALID_RECEIPT
                null
            }
        }
    }
}
