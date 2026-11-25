package com.javasultra.app.core.ai

import com.javasultra.app.ConversationEngine
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
 *
 * Call generateReply from a background executor. The conversation UI does this so network
 * operations never run on the Android main thread.
 */
class JavasGatewayConversationEngine(
    private val functionUrl: String,
    private val publishableKey: String,
    private val accessTokenProvider: JavasAccessTokenProvider,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 120_000
) : ConversationEngine {

    override fun generateReply(userMessage: String): String? {
        val message = userMessage.trim()
        require(message.isNotEmpty() && message.length <= MAX_MESSAGE_CHARS) {
            "Message is empty or too long"
        }
        require(functionUrl.startsWith("https://")) { "Gateway URL must use HTTPS" }
        require(publishableKey.isNotBlank()) { "Supabase publishable key is not configured" }

        val accessToken = accessTokenProvider.currentAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Sign in to JAVAS before starting a conversation")

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
            val requestBody = GatewayRequestCodec.encode(message)
            connection.outputStream.use { output ->
                output.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                connection.errorStream?.use { it.readBytes() }
                throw IllegalStateException("JAVAS gateway request failed (HTTP $status)")
            }

            val stream = connection.inputStream
            return readEventStream(stream)
        } finally {
            connection.disconnect()
        }
    }

    private fun readEventStream(stream: InputStream): String {
        val answer = StringBuilder()
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.substringAfter("data:").trimStart()
                if (data == "[DONE]") break
                val delta = AnthropicSseParser.textDelta(data)
                if (delta != null) answer.append(delta)
                if (AnthropicSseParser.isError(data)) {
                    throw IllegalStateException("The AI provider returned an error")
                }
            }
        }
        return answer.toString().takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("The AI provider returned no response")
    }

    companion object {
        const val MAX_MESSAGE_CHARS = 12_000
    }
}

object GatewayRequestCodec {
    fun encode(message: String): String =
        """{"messages":[{"role":"user","content":${jsonString(message)}}],"stream":true}"""

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
