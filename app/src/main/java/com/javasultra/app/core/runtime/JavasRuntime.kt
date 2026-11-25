package com.javasultra.app.core.runtime

import android.content.Context
import com.javasultra.app.core.agent.ConversationResult
import com.javasultra.app.core.agent.ConversationTurnRequest
import com.javasultra.app.core.agent.CreatorConversationAuthorization
import com.javasultra.app.core.agent.CreatorConversationAuthorizationFactory
import com.javasultra.app.core.agent.DefaultModelGateway
import com.javasultra.app.core.agent.EncryptedRepositoryConversationMemoryProvider
import com.javasultra.app.core.agent.ModelProviderAdapter
import com.javasultra.app.core.agent.ProtectedConversationOrchestrator
import com.javasultra.app.core.memory.CreatorMemoryManager
import com.javasultra.app.core.memory.MemoryId
import com.javasultra.app.core.memory.MemoryKind
import com.javasultra.app.core.memory.MemoryRecord
import com.javasultra.app.core.memory.MemoryRepositoryFailure
import com.javasultra.app.core.memory.MemoryRepositoryResult
import com.javasultra.app.core.memory.MemorySensitivity
import com.javasultra.app.core.memory.EncryptedMemoryRepository
import com.javasultra.app.core.security.CreatorAccountGateway
import com.javasultra.app.core.security.CreatorAuthenticationGateway
import com.javasultra.app.core.security.CreatorDeviceIdentityProvider
import com.javasultra.app.core.security.CreatorSessionCoordinator
import com.javasultra.app.core.security.CreatorSessionResult
import com.javasultra.app.core.security.EncryptedCreatorSessionVault
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single application composition root for Creator session, encrypted memory and chat.
 *
 * No default/fake identity or model provider is installed. An empty adapter list deliberately
 * leaves chat unavailable. Every server gateway supplied here must independently enforce
 * server-side identity, account, device, expiry and authorization checks.
 */
/** UI-facing boundary; implementations must preserve Creator ownership and session checks. */
interface JavasRuntimeContract {
    suspend fun restoreSession(): CreatorSessionResult
    suspend fun signIn(identifier: String, secret: CharArray): CreatorSessionResult
    suspend fun signOut(): CreatorSessionResult
    fun hasActiveCreatorSession(now: Instant = Instant.now()): Boolean
    fun reviewMemories(now: Instant = Instant.now()): MemoryRepositoryResult<List<MemoryRecord>>
    fun proposeMemory(content: String, kind: MemoryKind, sensitivity: MemorySensitivity, now: Instant = Instant.now(), expiresAt: Instant? = null, tags: Set<String> = emptySet()): MemoryRepositoryResult<MemoryRecord>
    fun confirmMemory(id: MemoryId, now: Instant = Instant.now()): MemoryRepositoryResult<MemoryRecord>
    fun deleteMemory(id: MemoryId, now: Instant = Instant.now()): MemoryRepositoryResult<Unit>
    fun exportMemories(now: Instant = Instant.now()): MemoryRepositoryResult<List<MemoryRecord>>
    suspend fun sendMessage(message: String, offline: Boolean = false, now: Instant = Instant.now()): ConversationResult
}

