package com.javasultra.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationSubmissionPolicyTest {
    @Test fun rejectsBlankMessageWithoutCallingProvider() {
        var calls = 0
        val engine = ConversationEngine { calls++; "reply" }

        assertEquals(ConversationSubmission.Empty, ConversationSubmissionPolicy.submit("  \n ", engine))
        assertEquals(0, calls)
    }

    @Test fun reportsUnavailableProviderWithoutInventingReply() {
        val result = ConversationSubmissionPolicy.submit(
            "  Hello JAVAS  ",
            UnavailableConversationEngine
        )

        assertEquals(ConversationSubmission.EngineUnavailable, result)
    }

    @Test fun normalizesInputAndReturnsProviderReply() {
        var received: String? = null
        val engine = ConversationEngine { prompt ->
            received = prompt
            "  Hello, Creator.  "
        }

        val result = ConversationSubmissionPolicy.submit("  Hello  ", engine)

        assertEquals("Hello", received)
        assertEquals(ConversationSubmission.Replied("Hello, Creator."), result)
    }

    @Test fun treatsBlankProviderOutputAsUnavailable() {
        val result = ConversationSubmissionPolicy.submit("Question", ConversationEngine { "  " })

        assertEquals(ConversationSubmission.EngineUnavailable, result)
    }
}
