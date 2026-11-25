package com.javasultra.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayRequestCodecTest {
    @Test fun encodesQuotesBackslashesAndNewlinesAsJson() {
        assertEquals(
            """{"messages":[{"role":"user","content":"Say \"hello\"\\next\nline"}],"stream":true}""",
            GatewayRequestCodec.encode("Say \"hello\"\\next\nline")
        )
    }

    @Test fun extractsAnthropicTextDelta() {
        val event = """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello JAVAS"}}"""
        assertEquals("Hello JAVAS", AnthropicSseParser.textDelta(event))
    }

    @Test fun decodesJsonEscapesInsideTextDelta() {
        val event = """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Line 1\nLine 2 \"JAVAS\""}}"""
        assertEquals("Line 1\nLine 2 \"JAVAS\"", AnthropicSseParser.textDelta(event))
    }

    @Test fun ignoresNonTextEvents() {
        assertEquals(null, AnthropicSseParser.textDelta("""{"type":"message_start"}"""))
        assertFalse(AnthropicSseParser.isError("""{"type":"message_stop"}"""))
    }

    @Test fun identifiesProviderErrorEvents() {
        assertTrue(AnthropicSseParser.isError("""{"type":"error","error":{"type":"overloaded_error"}}"""))
    }
}
