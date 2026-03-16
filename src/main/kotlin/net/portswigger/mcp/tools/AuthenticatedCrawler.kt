package net.portswigger.mcp.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.HttpService
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.HttpRequestResponse as MontoyaHttpRequestResponse
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

data class CrawlTarget(val url: String, val depth: Int)

class AuthenticatedCrawler(
    private val api: MontoyaApi,
    private val seedUrls: List<String>,
    private val cookies: String,
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val maxDepth: Int = 10,
    private val maxPages: Int = 500,
    private val delayMs: Long = 100,
    extraPathPatterns: List<String> = emptyList()
) {
    private val queue = ConcurrentLinkedQueue<CrawlTarget>()
    private val visited: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val discoveredUrls: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val pagesVisited = AtomicInteger(0)
    val currentDepth = AtomicInteger(0)
    val status = AtomicReference("pending")
    val cancelled = AtomicBoolean(false)
    val startTimeMs = AtomicReference(0L)
    val errors = AtomicInteger(0)

    private var crawlThread: Thread? = null

    private val linkPatterns = listOf(
        Pattern.compile("""href\s*=\s*["']([^"'#][^"']*)["']""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""src\s*=\s*["']([^"'#][^"']*)["']""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""action\s*=\s*["']([^"'#][^"']*)["']""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""data-url\s*=\s*["']([^"'#][^"']*)["']""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""data-href\s*=\s*["']([^"'#][^"']*)["']""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""@click\s*=\s*["'][^"']*navigate\s*\(\s*['"]([^'"]+)['"]""", Pattern.CASE_INSENSITIVE)
    )

    private val jsUrlPatterns = buildList {
        // Generic API path prefixes commonly used across web apps
        add(Pattern.compile("""["'](/api/[^"'\s?#]+)["']"""))
        add(Pattern.compile("""["'](/v[0-9]+/[^"'\s?#]+)["']"""))
        add(Pattern.compile("""["'](/graphql[^"'\s?#]*)["']"""))
        add(Pattern.compile("""["'](/rest/[^"'\s?#]+)["']"""))

        // JS fetch/XHR/axios calls
        add(Pattern.compile("""fetch\s*\(\s*["']([^"']+)["']""", Pattern.CASE_INSENSITIVE))
        add(Pattern.compile("""axios\s*\.\s*(?:get|post|put|patch|delete|request|head|options)\s*\(\s*["']([^"']+)["']""", Pattern.CASE_INSENSITIVE))
        add(Pattern.compile("""\.open\s*\(\s*["'][A-Z]+["']\s*,\s*["']([^"']+)["']""", Pattern.CASE_INSENSITIVE))

        // jQuery AJAX
        add(Pattern.compile("""\$\s*\.\s*(?:ajax|get|post|getJSON)\s*\(\s*["']([^"']+)["']""", Pattern.CASE_INSENSITIVE))

        // JS location / navigation
        add(Pattern.compile("""(?:window\.)?location\s*(?:\.href)?\s*=\s*["']([^"']+)["']""", Pattern.CASE_INSENSITIVE))
        add(Pattern.compile("""(?:window\.)?location\.replace\s*\(\s*["']([^"']+)["']""", Pattern.CASE_INSENSITIVE))

        // SPA router paths (React Router, Vue Router, Angular)
        add(Pattern.compile("""path\s*:\s*["'](/[^"']+)["']"""))
        add(Pattern.compile("""(?:to|redirect|navigate)\s*=\s*["'](/[^"']+)["']""", Pattern.CASE_INSENSITIVE))

        // Generic quoted absolute paths in JS (catches most remaining endpoints)
        add(Pattern.compile("""["']((?:https?://[^"'\s]+)|(?:/[a-zA-Z0-9_][^"'\s]*))["']"""))

        // User-supplied custom patterns
        for (raw in extraPathPatterns) {
            try {
                add(Pattern.compile(raw, Pattern.CASE_INSENSITIVE))
            } catch (_: Exception) {
                api.logging().logToError("Invalid extra crawl pattern (skipped): $raw")
            }
        }
    }

    private val skipExtensions = setOf(
        ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".webp",
        ".css", ".woff", ".woff2", ".ttf", ".eot",
        ".mp4", ".mp3", ".avi", ".mov",
        ".pdf", ".zip", ".gz", ".tar",
        ".map"
    )

    fun pagesQueued(): Int = queue.size
    fun urlsDiscovered(): Int = discoveredUrls.size
    fun elapsedMs(): Long {
        val start = startTimeMs.get()
        return if (start > 0) System.currentTimeMillis() - start else 0
    }

    fun start() {
        seedUrls.forEach { url ->
            val normalized = normalizeUrl(url, url)
            if (normalized != null) {
                queue.add(CrawlTarget(normalized, 0))
                discoveredUrls.add(normalized)
            }
        }

        status.set("running")
        startTimeMs.set(System.currentTimeMillis())

        crawlThread = Thread({
            try {
                crawlLoop()
            } catch (e: Exception) {
                api.logging().logToError("Crawler error: ${e.message}")
                status.set("error: ${e.message}")
            } finally {
                if (status.get() == "running") {
                    status.set("finished")
                }
                api.logging().logToOutput(
                    "Crawler finished: ${pagesVisited.get()} pages, " +
                    "${discoveredUrls.size} URLs discovered, ${errors.get()} errors"
                )
            }
        }, "mcp-authenticated-crawler")
        crawlThread!!.isDaemon = true
        crawlThread!!.start()
    }

    fun cancel() {
        cancelled.set(true)
        status.set("cancelled")
    }

    private fun crawlLoop() {
        while (!cancelled.get()) {
            val target = queue.poll() ?: break

            if (pagesVisited.get() >= maxPages) {
                api.logging().logToOutput("Crawler reached max pages limit: $maxPages")
                break
            }

            if (target.depth > maxDepth) continue

            val urlKey = stripQueryAndFragment(target.url)
            if (!visited.add(urlKey)) continue

            currentDepth.set(target.depth)

            try {
                val response = fetchUrl(target.url)
                if (response != null) {
                    pagesVisited.incrementAndGet()
                    api.siteMap().add(response)

                    val responseStr = response.response()?.toString() ?: ""
                    val contentType = response.response()?.statedMimeType()?.name ?: ""

                    if (shouldParseForLinks(contentType, responseStr)) {
                        val links = extractLinks(responseStr, target.url)
                        for (link in links) {
                            if (cancelled.get()) break
                            if (!visited.contains(stripQueryAndFragment(link))) {
                                discoveredUrls.add(link)
                                queue.add(CrawlTarget(link, target.depth + 1))
                            }
                        }
                    }

                    updateCookiesFromResponse(response)

                    if (pagesVisited.get() % 25 == 0) {
                        api.logging().logToOutput(
                            "Crawler progress: ${pagesVisited.get()} pages, " +
                            "${queue.size} queued, depth ${target.depth}"
                        )
                    }
                }
            } catch (e: Exception) {
                errors.incrementAndGet()
                api.logging().logToError("Crawler error on ${target.url}: ${e.message}")
            }

            if (delayMs > 0 && !cancelled.get()) {
                Thread.sleep(delayMs)
            }
        }
    }

    private fun fetchUrl(url: String): MontoyaHttpRequestResponse? {
        try {
            val uri = URI(url)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else if (scheme == "https") 443 else 80
            val usesHttps = scheme == "https"
            val path = if (uri.rawPath.isNullOrBlank()) "/" else uri.rawPath +
                (if (!uri.rawQuery.isNullOrBlank()) "?${uri.rawQuery}" else "")

            val service = HttpService.httpService(host, port, usesHttps)

            val requestBuilder = StringBuilder()
            requestBuilder.append("GET $path HTTP/1.1\r\n")
            requestBuilder.append("Host: $host\r\n")
            requestBuilder.append("Cookie: $cookies\r\n")
            requestBuilder.append("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36\r\n")
            requestBuilder.append("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,application/json,*/*;q=0.8\r\n")
            requestBuilder.append("Accept-Language: en-US,en;q=0.9\r\n")
            extraHeaders.forEach { (key, value) ->
                requestBuilder.append("$key: $value\r\n")
            }
            requestBuilder.append("\r\n")

            val request = HttpRequest.httpRequest(service, requestBuilder.toString())
            return api.http().sendRequest(request)
        } catch (e: Exception) {
            api.logging().logToError("Fetch error for $url: ${e.message}")
            return null
        }
    }

    private fun extractLinks(body: String, baseUrl: String): Set<String> {
        val links = mutableSetOf<String>()

        for (pattern in linkPatterns) {
            val matcher = pattern.matcher(body)
            while (matcher.find()) {
                val raw = matcher.group(1) ?: continue
                val resolved = normalizeUrl(raw, baseUrl)
                if (resolved != null && isInScope(resolved) && !shouldSkip(resolved)) {
                    links.add(resolved)
                }
            }
        }

        for (pattern in jsUrlPatterns) {
            val matcher = pattern.matcher(body)
            while (matcher.find()) {
                val raw = matcher.group(1) ?: continue
                val resolved = normalizeUrl(raw, baseUrl)
                if (resolved != null && isInScope(resolved) && !shouldSkip(resolved)) {
                    links.add(resolved)
                }
            }
        }

        return links
    }

    private fun normalizeUrl(raw: String, baseUrl: String): String? {
        try {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("javascript:") ||
                trimmed.startsWith("mailto:") || trimmed.startsWith("data:") ||
                trimmed.startsWith("tel:") || trimmed.startsWith("#")) {
                return null
            }

            val base = URI(baseUrl)
            val resolved = base.resolve(trimmed)

            val normalized = URI(
                resolved.scheme,
                resolved.authority,
                resolved.path?.replace("//+".toRegex(), "/"),
                resolved.query,
                null
            )

            return normalized.toString()
        } catch (e: Exception) {
            return null
        }
    }

    private fun stripQueryAndFragment(url: String): String {
        return url.substringBefore("?").substringBefore("#")
    }

    private fun isInScope(url: String): Boolean {
        return try {
            api.scope().isInScope(url)
        } catch (e: Exception) {
            false
        }
    }

    private fun shouldSkip(url: String): Boolean {
        val lower = url.lowercase()
        return skipExtensions.any { lower.substringBefore("?").endsWith(it) }
    }

    private fun shouldParseForLinks(contentType: String, body: String): Boolean {
        if (body.isEmpty()) return false
        val ct = contentType.lowercase()
        if (ct.contains("image") || ct.contains("font") || ct.contains("audio") || ct.contains("video")) {
            return false
        }
        return true
    }

    private fun updateCookiesFromResponse(response: MontoyaHttpRequestResponse) {
        // Cookie updates from Set-Cookie are handled by Burp's cookie jar automatically
        // when requests go through api.http().sendRequest()
    }
}
