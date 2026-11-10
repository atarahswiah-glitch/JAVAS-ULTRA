package com.javasultra.app.core.learning

import java.time.Instant

enum class PracticeNetworkPolicy { DISABLED, ALLOWLISTED_READ_ONLY, UNRESTRICTED }
enum class PracticeFilesystemPolicy { EPHEMERAL_WORKSPACE_ONLY, APPROVED_FILES, HOST_FILESYSTEM }
enum class PracticeSecretPolicy { NONE, SCOPED_TEST_FIXTURES, CREATOR_SECRETS }
enum class PracticePrivilegePolicy { UNPRIVILEGED_ONLY, ELEVATED }

data class PracticeSandboxLimits(
    val maxRuntimeSeconds: Long,
    val maxMemoryMiB: Int,
    val maxWorkspaceMiB: Int,
    val maxOutputKiB: Int,
    val maxFiles: Int,
    val maxAttempts: Int
)

data class PracticeSandboxRequest(
    val id: String,
    val taskId: String,
    val skillId: String,
    val codeSha256: String,
    val runnerImageSha256: String,
    val createdAt: Instant,
    val deadline: Instant,
    val attemptNumber: Int,
    val limits: PracticeSandboxLimits,
    val network: PracticeNetworkPolicy = PracticeNetworkPolicy.DISABLED,
    val filesystem: PracticeFilesystemPolicy = PracticeFilesystemPolicy.EPHEMERAL_WORKSPACE_ONLY,
    val secrets: PracticeSecretPolicy = PracticeSecretPolicy.NONE,
    val privileges: PracticePrivilegePolicy = PracticePrivilegePolicy.UNPRIVILEGED_ONLY
)

enum class PracticeSandboxDenial {
    INVALID_METADATA,
    INVALID_DIGEST,
    INVALID_TIME_WINDOW,
    INVALID_LIMITS,
    ATTEMPT_BUDGET_EXCEEDED,
    NETWORK_NOT_DISABLED,
    FILESYSTEM_NOT_EPHEMERAL,
    SECRETS_NOT_ALLOWED,
    ELEVATED_PRIVILEGES_NOT_ALLOWED,
    RECEIPT_MISSING_OR_MISMATCHED,
    UNVERIFIED_RUNNER_ATTESTATION,
    RUNNER_FAILURE,
    ACCEPTANCE_TESTS_FAILED,
    RESOURCE_LIMIT_EXCEEDED,
    NETWORK_ESCAPE_DETECTED,
    WORKSPACE_NOT_CLEANED
}

data class PracticeSandboxReceipt(
    val requestId: String,
    val codeSha256: String,
    val runnerImageSha256: String,
    val runnerId: String,
    val completedAt: Instant,
    val attestationSha256: String,
    val attestationVerifiedByTrustedHost: Boolean,
    val exitCode: Int,
    val timedOut: Boolean,
    val memoryLimitExceeded: Boolean,
    val workspaceLimitExceeded: Boolean,
    val outputLimitExceeded: Boolean,
    val establishedNetworkConnections: Int,
    val workspaceCleaned: Boolean,
    val acceptanceSuiteSha256: String,
    val acceptanceTestsRun: Int,
    val acceptanceTestsPassed: Int
)

data class PracticeSandboxDecision(
    val accepted: Boolean,
    val denial: PracticeSandboxDenial? = null,
    val evidenceReference: String? = null
)

/**
 * Conservative preflight and receipt-validation policy for a future isolated runner.
 *
 * This class does NOT execute code or create a sandbox. The runner must enforce the
 * requested limits outside the app process, isolate networking/filesystem/credentials,
 * and produce a signed attestation. A trusted verifier must validate that signature
 * before setting attestationVerifiedByTrustedHost. A remote trusted service must repeat
 * these checks before treating the receipt as skill evidence.
 */
object PracticeSandboxPolicy {
    private val sha256 = Regex("^[a-fA-F0-9]{64}$")

