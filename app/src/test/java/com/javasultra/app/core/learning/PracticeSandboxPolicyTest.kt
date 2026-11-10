package com.javasultra.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PracticeSandboxPolicyTest {
    private val now = Instant.parse("2026-11-10T10:00:00Z")
    private val digest = "a".repeat(64)
    private val limits = PracticeSandboxLimits(60, 256, 64, 256, 100, 2)

    private fun request() = PracticeSandboxRequest(
        "run-1", "task-1", "skill-1", digest, "b".repeat(64),
        now.minusSeconds(5), now.plusSeconds(90), 1, limits
    )

    private fun receipt(request: PracticeSandboxRequest = request()) = PracticeSandboxReceipt(
        request.id, request.codeSha256, request.runnerImageSha256, "isolated-runner-1",
        now.plusSeconds(10), "c".repeat(64), true, 0, false, false, false, false,
        0, true, "d".repeat(64), 5, 5
    )

    @Test fun acceptsOnlyBoundedOfflineEphemeralRequest() {
        assertNull(PracticeSandboxPolicy.validateRequest(request(), now))
    }

    @Test fun rejectsNetworkFilesystemSecretsAndPrivilegeExpansion() {
        val r = request()
        assertEquals(PracticeSandboxDenial.NETWORK_NOT_DISABLED,
            PracticeSandboxPolicy.validateRequest(r.copy(network = PracticeNetworkPolicy.ALLOWLISTED_READ_ONLY), now))
        assertEquals(PracticeSandboxDenial.FILESYSTEM_NOT_EPHEMERAL,
            PracticeSandboxPolicy.validateRequest(r.copy(filesystem = PracticeFilesystemPolicy.HOST_FILESYSTEM), now))
        assertEquals(PracticeSandboxDenial.SECRETS_NOT_ALLOWED,
            PracticeSandboxPolicy.validateRequest(r.copy(secrets = PracticeSecretPolicy.SCOPED_TEST_FIXTURES), now))
        assertEquals(PracticeSandboxDenial.ELEVATED_PRIVILEGES_NOT_ALLOWED,
            PracticeSandboxPolicy.validateRequest(r.copy(privileges = PracticePrivilegePolicy.ELEVATED), now))
    }

    @Test fun enforcesStrictResourceAndAttemptBudgets() {
        val r = request()
        assertEquals(PracticeSandboxDenial.INVALID_LIMITS,
            PracticeSandboxPolicy.validateRequest(r.copy(limits = limits.copy(maxRuntimeSeconds = 121)), now))
        assertEquals(PracticeSandboxDenial.ATTEMPT_BUDGET_EXCEEDED,
            PracticeSandboxPolicy.validateRequest(r.copy(attemptNumber = 3), now))
    }

    @Test fun rejectsMalformedDigestsAndExpiredOrOverlongWindows() {
        val r = request()
        assertEquals(PracticeSandboxDenial.INVALID_DIGEST,
            PracticeSandboxPolicy.validateRequest(r.copy(codeSha256 = "not-a-digest"), now))
        assertEquals(PracticeSandboxDenial.INVALID_TIME_WINDOW,
            PracticeSandboxPolicy.validateRequest(r.copy(deadline = now.plusSeconds(301)), now))
    }

    @Test fun successfulVerifiedReceiptProducesEvidenceReference() {
        val decision = PracticeSandboxPolicy.evaluateReceipt(request(), receipt(), now.plusSeconds(20))
        assertTrue(decision.accepted)
        assertNotNull(decision.evidenceReference)
        assertNull(decision.denial)
    }

    @Test fun unverifiedAttestationCannotBecomeSkillEvidence() {
        val decision = PracticeSandboxPolicy.evaluateReceipt(
            request(), receipt().copy(attestationVerifiedByTrustedHost = false), now.plusSeconds(20)
        )
        assertFalse(decision.accepted)
        assertEquals(PracticeSandboxDenial.UNVERIFIED_RUNNER_ATTESTATION, decision.denial)
    }

    @Test fun rejectsReceiptForDifferentCodeOrRunnerImage() {
        val r = request()
        assertEquals(PracticeSandboxDenial.RECEIPT_MISSING_OR_MISMATCHED,
            PracticeSandboxPolicy.evaluateReceipt(r, receipt().copy(codeSha256 = "e".repeat(64)), now.plusSeconds(20)).denial)
        assertEquals(PracticeSandboxDenial.RECEIPT_MISSING_OR_MISMATCHED,
            PracticeSandboxPolicy.evaluateReceipt(r, receipt().copy(runnerImageSha256 = "e".repeat(64)), now.plusSeconds(20)).denial)
    }

    @Test fun rejectsTimeoutResourceOverrunNetworkEscapeAndUncleanWorkspace() {
        val r = request()
        assertEquals(PracticeSandboxDenial.RUNNER_FAILURE,
            PracticeSandboxPolicy.evaluateReceipt(r, receipt().copy(timedOut = true), now.plusSeconds(20)).denial)
        assertEquals(PracticeSandboxDenial.RESOURCE_LIMIT_EXCEEDED,
            PracticeSandboxPolicy.evaluateReceipt(r, receipt().copy(memoryLimitExceeded = true), now.plusSeconds(20)).denial)
        assertEquals(PracticeSandboxDenial.NETWORK_ESCAPE_DETECTED,
            PracticeSandboxPolicy.evaluateReceipt(r, receipt().copy(establishedNetworkConnections = 1), now.plusSeconds(20)).denial)
        assertEquals(PracticeSandboxDenial.WORKSPACE_NOT_CLEANED,
            PracticeSandboxPolicy.evaluateReceipt(r, receipt().copy(workspaceCleaned = false), now.plusSeconds(20)).denial)
    }

    @Test fun recordsPartialAcceptanceAsFailedPracticeEvidence() {
        val r = request()
        val failed = PracticeSandboxPolicy.evaluateReceipt(
            r, receipt().copy(acceptanceTestsPassed = 4), now.plusSeconds(20)
        )
        assertTrue(failed.accepted)
        assertFalse(failed.passedAcceptanceCriteria)
        assertNotNull(failed.evidenceReference)
        assertEquals(PracticeSandboxDenial.ACCEPTANCE_TESTS_FAILED,
            PracticeSandboxPolicy.evaluateReceipt(r, receipt().copy(acceptanceTestsRun = 0, acceptanceTestsPassed = 0), now.plusSeconds(20)).denial)
    }

    @Test fun missingOrMismatchedReceiptCannotBeAccepted() {
        val r = request()
        assertEquals(PracticeSandboxDenial.RECEIPT_MISSING_OR_MISMATCHED,
            PracticeSandboxPolicy.evaluateReceipt(r, null, now.plusSeconds(20)).denial)
        assertEquals(PracticeSandboxDenial.RECEIPT_MISSING_OR_MISMATCHED,
            PracticeSandboxPolicy.evaluateReceipt(r, receipt().copy(requestId = "other-run"), now.plusSeconds(20)).denial)
    }
}
