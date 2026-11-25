package com.javasultra.app.core.runtime

import com.javasultra.app.core.agent.ConversationResult
import com.javasultra.app.core.memory.MemoryId
import com.javasultra.app.core.memory.MemoryKind
import com.javasultra.app.core.memory.MemoryLifecycle
import com.javasultra.app.core.memory.MemoryRecord
import com.javasultra.app.core.memory.MemoryRepositoryResult
import com.javasultra.app.core.memory.MemorySensitivity
import com.javasultra.app.core.security.CreatorSessionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CreatorUiSessionState { CHECKING, SIGNED_OUT, SIGNED_IN, DEVICE_VERIFICATION_REQUIRED, ERROR }

data class JavasChatLine(val speaker: Speaker, val text: String) {
    enum class Speaker { CREATOR, JAVAS }
}

data class JavasUiState(
    val session: CreatorUiSessionState = CreatorUiSessionState.CHECKING,
    val busy: Boolean = false,
    val chat: List<JavasChatLine> = emptyList(),
    val memories: List<MemoryRecord> = emptyList(),
    val memoriesLoaded: Boolean = false,
    val error: String? = null,
    val exportSnapshot: List<MemoryRecord>? = null
)

/**
 * Lifecycle-owned state holder for Compose or another UI. It contains no credentials or tokens.
 * Close it when the owning screen/application scope is destroyed.
 *
 * The chat transcript exists only in process memory and is cleared on sign-out. It is not
 * persisted. Memory changes remain proposals until the Creator explicitly confirms them.
 */
