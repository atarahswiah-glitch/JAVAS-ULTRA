package com.javasultra.app.core.learning

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/**
 * Read-only HTTPS transport for explicitly allowed public research.
 *
 * Security properties:
 * - HTTPS/443 only, no credentials, no cookies, no authentication headers and GET only.
 * - DNS answers are checked before every new connection; any non-public answer rejects the host.
 * - Redirects are handled manually, capped, and restricted to the original HTTPS origin.
 * - robots.txt is fetched and checked before every page request; disallow rules fail closed.
 * - Response bodies and extracted text are byte-bounded.
 *
 * This is a client-side defence-in-depth adapter, not a replacement for a trusted server-side
 * egress proxy. Android's network stack, OS resolver and upstream network remain in the trust model.
 */
class OkHttpPublicResearchAdapter(
    private val clock: () -> Instant = Instant::now
) : PublicResearchAdapter {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .dns(SafePublicDns)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun retrieve(request: PublicPageRequest): Result<ResearchDocument> =
        withContext(Dispatchers.IO) {
            try {
                if (!request.robotsPolicyChecked) {
                    return@withContext Result.failure(IllegalArgumentException("Robots policy must be checked"))
                }
                if (request.method != ResearchHttpMethod.GET) {
                    return@withContext Result.failure(IllegalArgumentException("Read-only GET required"))
                }
                if (!isAllowedInitialUri(request.uri)) {
                    return@withContext Result.failure(IllegalArgumentException("Unsafe research URI"))
                }

                val original = request.uri
                var current = original
                var redirects = 0
                while (true) {
                    if (!sameOrigin(original, current)) {
                        return@withContext Result.failure(IllegalArgumentException("Cross-origin redirect denied"))
                    }

                    val robots = fetchRobots(current)
                    if (!robots.first) {
                        return@withContext Result.failure(IllegalStateException("Robots policy unavailable or denied"))
                    }
                    if (!RobotsTxtPolicy.isAllowed(robots.second, current.rawPath.ifBlank { "/" } + (current.rawQuery?.let { "?$it" } ?: ""))) {
                        return@withContext Result.failure(IllegalStateException("Target disallowed by robots.txt"))
                    }

                    val response = executeGet(current, MAX_PAGE_BYTES)
                    when {
                        response.code in REDIRECT_CODES -> {
                            val location = response.header("Location")
                            response.close()
                            if (location.isNullOrBlank() || redirects >= MAX_REDIRECTS) {
                                return@withContext Result.failure(IllegalStateException("Invalid or excessive redirect"))
                            }
                            val next = try {
                                current.resolve(location)
                            } catch (_: Exception) {
                                null
                            }
                            if (next == null || !isAllowedInitialUri(next) || !sameOrigin(original, next)) {
                                return@withContext Result.failure(IllegalStateException("Unsafe redirect target"))
                            }
                            current = next
                            redirects++
                        }
                        response.code !in 200..299 -> {
                            val code = response.code
                            response.close()
                            return@withContext Result.failure(IOException("Public research HTTP status $code"))
                        }
                        else -> {
                            val body = response.body
                            val contentType = body?.contentType()?.let {
                                "${it.type}/${it.subtype}".lowercase()
                            }.orEmpty()
                            if (body == null || contentType !in SUPPORTED_CONTENT_TYPES) {
                                response.close()
                                return@withContext Result.failure(IllegalStateException("Unsupported or empty research content"))
                            }
                            val raw = try {
                                readBounded(body.byteStream(), MAX_PAGE_BYTES)
                            } finally {
                                response.close()
                            }
                            if (raw.isEmpty()) {
                                return@withContext Result.failure(IllegalStateException("Empty research response"))
                            }

                            val decoded = String(raw, body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                            val title: String
                            val extracted: String
                            if (contentType == "text/html") {
                                val parsed = Jsoup.parse(decoded, current.toString())
                                parsed.select("script,style,noscript,svg,canvas,nav,footer,header,form,iframe").remove()
                                title = parsed.title().trim().take(MAX_TITLE_CHARS)
                                extracted = parsed.body().text().trim()
                            } else {
                                title = current.host ?: "Public text source"
                                extracted = decoded.trim()
                            }
                            val extractedBytes = extracted.toByteArray(Charsets.UTF_8)
                            if (title.isBlank() || extracted.isBlank() || extractedBytes.size > MAX_EXTRACTED_BYTES) {
                                return@withContext Result.failure(IllegalStateException("Extracted research content is empty or over limit"))
                            }
                            val retrievedAt = clock()
                            if (retrievedAt == Instant.MIN || !retrievedAt.isAfter(Instant.EPOCH)) {
                                return@withContext Result.failure(IllegalStateException("Invalid research clock"))
                            }
                            return@withContext Result.success(
                                ResearchDocument(
                                    canonicalUri = current,
                                    title = title,
                                    retrievedAtEpochMillis = retrievedAt.toEpochMilli(),
                                    contentType = contentType,
                                    extractedText = extracted,
                                    contentDigestSha256 = sha256(raw)
                                )
                            )
                        }
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                Result.failure(IllegalStateException("Research redirect loop"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.failure(failure)
            }
        }

    private suspend fun fetchRobots(uri: URI): Pair<Boolean, String> {
        val robotsUri = URI(uri.scheme, null, uri.host, uri.port, "/robots.txt", null, null)
        val response = executeGet(robotsUri, MAX_ROBOTS_BYTES)
        response.use {
            if (it.code == 404 || it.code == 410) return true to ""
            if (it.code !in 200..299) return false to ""
            val body = it.body ?: return false to ""
            val bytes = readBounded(body.byteStream(), MAX_ROBOTS_BYTES)
            return true to String(bytes, Charsets.UTF_8)
        }
    }

    private suspend fun executeGet(uri: URI, maxBytes: Int): Response {
        val request = Request.Builder()
            .url(uri.toString())
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,text/plain;q=0.9")
            .header("Accept-Encoding", "gzip")
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: okhttp3.Call, failure: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    val declaredLength = response.body?.contentLength() ?: -1L
                    if (declaredLength > maxBytes) {
                        response.close()
                        if (continuation.isActive) {
                            continuation.resumeWithException(IOException("Research response exceeds byte limit"))
                        }
                    } else if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            })
        }
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) throw IOException("Research response exceeds byte limit")
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    private fun isAllowedInitialUri(uri: URI): Boolean =
        uri.isAbsolute &&
            !uri.isOpaque &&
            uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null &&
            uri.port in setOf(-1, 443) &&
            uri.host.none { it == '%' || it == '[' || it == ']' } &&
            !uri.host.matches(Regex("""(?:\d{1,3}\.){3}\d{1,3}""")) &&
            uri.host.contains('.')

    private fun sameOrigin(first: URI, second: URI): Boolean =
        first.scheme.equals(second.scheme, ignoreCase = true) &&
            first.host.equals(second.host, ignoreCase = true) &&
            effectivePort(first) == effectivePort(second)

    private fun effectivePort(uri: URI): Int = if (uri.port == -1) 443 else uri.port

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val USER_AGENT = "JAVAS-ULTRA-Research/1.0 (+public-read-only)"
        const val CONNECT_TIMEOUT_SECONDS = 5L
        const val READ_TIMEOUT_SECONDS = 8L
        const val WRITE_TIMEOUT_SECONDS = 5L
        const val CALL_TIMEOUT_SECONDS = 15L
        const val MAX_REDIRECTS = 3
        const val MAX_PAGE_BYTES = 1_000_000
        const val MAX_ROBOTS_BYTES = 128_000
        const val MAX_EXTRACTED_BYTES = 1_000_000
        const val MAX_TITLE_CHARS = 500
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val SUPPORTED_CONTENT_TYPES = setOf("text/html", "text/plain")
    }
}

/** Rejects a DNS response if any returned address is local, private, reserved or non-global. */
internal object SafePublicDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.isEmpty() || addresses.any { !PublicAddressPolicy.isGlobalUnicast(it) }) {
            throw UnknownHostException("Non-public DNS answer rejected")
        }
        return addresses
    }
}

