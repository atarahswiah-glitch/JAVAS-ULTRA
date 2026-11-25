package com.javasultra.app.core.agent

import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProtectedConversationOrchestratorTest {
    private val now = Instant.parse("2026-11-25T10:00:00Z")
    private val authorization = CreatorConversationAuthorization(
        principalId = "creator-1",
        deviceId = "device-1",
        authenticatedAt = now.minusSeconds(60),
        expiresAt = now.plusSeconds(3_600),
        verifiedFactorCount = 1,
        localOnly = false
    )

    private class FakeMemory(
        private val result: Result<ApprovedConversationMemory> = Result.success(
            ApprovedConversationMemory("Creator prefers clear explanations.", 1)
        )
    ) : ConversationMemoryContextProvider {
        var calls = 0
        var principalSeen: String? = null
        override suspend fun loadApprovedContext(principalId: String, query: String): Result<ApprovedConversationMemory> {
            calls++
            principalSeen = principalId
            return result
        }
    }

    private class FakeGateway(
        private val result: Result<ModelResponse> = Result.success(
            ModelResponse("Hello, Creator.", emptyList(), 200, 3, Instant.parse("2026-11-25T10:00:02Z"), "local-test")
        )
    ) : ModelGateway {
        var requestSeen: ModelRequest? = null
        var offlineSeen: Boolean? = null
        override suspend fun generate(request: ModelRequest, offline: Boolean, now: Instant): Result<ModelResponse> {
            requestSeen = request
            offlineSeen = offline
            return result
        }
    }

    @Test fun sendsCreatorPrivateRequestWithApprovedMemoryAndNoTools() = runBlocking {
        val memory = FakeMemory()
        val gateway = FakeGateway()
        val result = ProtectedConversationOrchestrator(memory, gateway).respond(
            ConversationTurnRequest(authorization, "Hello JAVAS", now, offline = true)
        )
        assertTrue(result is ConversationResult.Success)
        assertEquals("creator-1", memory.principalSeen)
        assertEquals(DataClassification.CREATOR_PRIVATE, gateway.requestSeen?.dataClassification)
        assertFalse(gateway.requestSeen!!.requestedToolSupport)
        assertEquals(setOf(RuntimeModality.TEXT), gateway.requestSeen!!.modalities)
        assertTrue(gateway.offlineSeen == true)
        assertEquals("local-test", (result as ConversationResult.Success).turn.providerId)
    }

    @Test fun rejectsInvalidAuthenticationBeforeReadingMemory() = runBlocking {
        val memory = FakeMemory()
        val auth = authorization.copy(localOnly = true)
        val result = ProtectedConversationOrchestrator(memory, FakeGateway()).respond(
            ConversationTurnRequest(auth, "Hello", now, offline = true)
        )
        assertEquals(ConversationDenial.INVALID_AUTHORIZATION, (result as ConversationResult.Denied).reason)
        assertEquals(0, memory.calls)
    }

    @Test fun rejectsExpiredSessionBeforeReadingMemory() = runBlocking {
        val memory = FakeMemory()
        val auth = authorization.copy(expiresAt = now)
        val result = ProtectedConversationOrchestrator(memory, FakeGateway()).respond(
            ConversationTurnRequest(auth, "Hello", now, offline = false)
        )
        assertEquals(ConversationDenial.SESSION_EXPIRED, (result as ConversationResult.Denied).reason)
        assertEquals(0, memory.calls)
    }

    @Test fun rejectsStaleAuthentication() = runBlocking {
        val auth = authorization.copy(authenticatedAt = now.minusSeconds(31 * 60))
        val result = ProtectedConversationOrchestrator(FakeMemory(), FakeGateway()).respond(
            ConversationTurnRequest(auth, "Hello", now, offline = false)
        )
        assertEquals(ConversationDenial.AUTHENTICATION_TOO_OLD, (result as ConversationResult.Denied).reason)
    }

    @Test fun rejectsBlankAndOversizedMessages() = runBlocking {
        val service = ProtectedConversationOrchestrator(FakeMemory(), FakeGateway())
        val blank = service.respond(ConversationTurnRequest(authorization, "  ", now, false))
        val long = service.respond(ConversationTurnRequest(authorization, "x".repeat(12_001), now, false))
        assertEquals(ConversationDenial.INVALID_MESSAGE, (blank as ConversationResult.Denied).reason)
        assertEquals(ConversationDenial.INVALID_MESSAGE, (long as ConversationResult.Denied).reason)
    }

    @Test fun refusesWhenApprovedMemoryCannotBeLoaded() = runBlocking {
        val memory = FakeMemory(Result.failure(IllegalStateException("storage unavailable")))
        val result = ProtectedConversationOrchestrator(memory, FakeGateway()).respond(
            ConversationTurnRequest(authorization, "Hello", now, false)
        )
        assertEquals(ConversationDenial.MEMORY_UNAVAILABLE, (result as ConversationResult.Denied).reason)
    }

    @Test fun refusesWhenNoProviderIsCompatible() = runBlocking {
        val gateway = FakeGateway(Result.failure(IllegalStateException("no provider")))
        val result = ProtectedConversationOrchestrator(FakeMemory(), gateway).respond(
            ConversationTurnRequest(authorization, "Hello", now, offline = true)
        )
        assertEquals(ConversationDenial.NO_COMPATIBLE_PROVIDER, (result as ConversationResult.Denied).reason)
    }

    @Test fun rejectsModelToolProposalsInConversationPath() = runBlocking {
        val proposal = ProposedToolCall("proposal-1", "send-message", mapOf("to" to "someone"))
        val response = ModelResponse(
            "I have sent it.", listOf(proposal), 100, 5,
            now.plusSeconds(2), "local-test"
        )
        val result = ProtectedConversationOrchestrator(FakeMemory(), FakeGateway(Result.success(response))).respond(
            ConversationTurnRequest(authorization, "Send a message", now, false)
        )
        assertEquals(ConversationDenial.PROVIDER_RESPONSE_INVALID, (result as ConversationResult.Denied).reason)
    }

    @Test fun refusesProviderResponseWithoutProviderIdentity() = runBlocking {
        val response = ModelResponse("Hello", emptyList(), 100, 2, now.plusSeconds(2))
        val result = ProtectedConversationOrchestrator(FakeMemory(), FakeGateway(Result.success(response))).respond(
            ConversationTurnRequest(authorization, "Hello", now, false)
        )
        assertEquals(ConversationDenial.PROVIDER_RESPONSE_INVALID, (result as ConversationResult.Denied).reason)
    }
}
