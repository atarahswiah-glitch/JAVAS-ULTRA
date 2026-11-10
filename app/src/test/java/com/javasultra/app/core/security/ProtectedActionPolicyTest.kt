package com.javasultra.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectedActionPolicyTest {
    @Test fun lowRiskActionMayRunOfflineWithinItsGrant() {
        assertEquals(
            ActionPolicyDecision(false, false, true),
            ProtectedActionPolicy.forRisk(ActionRisk.LOW)
        )
    }

    @Test fun mediumRiskActionCannotRunOffline() {
        assertEquals(
            ActionPolicyDecision(false, false, false),
            ProtectedActionPolicy.forRisk(ActionRisk.MEDIUM)
        )
    }

    @Test fun highRiskActionRequiresCreatorApprovalAndStepUp() {
        assertEquals(
            ActionPolicyDecision(true, true, false),
            ProtectedActionPolicy.forRisk(ActionRisk.HIGH)
        )
    }

    @Test fun criticalRiskActionRequiresCreatorApprovalAndStepUp() {
        assertEquals(
            ActionPolicyDecision(true, true, false),
            ProtectedActionPolicy.forRisk(ActionRisk.CRITICAL)
        )
    }
}
