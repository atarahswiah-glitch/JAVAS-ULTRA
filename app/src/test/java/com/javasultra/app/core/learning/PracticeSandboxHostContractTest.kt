package com.javasultra.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.Base64

class PracticeSandboxHostContractTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private val codeDigest = "a".repeat(64)
    private val runnerDigest = "b".repeat(64)
    private val suiteDigest = "c".repeat(64)
    private val request = PracticeSandboxRequest(
        "request-1", "task-1", "skill-1", codeDigest, runnerDigest,
        now.minusSeconds(30), now.plusSeconds(120), 1,
        PracticeSandboxLimits(60, 256, 64, 256, 100, 2)
    )
    private val submission = PracticeSandboxSubmission(
        "submission-1", request, "sha256:$codeDigest", "sha256:$suiteDigest",
        "idempotency-key-0001", now.minusSeconds(10)
    )

    private fun status(
        state: PracticeSandboxHostState = PracticeSandboxHostState.ACCEPTED,
        receipt: PracticeSandboxReceipt? = null,
        attestation: SignedPracticeSandboxAttestation? = null,
        rejectionCode: String? = null
    ) = PracticeSandboxHostStatus(
        submission.submissionId, request.id, state, now, receipt, rejectionCode, attestation
    )

    private fun receipt() = PracticeSandboxReceipt(
        request.id, codeDigest, runnerDigest, "runner-1", now.minusSeconds(1),
        "d".repeat(64), true, 0, false, false, false, false,
        0, true, suiteDigest, 4, 4
    )

    private fun attestation(
        signedSubmission: PracticeSandboxSubmission = submission,
        signedReceipt: PracticeSandboxReceipt = receipt()
    ) = SignedPracticeSandboxAttestation(
        "javas-practice-host", "key-2026-01", "Ed25519",
        PracticeSandboxAttestationDigest.requestDigest(signedSubmission),
        PracticeSandboxAttestationDigest.receiptDigest(signedReceipt),
        Base64.getEncoder().encodeToString(ByteArray(64) { 1 }), now
    )

    @Test fun acceptsContentAddressedSubmissionWithBoundCodeDigest() {
        assertNull(PracticeSandboxHostContract.validateSubmission(submission, now))
    }

    @Test fun rejectsCodeArtifactDigestMismatch() {
        assertEquals(
            PracticeSandboxHostContractDenial.CODE_ARTIFACT_MISMATCH,
            PracticeSandboxHostContract.validateSubmission(
                submission.copy(codeArtifactReference = "sha256:${"e".repeat(64)}"), now
            )
        )
    }

    @Test fun rejectsMutableOrMalformedArtifactReferences() {
        assertEquals(
            PracticeSandboxHostContractDenial.INVALID_ARTIFACT_REFERENCE,
            PracticeSandboxHostContract.validateSubmission(
                submission.copy(acceptanceSuiteArtifactReference = "https://example.org/suite.zip"), now
            )
        )
    }

    @Test fun rejectsWeakIdempotencyKeyAndFutureSubmission() {
        assertEquals(
            PracticeSandboxHostContractDenial.INVALID_IDEMPOTENCY_KEY,
            PracticeSandboxHostContract.validateSubmission(submission.copy(idempotencyKey = "short"), now)
        )
        assertEquals(
            PracticeSandboxHostContractDenial.SUBMISSION_TIME_INVALID,
            PracticeSandboxHostContract.validateSubmission(
                submission.copy(submittedAt = now.plusSeconds(1)), now
            )
        )
    }

    @Test fun acceptsPendingStatusWithoutReceipt() {
        assertNull(PracticeSandboxHostContract.validateStatus(submission, status(), now))
        assertNull(PracticeSandboxHostContract.validateStatus(
            submission, status(PracticeSandboxHostState.RUNNING), now
        ))
    }

    @Test fun rejectsCrossRequestStatusAndInvalidStateShape() {
        assertEquals(
            PracticeSandboxHostContractDenial.STATUS_CORRELATION_MISMATCH,
            PracticeSandboxHostContract.validateStatus(
                submission, status().copy(requestId = "other"), now
            )
        )
        assertEquals(
            PracticeSandboxHostContractDenial.INVALID_STATUS_SHAPE,
            PracticeSandboxHostContract.validateStatus(
                submission, status(receipt = receipt()), now
            )
        )
    }

    @Test fun completedStatusRequiresReceiptAndSignedAttestationMetadata() {
        assertEquals(
            PracticeSandboxHostContractDenial.INVALID_STATUS_SHAPE,
            PracticeSandboxHostContract.validateStatus(
                submission, status(PracticeSandboxHostState.COMPLETED), now
            )
        )
        assertEquals(
            PracticeSandboxHostContractDenial.INVALID_ATTESTATION_METADATA,
            PracticeSandboxHostContract.validateStatus(
                submission, status(PracticeSandboxHostState.COMPLETED, receipt()), now
            )
        )
        assertNull(PracticeSandboxHostContract.validateStatus(
            submission,
            status(PracticeSandboxHostState.COMPLETED, receipt(), attestation()),
            now
        ))
    }

    @Test fun completedStatusRejectsAttestationDigestMismatch() {
        val invalid = attestation().copy(receiptDigestSha256 = "f".repeat(64))
        assertEquals(
            PracticeSandboxHostContractDenial.INVALID_ATTESTATION_METADATA,
            PracticeSandboxHostContract.validateStatus(
                submission,
                status(PracticeSandboxHostState.COMPLETED, receipt(), invalid),
                now
            )
        )
    }

    @Test fun rejectedStatusRequiresReasonAndCannotCarryReceipt() {
        assertEquals(
            PracticeSandboxHostContractDenial.INVALID_STATUS_SHAPE,
            PracticeSandboxHostContract.validateStatus(
                submission, status(PracticeSandboxHostState.REJECTED), now
            )
        )
        assertEquals(
            PracticeSandboxHostContractDenial.INVALID_STATUS_SHAPE,
            PracticeSandboxHostContract.validateStatus(
                submission,
                status(PracticeSandboxHostState.REJECTED, receipt(), rejectionCode = "POLICY_DENIED"),
                now
            )
        )
    }

    @Test fun completedStatusMustBindReceiptToSubmittedCodeAndRunner() {
        assertEquals(
            PracticeSandboxHostContractDenial.INVALID_RECEIPT,
            PracticeSandboxHostContract.validateStatus(
                submission,
                status(
                    PracticeSandboxHostState.COMPLETED,
                    receipt().copy(codeSha256 = "e".repeat(64)),
                    attestation()
                ),
                now
            )
        )
    }
}
