package com.javasultra.app

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.concurrent.Executors

data class ConversationPromptMessage(val role: String, val content: String)

/** Provider-neutral boundary. Implementations must use an authenticated server-side gateway. */
fun interface ConversationEngine {
    fun generateReply(messages: List<ConversationPromptMessage>): String?
}

/** Optional incremental response interface used by streaming providers. */
interface StreamingConversationEngine : ConversationEngine {
    fun generateReply(
        messages: List<ConversationPromptMessage>,
        onTextDelta: (String) -> Unit
    ): String?

    override fun generateReply(messages: List<ConversationPromptMessage>): String? =
        generateReply(messages) {}
}

object UnavailableConversationEngine : ConversationEngine {
    override fun generateReply(messages: List<ConversationPromptMessage>): String? = null
}

sealed interface ConversationSubmission {
    data object Empty : ConversationSubmission
    data object EngineUnavailable : ConversationSubmission
    data class Replied(val text: String) : ConversationSubmission
}

object ConversationSubmissionPolicy {
    fun submit(
        messages: List<ConversationPromptMessage>,
        engine: ConversationEngine,
        onTextDelta: (String) -> Unit = {}
    ): ConversationSubmission {
        if (messages.isEmpty() || messages.size > MAX_CONTEXT_MESSAGES) return ConversationSubmission.Empty
        val normalized = messages.map { it.copy(content = it.content.trim()) }
        if (normalized.any { it.content.isEmpty() || it.role !in setOf("user", "assistant") }) {
            return ConversationSubmission.Empty
        }
        if (normalized.last().role != "user") return ConversationSubmission.Empty

        val reply = if (engine is StreamingConversationEngine) {
            engine.generateReply(normalized, onTextDelta)
        } else {
            engine.generateReply(normalized)
        }?.trim()
        return if (reply.isNullOrEmpty()) ConversationSubmission.EngineUnavailable
        else ConversationSubmission.Replied(reply)
    }

    const val MAX_CONTEXT_MESSAGES = 40
}

private data class ConversationLine(
    val text: String,
    val fromCreator: Boolean,
    val isError: Boolean = false
)

@Composable
internal fun ConversationPanel(
    modifier: Modifier = Modifier,
    engine: ConversationEngine = UnavailableConversationEngine
) {
    val lines = remember { mutableStateListOf<ConversationLine>() }
    var draft by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val worker = remember(engine) { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(worker) {
        onDispose { worker.shutdownNow() }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Conversation", color = Color(0xFFE7FAFF), style = MaterialTheme.typography.titleMedium)
        if (lines.isEmpty()) {
            Text(
                "Your private conversation space is ready. Sign in and connect the JAVAS AI gateway to begin.",
                color = Color(0xFF9CB7C5),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(lines) { line ->
                    Surface(
                        color = when {
                            line.isError -> Color(0xFF49331F)
                            line.fromCreator -> Color(0xFF16445A)
                            else -> Color(0xFF183040)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            line.text,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                            color = Color(0xFFE7FAFF),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Write a message") },
            enabled = !isSending,
            singleLine = false,
            maxLines = 3
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = {
                    val submitted = draft.trim()
                    if (submitted.isNotEmpty() && !isSending) {
                        val context = lines.filterNot { it.isError || it.text.isBlank() }.map {
                            ConversationPromptMessage(
                                role = if (it.fromCreator) "user" else "assistant",
                                content = it.text
                            )
                        }.takeLast(ConversationSubmissionPolicy.MAX_CONTEXT_MESSAGES - 1) +
                            ConversationPromptMessage("user", submitted)

                        lines += ConversationLine(submitted, true)
                        val replyIndex = lines.size
                        lines += ConversationLine("JAVAS is thinking…", false)
                        draft = ""
                        isSending = true

                        worker.execute {
                            var streamedText = ""
                            val result = try {
                                ConversationSubmissionPolicy.submit(context, engine) { delta ->
                                    streamedText += delta
                                    mainHandler.post {
                                        if (replyIndex < lines.size) {
                                            lines[replyIndex] = ConversationLine(streamedText, false)
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                null
                            }

                            mainHandler.post {
                                if (replyIndex < lines.size) {
                                    lines[replyIndex] = when (result) {
                                        is ConversationSubmission.Replied ->
                                            ConversationLine(result.text, false)
                                        ConversationSubmission.EngineUnavailable ->
                                            ConversationLine(
                                                "JAVAS could not complete the request. Check your sign-in and gateway connection.",
                                                false,
                                                true
                                            )
                                        ConversationSubmission.Empty, null ->
                                            ConversationLine(
                                                "JAVAS could not complete the request. No response was received.",
                                                false,
                                                true
                                            )
                                    }
                                }
                                isSending = false
                            }
                        }
                    }
                },
                enabled = draft.isNotBlank() && !isSending
            ) {
                Text(if (isSending) "Sending…" else "Send")
            }
        }
    }
}
