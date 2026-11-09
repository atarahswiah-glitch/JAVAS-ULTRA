package com.javasultra.app.core.development

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class UpgradeWorkspacePolicyTest {
    private fun proposal(
        target: ChangeTarget = ChangeTarget.FEATURE_MODULE,
        approval: Boolean = true,
        backup: GateResult = GateResult.PASS
    ) = UpgradeProposal(
        "change-1", "Improve retrieval", target, Instant.parse("2026-11-09T12:00:00Z"),
        "sandbox-1", GateResult.PASS, GateResult.PASS, GateResult.PASS,
        GateResult.PASS, backup, approval, true
    )

    @Test fun permitsFullyCheckedApprovedIsolatedFeatureChange() {
        assertNull(UpgradeWorkspacePolicy.evaluate(proposal(), WorkspaceState.REVIEW_REQUIRED))
    }

    @Test fun blocksChangesToTrustRootAndIdentity() {
        assertEquals(UpgradeDenial.TRUST_BOUNDARY_PROTECTED,
            UpgradeWorkspacePolicy.evaluate(proposal(target = ChangeTarget.TRUST_ROOT), WorkspaceState.REVIEW_REQUIRED))
    }

    @Test fun requiresCreatorApproval() {
        assertEquals(UpgradeDenial.CREATOR_APPROVAL_REQUIRED,
            UpgradeWorkspacePolicy.evaluate(proposal(approval = false), WorkspaceState.TESTING))
    }

    @Test fun requiresVerifiedBackup() {
        assertEquals(UpgradeDenial.BACKUP_NOT_VERIFIED,
            UpgradeWorkspacePolicy.evaluate(proposal(backup = GateResult.NOT_RUN), WorkspaceState.TESTING))
    }

    @Test fun rejectsExecutionOutsideIsolatedTestingStates() {
        assertEquals(UpgradeDenial.NOT_ISOLATED,
            UpgradeWorkspacePolicy.evaluate(proposal(), WorkspaceState.APPROVED))
    }

    @Test fun rejectsMissingTests() {
        assertEquals(UpgradeDenial.TESTS_NOT_PASSED,
            UpgradeWorkspacePolicy.evaluate(proposal().copy(tests = GateResult.NOT_RUN), WorkspaceState.TESTING))
    }
}