class JavasRuntimeStateHolder(
    private val runtime: JavasRuntimeContract,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(JavasUiState())
    val state: StateFlow<JavasUiState> = mutableState.asStateFlow()

    fun restore() = launchBusy {
        val restored = try { runtime.restoreSession() } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            signedOutWithError("Session restoration is unavailable.")
            return@launchBusy
        }
        when (val result = restored) {
            is CreatorSessionResult.Authenticated -> update { it.copy(session = CreatorUiSessionState.SIGNED_IN, error = null) }
            is CreatorSessionResult.DeviceVerificationRequired -> update {
                it.copy(session = CreatorUiSessionState.DEVICE_VERIFICATION_REQUIRED, error = "This device needs Creator verification.")
            }
            CreatorSessionResult.NoSession -> update { it.copy(session = CreatorUiSessionState.SIGNED_OUT, error = null) }
            is CreatorSessionResult.Denied -> signedOutWithError("Session could not be restored: ${result.reason}.")
            is CreatorSessionResult.SignedOut -> update { it.copy(session = CreatorUiSessionState.SIGNED_OUT) }
        }
    }

    fun signIn(identifier: String, secret: CharArray) = launchBusy {
        val result = try {
            runtime.signIn(identifier, secret)
        } catch (cancelled: CancellationException) {
            secret.fill('\u0000')
            throw cancelled
        } catch (_: Exception) {
            secret.fill('\u0000')
            signedOutWithError("Sign-in could not be completed.")
            return@launchBusy
        }
        when (result) {
            is CreatorSessionResult.Authenticated -> update {
                it.copy(session = CreatorUiSessionState.SIGNED_IN, chat = emptyList(), memories = emptyList(), memoriesLoaded = false, error = null)
            }
            is CreatorSessionResult.DeviceVerificationRequired -> update {
                it.copy(session = CreatorUiSessionState.DEVICE_VERIFICATION_REQUIRED, error = "This device needs Creator verification.")
            }
            is CreatorSessionResult.Denied -> signedOutWithError("Sign-in was denied: ${result.reason}.")
            else -> signedOutWithError("No authenticated Creator session was established.")
        }
    }

    fun signOut() = launchBusy {
        val result = try { runtime.signOut() } catch (cancelled: CancellationException) {
            update {
                it.copy(session = CreatorUiSessionState.SIGNED_OUT, chat = emptyList(), memories = emptyList(), memoriesLoaded = false, exportSnapshot = null)
            }
            throw cancelled
        } catch (_: Exception) {
            update {
                it.copy(
                    session = CreatorUiSessionState.SIGNED_OUT,
                    chat = emptyList(),
                    memories = emptyList(),
                    memoriesLoaded = false,
                    exportSnapshot = null,
                    error = "Signed out locally, but the sign-out service could not confirm server revocation."
                )
            }
            return@launchBusy
        }
        update {
            it.copy(
                session = CreatorUiSessionState.SIGNED_OUT,
                chat = emptyList(),
                memories = emptyList(),
                memoriesLoaded = false,
                exportSnapshot = null,
                error = if (result is CreatorSessionResult.SignedOut && !result.serverRevocationConfirmed)
                    "Signed out locally. Server revocation could not be confirmed."
                else null
            )
        }
    }

    fun sendMessage(message: String, offline: Boolean = false) = launchBusy {
        if (!requireActiveSession()) return@launchBusy
        when (val result = runtime.sendMessage(message, offline)) {
            is ConversationResult.Success -> update {
                it.copy(
                    chat = (it.chat + listOf(
                        JavasChatLine(JavasChatLine.Speaker.CREATOR, message.trim()),
                        JavasChatLine(JavasChatLine.Speaker.JAVAS, result.turn.answer)
                    )).takeLast(MAX_CHAT_LINES),
                    error = null
                )
            }
            is ConversationResult.Denied -> fail("JAVAS could not answer: ${result.reason}.")
        }
    }

    fun refreshMemories() = launchBusy {
        if (!requireActiveSession()) return@launchBusy
        when (val result = withContext(Dispatchers.IO) { runtime.reviewMemories() }) {
            is MemoryRepositoryResult.Success -> update { it.copy(memories = result.value, memoriesLoaded = true, error = null) }
            is MemoryRepositoryResult.Failure -> fail("Memory review failed: ${result.reason}.")
        }
    }

    fun proposeMemory(
        content: String,
        kind: MemoryKind,
        sensitivity: MemorySensitivity,
        tags: Set<String> = emptySet()
    ) = launchBusy {
        if (!requireActiveSession()) return@launchBusy
        when (val result = withContext(Dispatchers.IO) {
            runtime.proposeMemory(content, kind, sensitivity, tags = tags)
        }) {
            is MemoryRepositoryResult.Success -> update {
                it.copy(memories = it.memories + result.value, memoriesLoaded = true, error = "Memory saved as a proposal. Confirm it explicitly to activate it.")
            }
            is MemoryRepositoryResult.Failure -> fail("Memory proposal failed: ${result.reason}.")
        }
    }

    fun confirmMemory(id: MemoryId) = launchBusy {
        if (!requireActiveSession()) return@launchBusy
        when (val result = withContext(Dispatchers.IO) { runtime.confirmMemory(id) }) {
            is MemoryRepositoryResult.Success -> update {
                it.copy(memories = replaceMemory(it.memories, result.value), error = null)
            }
            is MemoryRepositoryResult.Failure -> fail("Memory confirmation failed: ${result.reason}.")
        }
    }

    fun deleteMemory(id: MemoryId) = launchBusy {
        if (!requireActiveSession()) return@launchBusy
        when (val result = withContext(Dispatchers.IO) { runtime.deleteMemory(id) }) {
            is MemoryRepositoryResult.Success -> update {
                it.copy(memories = it.memories.filterNot { memory -> memory.id == id }, error = null)
            }
            is MemoryRepositoryResult.Failure -> fail("Memory deletion failed: ${result.reason}.")
        }
    }

    fun prepareExport() = launchBusy {
        if (!requireActiveSession()) return@launchBusy
        when (val result = withContext(Dispatchers.IO) { runtime.exportMemories() }) {
            is MemoryRepositoryResult.Success -> update { it.copy(exportSnapshot = result.value, error = null) }
            is MemoryRepositoryResult.Failure -> fail("Memory export failed: ${result.reason}.")
        }
    }

    fun clearExportSnapshot() = update { it.copy(exportSnapshot = null) }

    fun close() { scope.cancel() }

    private fun launchBusy(block: suspend () -> Unit) {
        scope.launch {
            update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                fail("The requested operation could not be completed.")
            } finally {
                update { it.copy(busy = false) }
            }
        }
    }

    /** Call on app resume and before each private operation to hide stale private UI state. */
    fun validateSession() {
        if (mutableState.value.session == CreatorUiSessionState.SIGNED_IN) requireActiveSession()
    }

    private fun requireActiveSession(): Boolean {
        if (mutableState.value.session != CreatorUiSessionState.SIGNED_IN) {
            fail("Creator sign-in is required for this operation.")
            return false
        }
        if (!runtime.hasActiveCreatorSession()) {
            signedOutWithError("Your Creator session has expired or needs fresh authentication. Sign in again.")
            return false
        }
        return true
    }

    private fun signedOutWithError(message: String) = update {
        it.copy(session = CreatorUiSessionState.SIGNED_OUT, chat = emptyList(), memories = emptyList(), memoriesLoaded = false, error = message)
    }

    private fun fail(message: String) = update { it.copy(error = message) }
    private fun update(transform: (JavasUiState) -> JavasUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun replaceMemory(existing: List<MemoryRecord>, replacement: MemoryRecord): List<MemoryRecord> {
        val withoutSameId = existing.filterNot { it.id == replacement.id }
        val superseded = if (replacement.lifecycle == MemoryLifecycle.ACTIVE && replacement.supersedes != null) {
            withoutSameId.map { record ->
                if (record.id == replacement.supersedes) record.copy(lifecycle = MemoryLifecycle.SUPERSEDED) else record
            }
        } else withoutSameId
        return (superseded + replacement).sortedByDescending { it.updatedAt }
    }

    private companion object { const val MAX_CHAT_LINES = 50 }
}
