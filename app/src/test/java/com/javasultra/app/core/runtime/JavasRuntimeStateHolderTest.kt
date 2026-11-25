package com.javasultra.app.core.runtime

import com.javasultra.app.core.agent.ConversationResult
import com.javasultra.app.core.agent.ConversationDenial
import com.javasultra.app.core.memory.MemoryId
import com.javasultra.app.core.memory.MemoryKind
import com.javasultra.app.core.memory.MemoryRecord
import com.javasultra.app.core.memory.MemoryRepositoryFailure
import com.javasultra.app.core.memory.MemoryRepositoryResult
import com.javasultra.app.core.memory.MemorySensitivity
import com.javasultra.app.core.security.CreatorSessionResult
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JavasRuntimeStateHolderTest {
    @Test
    fun restoreWithoutStoredSessionShowsSignedOut() {
        val holder = JavasRuntimeStateHolder(FakeRuntime(), Dispatchers.Unconfined)
        holder.restore()

        assertEquals(CreatorUiSessionState.SIGNED_OUT, holder.state.value.session)
        assertFalse(holder.state.value.busy)
        holder.close()
    }

    @Test
    fun deniedSignInDoesNotCreateAuthenticatedUiState() {
        val holder = JavasRuntimeStateHolder(FakeRuntime(), Dispatchers.Unconfined)
        holder.signIn("creator", charArrayOf('1', '2', '3', '4'))

        assertEquals(CreatorUiSessionState.SIGNED_OUT, holder.state.value.session)
        assertEquals("Sign-in was denied: AUTHENTICATION_FAILED.", holder.state.value.error)
        holder.close()
    }

    @Test
    fun signOutClearsInMemoryChatAndPrivateMemoryState() {
        val holder = JavasRuntimeStateHolder(FakeRuntime(), Dispatchers.Unconfined)
        holder.signOut()

        assertEquals(CreatorUiSessionState.SIGNED_OUT, holder.state.value.session)
        assertTrueEmpty(holder.state.value.chat)
        assertTrueEmpty(holder.state.value.memories)
        assertEquals(null, holder.state.value.exportSnapshot)
        holder.close()
    }

    private fun assertTrueEmpty(value: Collection<*>) = assertEquals(0, value.size)

    private class FakeRuntime : JavasRuntimeContract {
        override suspend fun restoreSession() = CreatorSessionResult.NoSession
        override suspend fun signIn(identifier: String, secret: CharArray) =
            CreatorSessionResult.Denied(com.javasultra.app.core.security.CreatorSessionDenial.AUTHENTICATION_FAILED)
        override suspend fun signOut() = CreatorSessionResult.SignedOut(serverRevocationConfirmed = true)
        override fun reviewMemories(now: Instant) =
            MemoryRepositoryResult.Failure(MemoryRepositoryFailure.ACCESS_DENIED)
        override fun proposeMemory(content: String, kind: MemoryKind, sensitivity: MemorySensitivity, now: Instant, expiresAt: Instant?, tags: Set<String>) =
            MemoryRepositoryResult.Failure(MemoryRepositoryFailure.ACCESS_DENIED)
        override fun confirmMemory(id: MemoryId, now: Instant) =
            MemoryRepositoryResult.Failure(MemoryRepositoryFailure.ACCESS_DENIED)
        override fun deleteMemory(id: MemoryId, now: Instant) =
            MemoryRepositoryResult.Failure(MemoryRepositoryFailure.ACCESS_DENIED)
        override fun exportMemories(now: Instant) =
            MemoryRepositoryResult.Failure(MemoryRepositoryFailure.ACCESS_DENIED)
        override suspend fun sendMessage(message: String, offline: Boolean, now: Instant) =
            ConversationResult.Denied(ConversationDenial.INVALID_AUTHORIZATION)
    }
}
