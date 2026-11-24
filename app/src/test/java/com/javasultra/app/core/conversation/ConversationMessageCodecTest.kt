package com.javasultra.app.core.conversation

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMessageCodecTest {
    private val sample = ConversationMessage(
        id = "message-1",
        ownerPrincipalId = "creator-1",
        conversationId = "thread-1",
        speaker = ConversationSpeaker.CREATOR,
        text = "Hello JAVAS",
        createdAt = Instant.parse("2026-11-24T10:00:00Z")
    )

    @Test fun roundTripsMessageWithoutChangingContent() {
        assertEquals(sample, ConversationMessageCodec.decode(ConversationMessageCodec.encode(sample)))
    }

    @Test fun rejectsBlankOrOversizedFields() {
        assertFalse(ConversationHistoryPolicy.valid(sample.copy(text = "  ")))
        assertFalse(ConversationHistoryPolicy.valid(sample.copy(id = "")))
        assertFalse(
            ConversationHistoryPolicy.valid(
                sample.copy(text = "x".repeat(ConversationHistoryPolicy.MAX_MESSAGE_CHARS + 1))
            )
        )
    }

    @Test fun enforcesOwnerAndBoundedHistoryAccess() {
        assertTrue(ConversationHistoryAccessPolicy.canAccess("creator-1", "creator-1"))
        assertFalse(ConversationHistoryAccessPolicy.canAccess("creator-1", "other-user"))
        assertFalse(ConversationHistoryAccessPolicy.canAccess("", "creator-1"))
        assertTrue(ConversationHistoryAccessPolicy.validReadRequest("thread-1", "creator-1", 100))
        assertFalse(ConversationHistoryAccessPolicy.validReadRequest("thread-1", "creator-1", 201))
        assertFalse(ConversationHistoryAccessPolicy.validReadRequest("", "creator-1", 10))
        assertTrue(ConversationHistoryAccessPolicy.validDeleteRequest("thread-1", "creator-1"))
        assertFalse(ConversationHistoryAccessPolicy.validDeleteRequest("thread-1", ""))
    }

    @Test fun codecRejectsUnsupportedVersionAndTrailingBytes() {
        val encoded = ConversationMessageCodec.encode(sample)
        val badVersion = encoded.copyOf().also { it[3] = 2 }
        var versionRejected = false
        try { ConversationMessageCodec.decode(badVersion) } catch (_: IllegalArgumentException) { versionRejected = true }
        assertTrue(versionRejected)

        var trailingRejected = false
        try { ConversationMessageCodec.decode(encoded + byteArrayOf(0)) } catch (_: IllegalArgumentException) { trailingRejected = true }
        assertTrue(trailingRejected)
    }

    private fun invalidReadResult(
        conversationId: String,
        owner: String,
        limit: Int
    ): ConversationHistoryFailure {
        return if (conversationId.isBlank() || owner.isBlank() || limit !in 1..200) {
            ConversationHistoryFailure.INVALID_MESSAGE
        } else {
            ConversationHistoryFailure.STORAGE_ERROR
        }
    }
}