    fun validateRequest(request: PracticeSandboxRequest, now: Instant): PracticeSandboxDenial? {
        if (request.id.isBlank() || request.taskId.isBlank() || request.skillId.isBlank()) {
            return PracticeSandboxDenial.INVALID_METADATA
        }
        if (!sha256.matches(request.codeSha256) || !sha256.matches(request.runnerImageSha256)) {
            return PracticeSandboxDenial.INVALID_DIGEST
        }
        if (!request.deadline.isAfter(request.createdAt) ||
            request.createdAt.isAfter(now) || !request.deadline.isAfter(now) ||
            request.deadline.isAfter(request.createdAt.plusSeconds(300))
        ) return PracticeSandboxDenial.INVALID_TIME_WINDOW

        val limits = request.limits
        if (limits.maxRuntimeSeconds !in 1..120 ||
            limits.maxMemoryMiB !in 32..512 ||
            limits.maxWorkspaceMiB !in 1..256 ||
            limits.maxOutputKiB !in 1..1024 ||
            limits.maxFiles !in 1..500 ||
            limits.maxAttempts !in 1..3
        ) return PracticeSandboxDenial.INVALID_LIMITS
        if (request.attemptNumber !in 1..limits.maxAttempts) {
            return PracticeSandboxDenial.ATTEMPT_BUDGET_EXCEEDED
        }
        if (request.network != PracticeNetworkPolicy.DISABLED) {
            return PracticeSandboxDenial.NETWORK_NOT_DISABLED
        }
        if (request.filesystem != PracticeFilesystemPolicy.EPHEMERAL_WORKSPACE_ONLY) {
            return PracticeSandboxDenial.FILESYSTEM_NOT_EPHEMERAL
        }
        if (request.secrets != PracticeSecretPolicy.NONE) {
            return PracticeSandboxDenial.SECRETS_NOT_ALLOWED
        }
        if (request.privileges != PracticePrivilegePolicy.UNPRIVILEGED_ONLY) {
            return PracticeSandboxDenial.ELEVATED_PRIVILEGES_NOT_ALLOWED
        }
        return null
    }

    fun evaluateReceipt(
        request: PracticeSandboxRequest,
        receipt: PracticeSandboxReceipt?,
        now: Instant
    ): PracticeSandboxDecision {
        validateRequest(request, now)?.let { return denied(it) }
        if (receipt == null || receipt.requestId != request.id ||
            !receipt.codeSha256.equals(request.codeSha256, true) ||
            !receipt.runnerImageSha256.equals(request.runnerImageSha256, true) ||
            receipt.runnerId.isBlank() || !sha256.matches(receipt.attestationSha256) ||
            !sha256.matches(receipt.acceptanceSuiteSha256)
        ) return denied(PracticeSandboxDenial.RECEIPT_MISSING_OR_MISMATCHED)
        if (!receipt.attestationVerifiedByTrustedHost) {
            return denied(PracticeSandboxDenial.UNVERIFIED_RUNNER_ATTESTATION)
        }
        if (receipt.completedAt.isBefore(request.createdAt) ||
            receipt.completedAt.isAfter(now) ||
            !receipt.completedAt.isBefore(request.deadline)
        ) return denied(PracticeSandboxDenial.RECEIPT_MISSING_OR_MISMATCHED)
        if (receipt.exitCode != 0 || receipt.timedOut) {
            return denied(PracticeSandboxDenial.RUNNER_FAILURE)
        }
        if (receipt.memoryLimitExceeded || receipt.workspaceLimitExceeded || receipt.outputLimitExceeded) {
            return denied(PracticeSandboxDenial.RESOURCE_LIMIT_EXCEEDED)
        }
        if (receipt.establishedNetworkConnections != 0) {
            return denied(PracticeSandboxDenial.NETWORK_ESCAPE_DETECTED)
        }
        if (!receipt.workspaceCleaned) return denied(PracticeSandboxDenial.WORKSPACE_NOT_CLEANED)
        if (receipt.acceptanceTestsRun <= 0 ||
            receipt.acceptanceTestsPassed != receipt.acceptanceTestsRun
        ) return denied(PracticeSandboxDenial.ACCEPTANCE_TESTS_FAILED)

        return PracticeSandboxDecision(
            accepted = true,
            evidenceReference = "sandbox:${receipt.runnerId}:${receipt.attestationSha256.lowercase()}"
        )
    }

    private fun denied(reason: PracticeSandboxDenial) =
        PracticeSandboxDecision(accepted = false, denial = reason)
}