class JavasRuntime private constructor(
    private val sessions: CreatorSessionCoordinator,
    private val memories: CreatorMemoryManager,
    private val conversation: ProtectedConversationOrchestrator,
    private val clock: Clock
) : JavasRuntimeContract {
    private val sessionMutex = Mutex()
    @Volatile private var authorization: CreatorConversationAuthorization? = null

    /** Restore must complete its trusted account/device checks before local authority is issued. */
    override suspend fun restoreSession(): CreatorSessionResult = sessionMutex.withLock {
        val result = sessions.restore(clock.instant())
        authorization = CreatorConversationAuthorizationFactory.fromVerifiedSession(result, clock.instant())
        result
    }

    /** The supplied PIN/password is cleared by CreatorSessionCoordinator on every sign-in path. */
    override suspend fun signIn(identifier: String, secret: CharArray): CreatorSessionResult =
        sessionMutex.withLock {
            val result = sessions.signIn(identifier, secret, clock.instant())
            authorization = CreatorConversationAuthorizationFactory.fromVerifiedSession(result, clock.instant())
            result
        }

    /** Revoke local authority immediately, then ask the session coordinator to clear/revoke tokens. */
    override suspend fun signOut(): CreatorSessionResult = sessionMutex.withLock {
        authorization = null
        sessions.signOut()
    }

    override fun hasActiveCreatorSession(now: Instant): Boolean =
        activeAuthorization(now) != null

    /** Owner is derived from the verified session; callers cannot choose another owner. */
    override fun reviewMemories(now: Instant): MemoryRepositoryResult<List<MemoryRecord>> {
        val current = activeAuthorization(now)
            ?: return MemoryRepositoryResult.Failure(MemoryRepositoryFailure.ACCESS_DENIED)
        return memories.review(current.principalId, now, stepUpAuthenticated = false)
    }

    override fun proposeMemory(
        content: String,
        kind: MemoryKind,
        sensitivity: MemorySensitivity,
        now: Instant,
        expiresAt: Instant?,
        tags: Set<String>
    ): MemoryRepositoryResult<MemoryRecord> {
        val current = activeAuthorization(now)
            ?: return MemoryRepositoryResult.Failure(MemoryRepositoryFailure.ACCESS_DENIED)
        return memories.proposeCreatorStatement(
            creatorPrincipalId = current.principalId,
            content = content,
            kind = kind,
            sensitivity = sensitivity,
            now = now,
            expiresAt = expiresAt,
            tags = tags
        )
    }

    /** Confirmation is explicit; highly-sensitive records remain denied without step-up. */
    override fun confirmMemory(id: MemoryId, now: Instant): MemoryRepositoryResult<MemoryRecord> {
        val current = activeAuthorization(now)
            ?: return MemoryRepositoryResult.Failure(MemoryRepositoryFailure.ACCESS_DENIED)
        return memories.confirm(current.principalId, id, now, stepUpAuthenticated = false)
    }

    override fun deleteMemory(id: MemoryId, now: Instant): MemoryRepositoryResult<Unit> {
        val current = activeAuthorization(now)
            ?: return MemoryRepositoryResult.Failure(MemoryRepositoryFailure.ACCESS_DENIED)
        return memories.delete(current.principalId, id, now, stepUpAuthenticated = false)
    }

    override fun exportMemories(now: Instant): MemoryRepositoryResult<List<MemoryRecord>> {
        val current = activeAuthorization(now)
            ?: return MemoryRepositoryResult.Failure(MemoryRepositoryFailure.ACCESS_DENIED)
        return memories.export(current.principalId, now, stepUpAuthenticated = false)
    }

    private fun activeAuthorization(now: Instant): CreatorConversationAuthorization? {
        val current = authorization ?: return null
        if (!now.isBefore(current.expiresAt) ||
            now.isBefore(current.authenticatedAt) ||
            Duration.between(current.authenticatedAt, now) > Duration.ofMinutes(30)
        ) {
            authorization = null
            return null
        }
        return current
    }

    /**
     * Conversation is denied unless a recently verified Creator session exists.
     * The orchestrator still rechecks expiry, age, owner-scoped memory and provider policy.
     */
    override suspend fun sendMessage(
        message: String,
        offline: Boolean,
        now: Instant
    ): ConversationResult {
        val current = activeAuthorization(now)
            ?: return ConversationResult.Denied(
                com.javasultra.app.core.agent.ConversationDenial.INVALID_AUTHORIZATION
            )
        return conversation.respond(
            ConversationTurnRequest(
                authorization = current,
                userMessage = message,
                now = now,
                offline = offline
            )
        )
    }

    companion object {
        /**
         * Builds the real encrypted local stores and wires them to injected trusted gateways.
         * Provider implementations are supplied by the host app; none are fabricated here.
         */
        fun create(
            context: Context,
            authentication: CreatorAuthenticationGateway,
            accounts: CreatorAccountGateway,
            deviceIdentity: CreatorDeviceIdentityProvider,
            modelProviders: List<ModelProviderAdapter>,
            clock: Clock = Clock.systemUTC()
        ): JavasRuntime {
            val appContext = context.applicationContext
            val sessions = CreatorSessionCoordinator(
                authentication = authentication,
                accounts = accounts,
                deviceIdentity = deviceIdentity,
                vault = EncryptedCreatorSessionVault(appContext)
            )
            val repository = EncryptedMemoryRepository(appContext)
            val memoryManager = CreatorMemoryManager(repository)
            val memoryContext = EncryptedRepositoryConversationMemoryProvider(repository, clock)
            val modelGateway = DefaultModelGateway(modelProviders)
            val conversation = ProtectedConversationOrchestrator(memoryContext, modelGateway)
            return JavasRuntime(sessions, memoryManager, conversation, clock)
        }
    }
}
