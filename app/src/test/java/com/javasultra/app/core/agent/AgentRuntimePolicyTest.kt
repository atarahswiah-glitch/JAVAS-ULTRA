package com.javasultra.app.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class AgentRuntimePolicyTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private fun request(
        effect: ToolEffect = ToolEffect.READ_ONLY,
        approvalRequired: Boolean = false,
        approvalGranted: Boolean = false
    ): RuntimeAuthorizationRequest {
        val task = AgentTaskEnvelope("task-1", "Research a public science topic", now.minusSeconds(10),
            setOf("web-read"), now.plusSeconds(600), 10, 5000, approvalRequired, approvalGranted)
        val tool = ToolDescriptor("web-read", "1", "Read approved public web pages",
            setOf(RuntimeModality.TEXT), effect, setOf(ToolPermission.WEB_PUBLIC_READ), setOf("nasa.gov"), true)
        return RuntimeAuthorizationRequest(task, tool, setOf(ToolPermission.WEB_PUBLIC_READ), 0, now)
    }

    @Test fun permitsTaskScopedReadOnlyTool() = assertNull(AgentRuntimePolicy.evaluate(request()))
    @Test fun requiresCreatorApprovalForExternalSideEffect() =
        assertEquals(RuntimeDenial.PRIVILEGED_ACTION_REQUIRES_APPROVAL, AgentRuntimePolicy.evaluate(request(ToolEffect.EXTERNAL_SIDE_EFFECT, true, false)))
    @Test fun permitsExternalSideEffectOnlyWithExplicitApprovalBindingPreflight() =
        assertNull(AgentRuntimePolicy.evaluate(request(ToolEffect.EXTERNAL_SIDE_EFFECT, true, true)))
    @Test fun rejectsUnlistedTool() {
        val r = request()
        assertEquals(RuntimeDenial.TOOL_NOT_ALLOWED, AgentRuntimePolicy.evaluate(r.copy(task = r.task.copy(allowedTools = emptySet()))))
    }
    @Test fun enforcesCallBudget() {
        val r = request()
        assertEquals(RuntimeDenial.BUDGET_EXCEEDED, AgentRuntimePolicy.evaluate(r.copy(callsAlreadyUsed = 10)))
    }
    @Test fun requiresGrantedToolPermission() {
        val r = request()
        assertEquals(RuntimeDenial.PERMISSION_MISSING, AgentRuntimePolicy.evaluate(r.copy(grantedPermissions = emptySet())))
    }
}
