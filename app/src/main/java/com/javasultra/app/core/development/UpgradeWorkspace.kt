package com.javasultra.app.core.development

import java.time.Instant

enum class ChangeTarget { KNOWLEDGE_ONLY, UI_MODULE, FEATURE_MODULE, DEPENDENCY, IDENTITY, PERMISSIONS, TRUST_ROOT, AUDIT, RECOVERY, PRODUCTION_RELEASE }
enum class WorkspaceState { CREATED, ISOLATED, BUILDING, TESTING, REVIEW_REQUIRED, APPROVED, REJECTED, DEPLOYED, ROLLED_BACK }
enum class GateResult { PASS, FAIL, NOT_RUN }

data class UpgradeProposal(
    val id: String,
    val summary: String,
    val target: ChangeTarget,
    val createdAt: Instant,
    val workspaceId: String,
    val tests: GateResult,
    val staticAnalysis: GateResult,
    val dependencyReview: GateResult,
    val secretScan: GateResult,
    val backupVerified: GateResult,
    val creatorApproval: Boolean,
    val rollbackPlanPresent: Boolean
)

enum class UpgradeDenial {
    EMPTY_METADATA, TRUST_BOUNDARY_PROTECTED, NOT_ISOLATED,
    TESTS_NOT_PASSED, STATIC_ANALYSIS_NOT_PASSED, DEPENDENCY_REVIEW_NOT_PASSED,
    SECRET_SCAN_NOT_PASSED, BACKUP_NOT_VERIFIED, CREATOR_APPROVAL_REQUIRED,
    ROLLBACK_PLAN_REQUIRED
}

/** Evaluates readiness only. It does not deploy code or grant execution privileges. */
object UpgradeWorkspacePolicy {
    fun evaluate(proposal: UpgradeProposal, workspaceState: WorkspaceState): UpgradeDenial? {
        if (proposal.id.isBlank() || proposal.summary.isBlank() || proposal.workspaceId.isBlank()) {
            return UpgradeDenial.EMPTY_METADATA
        }
        if (proposal.target in setOf(ChangeTarget.IDENTITY, ChangeTarget.PERMISSIONS,
                ChangeTarget.TRUST_ROOT, ChangeTarget.AUDIT, ChangeTarget.RECOVERY)) {
            return UpgradeDenial.TRUST_BOUNDARY_PROTECTED
        }
        if (workspaceState !in setOf(WorkspaceState.TESTING, WorkspaceState.REVIEW_REQUIRED)) {
            return UpgradeDenial.NOT_ISOLATED
        }
        if (proposal.tests != GateResult.PASS) return UpgradeDenial.TESTS_NOT_PASSED
        if (proposal.staticAnalysis != GateResult.PASS) return UpgradeDenial.STATIC_ANALYSIS_NOT_PASSED
        if (proposal.dependencyReview != GateResult.PASS) return UpgradeDenial.DEPENDENCY_REVIEW_NOT_PASSED
        if (proposal.secretScan != GateResult.PASS) return UpgradeDenial.SECRET_SCAN_NOT_PASSED
        if (proposal.backupVerified != GateResult.PASS) return UpgradeDenial.BACKUP_NOT_VERIFIED
        if (!proposal.creatorApproval) return UpgradeDenial.CREATOR_APPROVAL_REQUIRED
        if (!proposal.rollbackPlanPresent) return UpgradeDenial.ROLLBACK_PLAN_REQUIRED
        return null
    }
}