internal object PublicAddressPolicy {
    fun isGlobalUnicast(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress
        ) return false

        return when (address) {
            is Inet4Address -> isPublicIpv4(address.address)
            is Inet6Address -> isPublicIpv6(address.address)
            else -> false
        }
    }

    private fun isPublicIpv4(bytes: ByteArray): Boolean {
        if (bytes.size != 4) return false
        val a = bytes[0].toInt() and 0xff
        val b = bytes[1].toInt() and 0xff
        val c = bytes[2].toInt() and 0xff
        return when {
            a == 0 || a == 10 || a == 127 || a >= 224 -> false
            a == 100 && b in 64..127 -> false
            a == 169 && b == 254 -> false
            a == 172 && b in 16..31 -> false
            a == 192 && b == 0 -> false
            a == 192 && b == 0 && c == 2 -> false
            a == 192 && b == 88 && c == 99 -> false
            a == 192 && b == 168 -> false
            a == 198 && b in 18..19 -> false
            a == 198 && b == 51 && c == 100 -> false
            a == 203 && b == 0 && c == 113 -> false
            else -> true
        }
    }

    private fun isPublicIpv6(bytes: ByteArray): Boolean {
        if (bytes.size != 16) return false
        // Only global-unicast 2000::/3 is considered. Exclude documentation, transition,
        // Teredo, and well-known NAT64 prefixes because they can encode non-public endpoints.
        val first = bytes[0].toInt() and 0xff
        if ((first and 0xe0) != 0x20) return false
        val second = bytes[1].toInt() and 0xff
        val third = bytes[2].toInt() and 0xff
        val fourth = bytes[3].toInt() and 0xff
        if (first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8) return false // 2001:db8::/32
        if (first == 0x20 && second == 0x01 && third == 0x00 && fourth == 0x00) return false // Teredo
        if (first == 0x20 && second == 0x02) return false // 6to4
        if (first == 0x00 && second == 0x64 && third == 0xff && fourth == 0x9b) return false // NAT64
        return true
    }
}

