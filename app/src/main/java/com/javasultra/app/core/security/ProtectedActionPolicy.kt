package com.javasultra.app.core.security

/**
 * Risk must be assigned or confirmed by trusted policy, never accepted solely
 * from an untrusted client or tool proposal.
 */
enum class ActionRisk {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class ActionPolicyDecision(
    val requiresExplicitCreatorApproval: Boolean,
    val requiresStepUpAuthentication: Boolean,
    val mayExecuteOffline: Boolean
)

object ProtectedActionPolicy {
    fun forRisk(risk: ActionRisk): ActionPolicyDecision = when (risk) {
        ActionRisk.LOW -> ActionPolicyDecision(
            requiresExplicitCreatorApproval = false,
            requiresStepUpAuthentication = false,
            mayExecuteOffline = true
        )
        ActionRisk.MEDIUM -> ActionPolicyDecision(
            requiresExplicitCreatorApproval = false,
            requiresStepUpAuthentication = false,
            mayExecuteOffline = false
        )
        ActionRisk.HIGH,
        ActionRisk.CRITICAL -> ActionPolicyDecision(
            requiresExplicitCreatorApproval = true,
            requiresStepUpAuthentication = true,
            mayExecuteOffline = false
        )
    }
}
