package com.javasultra.app

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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Provider-neutral boundary. A production model adapter must be explicitly configured. */
fun interface ConversationEngine {
    fun generateReply(userMessage: String): String?
}

object UnavailableConversationEngine : ConversationEngine {
    override fun generateReply(userMessage: String): String? = null
}

sealed interface ConversationSubmission {
    data object Empty : ConversationSubmission
    data object EngineUnavailable : ConversationSubmission
    data class Replied(val text: String) : ConversationSubmission
}

object ConversationSubmissionPolicy {
    fun submit(message: String, engine: ConversationEngine): ConversationSubmission {
        val normalized = message.trim()
        if (normalized.isEmpty()) return ConversationSubmission.Empty
        val reply = engine.generateReply(normalized)?.trim()
        return if (reply.isNullOrEmpty()) ConversationSubmission.EngineUnavailable
        else ConversationSubmission.Replied(reply)
    }
}

private data class ConversationLine(val text: String, val fromCreator: Boolean)

@Composable
internal fun ConversationPanel(
    modifier: Modifier = Modifier,
    engine: ConversationEngine = UnavailableConversationEngine
) {
    val lines = remember { mutableStateListOf<ConversationLine>() }
    var draft by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Conversation", color = Color(0xFFE7FAFF), style = MaterialTheme.typography.titleMedium)
        if (lines.isEmpty()) {
            Text(
                "Your private conversation space is ready. The AI model connection is not configured yet.",
                color = Color(0xFF9CB7C5),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(lines) { line ->
                    Surface(
                        color = if (line.fromCreator) Color(0xFF16445A) else Color(0xFF183040),
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
            enabled = true,
            singleLine = false,
            maxLines = 3
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = {
                    when (val result = ConversationSubmissionPolicy.submit(draft, engine)) {
                        ConversationSubmission.Empty -> Unit
                        ConversationSubmission.EngineUnavailable -> {
                            lines += ConversationLine(draft.trim(), true)
                            lines += ConversationLine(
                                "Not sent: the AI model provider is not connected. Your message was not transmitted.",
                                false
                            )
                            draft = ""
                        }
                        is ConversationSubmission.Replied -> {
                            lines += ConversationLine(draft.trim(), true)
                            lines += ConversationLine(result.text, false)
                            draft = ""
                        }
                    }
                },
                enabled = draft.isNotBlank()
            ) {
                Text("Send")
            }
        }
    }
}