internal object RobotsTxtPolicy {
    private data class Group(val agents: Set<String>, val disallowed: List<String>)

    /** Conservative parser: any matching Disallow in a matching group denies the path. */
    fun isAllowed(robotsText: String, pathAndQuery: String): Boolean {
        if (robotsText.length > 128_000) return false
        val groups = mutableListOf<Group>()
        var agents = linkedSetOf<String>()
        var rules = mutableListOf<String>()
        var hasRules = false

        fun flush() {
            if (agents.isNotEmpty()) groups += Group(agents.toSet(), rules.toList())
            agents = linkedSetOf()
            rules = mutableListOf()
            hasRules = false
        }

        robotsText.lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            val separator = line.indexOf(':')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            when (key) {
                "user-agent" -> {
                    if (hasRules) flush()
                    if (value.isNotBlank()) agents += value.lowercase()
                }
                "disallow" -> if (agents.isNotEmpty()) {
                    hasRules = true
                    if (value.isNotEmpty()) rules += value
                }
                "allow" -> if (agents.isNotEmpty()) hasRules = true
            }
        }
        flush()

        val applicable = groups.filter { group ->
            group.agents.any { it == "*" || USER_AGENT_TOKEN.startsWith(it) }
        }
        return applicable.none { group ->
            group.disallowed.any { rule -> matchesRule(pathAndQuery, rule) }
        }
    }

    private fun matchesRule(path: String, rule: String): Boolean {
        if (rule.isEmpty()) return false
        val endAnchored = rule.endsWith("$")
        val pattern = if (endAnchored) rule.dropLast(1) else rule
        val regex = "^" + pattern.split("*").joinToString(".*") { Regex.escape(it) } +
            if (endAnchored) "$" else ".*"
        return Regex(regex).matches(path)
    }

    private const val USER_AGENT_TOKEN = "javas-ultra"
}
