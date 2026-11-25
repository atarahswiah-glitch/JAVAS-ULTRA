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
import com.javasultra.app.core.memory.EncryptedMemoryRepository
import com.javasultra.app.core.security.CreatorAccountGateway
import com.javasultra.app.core.security.CreatorAuthenticationGateway
import com.javasultra.app.core.security.CreatorDeviceIdentityProvider
import com.javasultra.app.core.security.CreatorSessionCoordinator
import com.javasultra.app.core.security.CreatorSessionResult
import com.javasultra.app.core.security.EncryptedCreatorSessionVault
import java.time.Clock
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
class JavasRuntime private constructor(
    val sessions: CreatorSessionCoordinator,
    val memories: CreatorMemoryManager,
    private val conversation: ProtectedConversationOrchestrator,
    private val clock: Clock
) {
    private val sessionMutex = Mutex()
    @Volatile private var authorization: CreatorConversationAuthorization? = null

    /** Restore must complete its trusted account/device checks before local authority is issued. */
    suspend fun restoreSession(): CreatorSessionResult = sessionMutex.withLock {
        val result = sessions.restore(clock.instant())
        authorization = CreatorConversationAuthorizationFactory.fromVerifiedSession(result, clock.instant())
        result
    }

    /** The supplied PIN/password is cleared by CreatorSessionCoordinator on every sign-in path. */
    suspend fun signIn(identifier: String, secret: CharArray): CreatorSessionResult =
        sessionMutex.withLock {
            val result = sessions.signIn(identifier, secret, clock.instant())
            authorization = CreatorConversationAuthorizationFactory.fromVerifiedSession(result, clock.instant())
            result
        }

    /** Revoke local authority immediately, then ask the session coordinator to clear/revoke tokens. */
    suspend fun signOut(): CreatorSessionResult = sessionMutex.withLock {
        authorization = null
        sessions.signOut()
    }

    fun hasActiveCreatorSession(now: Instant = clock.instant()): Boolean {
        val current = authorization ?: return false
        if (!now.isBefore(current.expiresAt)) {
            authorization = null
            return false
        }
        return true
    }

    /**
     * Conversation is denied unless a recently verified Creator session exists.
     * The orchestrator still rechecks expiry, age, owner-scoped memory and provider policy.
     */
    suspend fun sendMessage(
        message: String,
        offline: Boolean = false,
        now: Instant = clock.instant()
    ): ConversationResult {
        val current = authorization
            ?: return ConversationResult.Denied(
                com.javasultra.app.core.agent.ConversationDenial.INVALID_AUTHORIZATION
            )
        if (!now.isBefore(current.expiresAt)) {
            authorization = null
            return ConversationResult.Denied(
                com.javasultra.app.core.agent.ConversationDenial.SESSION_EXPIRED
            )
        }
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
