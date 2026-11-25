package com.javasultra.app.core.ai

import com.javasultra.app.ConversationEngine
import com.javasultra.app.ConversationPromptMessage
import com.javasultra.app.StreamingConversationEngine
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Supplies the current Supabase Auth access token; never persist or hard-code a token here. */
fun interface JavasAccessTokenProvider {
    fun currentAccessToken(): String?
}

/**
 * Calls the authenticated JAVAS Supabase Edge Function and consumes Anthropic's SSE stream.
 * The conversation UI invokes this engine on a background executor.
 */
class JavasGatewayConversationEngine(
    private val functionUrl: String,
    private val publishableKey: String,
    private val accessTokenProvider: JavasAccessTokenProvider,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 120_000
) : StreamingConversationEngine {

    override fun generateReply(messages: List<ConversationPromptMessage>): String? =
        generateReply(messages) {}

    override fun generateReply(
        messages: List<ConversationPromptMessage>,
        onTextDelta: (String) -> Unit
    ): String? {
        require(functionUrl.startsWith("https://")) { "Gateway URL must use HTTPS" }
        require(publishableKey.isNotBlank()) { "Supabase publishable key is not configured" }
        val accessToken = accessTokenProvider.currentAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Sign in to JAVAS before starting a conversation")

        val requestBody = GatewayRequestCodec.encode(messages)
        val connection = (URL(functionUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Cache-Control", "no-cache")
        }

        try {
            connection.outputStream.use { output ->
                output.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                connection.errorStream?.use { it.readBytes() }
                throw IllegalStateException("JAVAS gateway request failed (HTTP $status)")
            }

            return readEventStream(connection.inputStream, onTextDelta)
        } finally {
            connection.disconnect()
        }
    }

    private fun readEventStream(stream: InputStream, onTextDelta: (String) -> Unit): String {
        val answer = StringBuilder()
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.substringAfter("data:").trimStart()
                if (data == "[DONE]") break
                if (AnthropicSseParser.isError(data)) {
                    throw IllegalStateException("The AI provider returned an error")
                }
                val delta = AnthropicSseParser.textDelta(data) ?: continue
                answer.append(delta)
                onTextDelta(delta)
            }
        }
        return answer.toString().takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("The AI provider returned no response")
    }
}

object GatewayRequestCodec {
    const val MAX_MESSAGES = 40
    const val MAX_MESSAGE_CHARS = 12_000

    fun encode(message: String): String =
        encode(listOf(ConversationPromptMessage("user", message)))

    fun encode(messages: List<ConversationPromptMessage>): String {
        require(messages.isNotEmpty() && messages.size <= MAX_MESSAGES)
        require(messages.all {
            it.role in setOf("user", "assistant") &&
                it.content.isNotBlank() && it.content.length <= MAX_MESSAGE_CHARS
        })
        require(messages.last().role == "user")
        return buildString {
            append("{\"messages\":[")
            messages.forEachIndexed { index, message ->
                if (index > 0) append(',')
                append("{\"role\":")
                append(jsonString(message.role))
                append(",\"content\":")
                append(jsonString(message.content))
                append('}')
            }
            append("],\"stream\":true}")
        }
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else append(char)
            }
        }
        append('"')
    }
}

/** Small, dependency-free parser for Anthropic text_delta SSE event data. */
object AnthropicSseParser {
    private val textDeltaPattern = Regex(
        "\"type\"\\s*:\\s*\"content_block_delta\".*?\"delta\"\\s*:\\s*\\{.*?" +
            "\"type\"\\s*:\\s*\"text_delta\".*?\"text\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\")"
    )
    private val errorPattern = Regex("\"type\"\\s*:\\s*\"error\"")

    fun textDelta(data: String): String? {
        val literal = textDeltaPattern.find(data)?.groupValues?.getOrNull(1) ?: return null
        return decodeJsonString(literal)
    }

    fun isError(data: String): Boolean = errorPattern.containsMatchIn(data)

    private fun decodeJsonString(literal: String): String {
        require(literal.length >= 2 && literal.first() == '"' && literal.last() == '"')
        val result = StringBuilder()
        var index = 1
        while (index < literal.lastIndex) {
            val current = literal[index++]
            if (current != '\\') {
                result.append(current)
                continue
            }
            require(index < literal.lastIndex)
            when (val escaped = literal[index++]) {
                '"' -> result.append('"')
                '\\' -> result.append('\\')
                '/' -> result.append('/')
                'b' -> result.append('\b')
                'f' -> result.append('\u000C')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                'u' -> {
                    require(index + 4 <= literal.lastIndex)
                    result.append(literal.substring(index, index + 4).toInt(16).toChar())
                    index += 4
                }
                else -> error("Invalid JSON escape: $escaped")
            }
        }
        return result.toString()
    }
}
