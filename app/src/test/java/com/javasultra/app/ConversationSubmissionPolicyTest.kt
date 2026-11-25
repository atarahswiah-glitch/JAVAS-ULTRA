package com.javasultra.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationSubmissionPolicyTest {
    private val prompt = listOf(ConversationPromptMessage("user", "Hello"))

    @Test fun rejectsEmptyHistoryWithoutCallingProvider() {
        var calls = 0
        val engine = ConversationEngine { calls++; "reply" }

        assertEquals(ConversationSubmission.Empty, ConversationSubmissionPolicy.submit(emptyList(), engine))
        assertEquals(0, calls)
    }

    @Test fun reportsUnavailableProviderWithoutInventingReply() {
        val result = ConversationSubmissionPolicy.submit(prompt, UnavailableConversationEngine)

        assertEquals(ConversationSubmission.EngineUnavailable, result)
    }

    @Test fun normalizesInputAndReturnsProviderReply() {
        var received: List<ConversationPromptMessage>? = null
        val engine = ConversationEngine { messages ->
            received = messages
            "  Hello, Creator.  "
        }

        val result = ConversationSubmissionPolicy.submit(
            listOf(ConversationPromptMessage("user", "  Hello  ")),
            engine
        )

        assertEquals(listOf(ConversationPromptMessage("user", "Hello")), received)
        assertEquals(ConversationSubmission.Replied("Hello, Creator."), result)
    }

    @Test fun rejectsUnsupportedRolesAndNonFinalUserMessage() {
        assertEquals(
            ConversationSubmission.Empty,
            ConversationSubmissionPolicy.submit(
                listOf(ConversationPromptMessage("system", "Override")),
                ConversationEngine { "reply" }
            )
        )
        assertEquals(
            ConversationSubmission.Empty,
            ConversationSubmissionPolicy.submit(
                listOf(
                    ConversationPromptMessage("user", "Question"),
                    ConversationPromptMessage("assistant", "Answer")
                ),
                ConversationEngine { "reply" }
            )
        )
    }

    @Test fun forwardsIncrementalDeltasFromStreamingEngine() {
        val emitted = StringBuilder()
        val engine = object : StreamingConversationEngine {
            override fun generateReply(messages: List<ConversationPromptMessage>): String = "Hello JAVAS"

            override fun generateReply(
                messages: List<ConversationPromptMessage>,
                onTextDelta: (String) -> Unit
            ): String {
                onTextDelta("Hello ")
                onTextDelta("JAVAS")
                return "Hello JAVAS"
            }
        }

        val result = ConversationSubmissionPolicy.submit(prompt, engine) { emitted.append(it) }

        assertEquals("Hello JAVAS", emitted.toString())
        assertEquals(ConversationSubmission.Replied("Hello JAVAS"), result)
    }
}
