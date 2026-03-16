package net.portswigger.mcp.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.burpsuite.TaskExecutionEngine.TaskExecutionEngineState.PAUSED
import burp.api.montoya.burpsuite.TaskExecutionEngine.TaskExecutionEngineState.RUNNING
import burp.api.montoya.collaborator.InteractionFilter
import burp.api.montoya.core.BurpSuiteEdition
import burp.api.montoya.http.HttpMode
import burp.api.montoya.http.HttpService
import burp.api.montoya.http.message.HttpHeader
import burp.api.montoya.http.message.HttpRequestResponse as MontoyaHttpRequestResponse
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.scanner.AuditConfiguration
import burp.api.montoya.scanner.BuiltInAuditConfiguration
import burp.api.montoya.scanner.CrawlConfiguration
import burp.api.montoya.scanner.ReportFormat
import burp.api.montoya.scanner.audit.Audit
import burp.api.montoya.sitemap.SiteMapFilter
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.portswigger.mcp.config.McpConfig
import net.portswigger.mcp.schema.*
import net.portswigger.mcp.security.HistoryAccessSecurity
import net.portswigger.mcp.security.HistoryAccessType
import net.portswigger.mcp.security.HttpRequestSecurity
import net.portswigger.mcp.security.ScannerSecurity
import java.awt.KeyboardFocusManager
import java.nio.file.Paths
import java.time.ZonedDateTime
import java.util.regex.Pattern
import javax.swing.JTextArea

private suspend fun checkHistoryPermissionOrDeny(
    accessType: HistoryAccessType, config: McpConfig, api: MontoyaApi, logMessage: String
): Boolean {
    val allowed = HistoryAccessSecurity.checkHistoryAccessPermission(accessType, config)
    if (!allowed) {
        api.logging().logToOutput("MCP $logMessage access denied")
        return false
    }
    api.logging().logToOutput("MCP $logMessage access granted")
    return true
}

private const val MAX_RESPONSE_LENGTH = 50_000

private fun truncateIfNeeded(serialized: String, maxLength: Int = MAX_RESPONSE_LENGTH): String {
    return if (serialized.length > maxLength) {
        serialized.substring(0, maxLength) + "\n... (truncated from ${serialized.length} chars)"
    } else {
        serialized
    }
}

fun Server.registerTools(api: MontoyaApi, config: McpConfig) {

    mcpTool<SendHttp1Request>("Issues an HTTP/1.1 request and returns the response.") {
        val allowed = runBlocking {
            HttpRequestSecurity.checkHttpRequestPermission(targetHostname, targetPort, config, content, api)
        }
        if (!allowed) {
            api.logging().logToOutput("MCP HTTP request denied: $targetHostname:$targetPort")
            return@mcpTool "Send HTTP request denied by Burp Suite"
        }

        api.logging().logToOutput("MCP HTTP/1.1 request: $targetHostname:$targetPort")

        val fixedContent = content.replace("\r", "").replace("\n", "\r\n")

        val request = HttpRequest.httpRequest(toMontoyaService(), fixedContent)
        val response = api.http().sendRequest(request)

        response?.toString() ?: "<no response>"
    }

    mcpTool<SendHttp2Request>("Issues an HTTP/2 request and returns the response. Do NOT pass headers to the body parameter.") {
        val http2RequestDisplay = buildString {
            pseudoHeaders.forEach { (key, value) ->
                val headerName = if (key.startsWith(":")) key else ":$key"
                appendLine("$headerName: $value")
            }
            headers.forEach { (key, value) ->
                appendLine("$key: $value")
            }
            if (requestBody.isNotBlank()) {
                appendLine()
                append(requestBody)
            }
        }

        val allowed = runBlocking {
            HttpRequestSecurity.checkHttpRequestPermission(targetHostname, targetPort, config, http2RequestDisplay, api)
        }
        if (!allowed) {
            api.logging().logToOutput("MCP HTTP request denied: $targetHostname:$targetPort")
            return@mcpTool "Send HTTP request denied by Burp Suite"
        }

        api.logging().logToOutput("MCP HTTP/2 request: $targetHostname:$targetPort")

        val orderedPseudoHeaderNames = listOf(":scheme", ":method", ":path", ":authority")

        val fixedPseudoHeaders = LinkedHashMap<String, String>().apply {
            orderedPseudoHeaderNames.forEach { name ->
                val value = pseudoHeaders[name.removePrefix(":")] ?: pseudoHeaders[name]
                if (value != null) {
                    put(name, value)
                }
            }

            pseudoHeaders.forEach { (key, value) ->
                val properKey = if (key.startsWith(":")) key else ":$key"
                if (!containsKey(properKey)) {
                    put(properKey, value)
                }
            }
        }

        val headerList = (fixedPseudoHeaders + headers).map { HttpHeader.httpHeader(it.key.lowercase(), it.value) }

        val request = HttpRequest.http2Request(toMontoyaService(), headerList, requestBody)
        val response = api.http().sendRequest(request, HttpMode.HTTP_2)

        response?.toString() ?: "<no response>"
    }

    mcpTool<CreateRepeaterTab>("Creates a new Repeater tab with the specified HTTP request and optional tab name. Make sure to use carriage returns appropriately.") {
        val request = HttpRequest.httpRequest(toMontoyaService(), content)
        api.repeater().sendToRepeater(request, tabName)
    }

    mcpTool<SendToIntruder>("Sends an HTTP request to Intruder with the specified HTTP request and optional tab name. Make sure to use carriage returns appropriately.") {
        val request = HttpRequest.httpRequest(toMontoyaService(), content)
        api.intruder().sendToIntruder(request, tabName)
    }

    mcpTool<UrlEncode>("URL encodes the input string") {
        api.utilities().urlUtils().encode(content)
    }

    mcpTool<UrlDecode>("URL decodes the input string") {
        api.utilities().urlUtils().decode(content)
    }

    mcpTool<Base64Encode>("Base64 encodes the input string") {
        api.utilities().base64Utils().encodeToString(content)
    }

    mcpTool<Base64Decode>("Base64 decodes the input string") {
        api.utilities().base64Utils().decode(content).toString()
    }

    mcpTool<GenerateRandomString>("Generates a random string of specified length and character set") {
        api.utilities().randomUtils().randomString(length, characterSet)
    }

    mcpTool(
        "output_project_options",
        "Outputs current project-level configuration in JSON format. You can use this to determine the schema for available config options."
    ) {
        api.burpSuite().exportProjectOptionsAsJson()
    }

    mcpTool(
        "output_user_options",
        "Outputs current user-level configuration in JSON format. You can use this to determine the schema for available config options."
    ) {
        api.burpSuite().exportUserOptionsAsJson()
    }

    val toolingDisabledMessage =
        "User has disabled configuration editing. They can enable it in the MCP tab in Burp by selecting 'Enable tools that can edit your config'"

    mcpTool<SetProjectOptions>("Sets project-level configuration in JSON format. This will be merged with existing configuration. Make sure to export before doing this, so you know what the schema is. Make sure the JSON has a top level 'project_options' object!") {
        if (config.configEditingTooling) {
            api.logging().logToOutput("Setting project-level configuration: $json")
            api.burpSuite().importProjectOptionsFromJson(json)

            "Project configuration has been applied"
        } else {
            toolingDisabledMessage
        }
    }


    mcpTool<SetUserOptions>("Sets user-level configuration in JSON format. This will be merged with existing configuration. Make sure to export before doing this, so you know what the schema is. Make sure the JSON has a top level 'user_options' object!") {
        if (config.configEditingTooling) {
            api.logging().logToOutput("Setting user-level configuration: $json")
            api.burpSuite().importUserOptionsFromJson(json)

            "User configuration has been applied"
        } else {
            toolingDisabledMessage
        }
    }

    if (api.burpSuite().version().edition() == BurpSuiteEdition.PROFESSIONAL) {
        mcpPaginatedTool<GetScannerIssues>("Displays information about issues identified by the scanner") {
            api.siteMap().issues().asSequence().map { Json.encodeToString(it.toSerializableForm()) }
        }

        val collaboratorClient by lazy { api.collaborator().createClient() }

        mcpTool<GenerateCollaboratorPayload>(
            "Generates a Burp Collaborator payload URL for out-of-band (OOB) testing. " +
            "Inject this payload into requests to detect server-side interactions (DNS lookups, HTTP requests, SMTP). " +
            "Use get_collaborator_interactions with the returned payloadId to check for interactions."
        ) {
            api.logging().logToOutput("MCP generating Collaborator payload${customData?.let { " with custom data" } ?: ""}")

            val payload = if (customData != null) {
                collaboratorClient.generatePayload(customData)
            } else {
                collaboratorClient.generatePayload()
            }

            val server = collaboratorClient.server()
            "Payload: $payload\nPayload ID: ${payload.id()}\nCollaborator server: ${server.address()}"
        }

        mcpTool<GetCollaboratorInteractions>(
            "Polls Burp Collaborator for out-of-band interactions (DNS, HTTP, SMTP). " +
            "Optionally filter by payloadId from generate_collaborator_payload. " +
            "Returns interaction details including type, timestamp, client IP, and protocol-specific data."
        ) {
            api.logging().logToOutput("MCP polling Collaborator interactions${payloadId?.let { " for payload: $it" } ?: ""}")

            val interactions = if (payloadId != null) {
                collaboratorClient.getInteractions(InteractionFilter.interactionIdFilter(payloadId))
            } else {
                collaboratorClient.getAllInteractions()
            }

            if (interactions.isEmpty()) {
                "No interactions detected"
            } else {
                interactions.joinToString("\n\n") {
                    Json.encodeToString(it.toSerializableForm())
                }
            }
        }

        mcpPaginatedTool<GetSiteMapUrls>(
            "Returns deduplicated URLs from the site map. " +
            "Use urlPrefix to filter by URL prefix (e.g. 'https://example.com/api/'). " +
            "By default, strips query parameters before deduplicating so /path?a=1 and /path?b=2 " +
            "collapse into /path. Set includeQueryString=true to keep full URLs with parameters."
        ) {
            val allowed = runBlocking {
                checkHistoryPermissionOrDeny(HistoryAccessType.SITE_MAP, config, api, "site map")
            }
            if (!allowed) {
                return@mcpPaginatedTool sequenceOf("Site map access denied by Burp Suite")
            }

            val items = if (urlPrefix.isNullOrBlank()) {
                api.siteMap().requestResponses()
            } else {
                api.siteMap().requestResponses(SiteMapFilter.prefixFilter(urlPrefix))
            }

            items.asSequence()
                .mapNotNull { it.request()?.url() }
                .map { url ->
                    if (includeQueryString == true) url
                    else url.substringBefore("?")
                }
                .distinct()
        }

        mcpPaginatedTool<GetSiteMapEntries>(
            "Returns site map entries with method, URL, status code, MIME type, and parameters. " +
            "Does NOT include raw request/response bodies. " +
            "Use urlPrefix to filter by URL prefix. Deduplicated by method+URL."
        ) {
            val allowed = runBlocking {
                checkHistoryPermissionOrDeny(HistoryAccessType.SITE_MAP, config, api, "site map")
            }
            if (!allowed) {
                return@mcpPaginatedTool sequenceOf("Site map access denied by Burp Suite")
            }

            val items = if (urlPrefix.isNullOrBlank()) {
                api.siteMap().requestResponses()
            } else {
                api.siteMap().requestResponses(SiteMapFilter.prefixFilter(urlPrefix))
            }

            val seen = mutableSetOf<String>()
            items.asSequence()
                .filter { entry ->
                    val key = "${entry.request()?.method() ?: "UNKNOWN"} ${entry.request()?.url() ?: ""}"
                    seen.add(key)
                }
                .map { Json.encodeToString(it.toSiteMapEntry()) }
        }

        mcpPaginatedTool<GetSiteMapIssueSummary>(
            "Returns compact scanner issue summaries (name, severity, confidence, URL, type). " +
            "Does NOT include request/response bodies. " +
            "Use urlPrefix to filter by URL prefix."
        ) {
            val allowed = runBlocking {
                checkHistoryPermissionOrDeny(HistoryAccessType.SITE_MAP, config, api, "site map")
            }
            if (!allowed) {
                return@mcpPaginatedTool sequenceOf("Site map issue access denied by Burp Suite")
            }

            val issues = if (urlPrefix.isNullOrBlank()) {
                api.siteMap().issues()
            } else {
                api.siteMap().issues(SiteMapFilter.prefixFilter(urlPrefix))
            }

            issues.asSequence().map { Json.encodeToString(it.toIssueSummary()) }
        }

        // --- Scanner Control Tools ---

        mcpTool<StartCrawl>(
            "Starts a crawl in the Burp Scanner with the specified seed URLs. " +
            "Returns a task ID that can be used with get_scan_status and delete_scan_task."
        ) {
            val allowed = runBlocking {
                ScannerSecurity.checkScanPermission("Start crawl on: ${seedUrls.joinToString(", ")}", config, api)
            }
            if (!allowed) {
                api.logging().logToOutput("MCP crawl denied")
                return@mcpTool "Scanner operation denied by Burp Suite"
            }

            api.logging().logToOutput("MCP starting crawl: ${seedUrls.joinToString(", ")}")
            val crawlConfig = CrawlConfiguration.crawlConfiguration(*seedUrls.toTypedArray())
            val crawl = api.scanner().startCrawl(crawlConfig)
            val taskId = ScanTaskRegistry.register(crawl)

            Json.encodeToString(ScanTaskStatus(
                taskId = taskId,
                statusMessage = crawl.statusMessage(),
                requestCount = crawl.requestCount(),
                errorCount = crawl.errorCount()
            ))
        }

        mcpTool<StartAudit>(
            "Starts an audit (scan) in the Burp Scanner. " +
            "Set configurationType to 'active' for LEGACY_ACTIVE_AUDIT_CHECKS or 'passive' for LEGACY_PASSIVE_AUDIT_CHECKS. " +
            "Returns a task ID for tracking."
        ) {
            val allowed = runBlocking {
                ScannerSecurity.checkScanPermission("Start $configurationType audit", config, api)
            }
            if (!allowed) {
                api.logging().logToOutput("MCP audit denied")
                return@mcpTool "Scanner operation denied by Burp Suite"
            }

            val builtIn = when (configurationType.lowercase()) {
                "active" -> BuiltInAuditConfiguration.LEGACY_ACTIVE_AUDIT_CHECKS
                "passive" -> BuiltInAuditConfiguration.LEGACY_PASSIVE_AUDIT_CHECKS
                else -> return@mcpTool "Invalid configurationType: use 'active' or 'passive'"
            }

            api.logging().logToOutput("MCP starting $configurationType audit")
            val auditConfig = AuditConfiguration.auditConfiguration(builtIn)
            val audit = api.scanner().startAudit(auditConfig)
            val taskId = ScanTaskRegistry.register(audit)

            Json.encodeToString(ScanTaskStatus(
                taskId = taskId,
                statusMessage = audit.statusMessage(),
                requestCount = audit.requestCount(),
                errorCount = audit.errorCount()
            ))
        }

        mcpTool<StartAuditOnRequests>(
            "Starts an audit and adds specific HTTP requests to it. " +
            "Provide raw HTTP request strings and target connection details. " +
            "Set configurationType to 'active' or 'passive'."
        ) {
            val allowed = runBlocking {
                ScannerSecurity.checkScanPermission(
                    "Start $configurationType audit on ${requests.size} request(s) to $targetHostname:$targetPort",
                    config, api
                )
            }
            if (!allowed) {
                api.logging().logToOutput("MCP audit-on-requests denied")
                return@mcpTool "Scanner operation denied by Burp Suite"
            }

            val builtIn = when (configurationType.lowercase()) {
                "active" -> BuiltInAuditConfiguration.LEGACY_ACTIVE_AUDIT_CHECKS
                "passive" -> BuiltInAuditConfiguration.LEGACY_PASSIVE_AUDIT_CHECKS
                else -> return@mcpTool "Invalid configurationType: use 'active' or 'passive'"
            }

            api.logging().logToOutput("MCP starting $configurationType audit on ${requests.size} request(s)")
            val auditConfig = AuditConfiguration.auditConfiguration(builtIn)
            val audit = api.scanner().startAudit(auditConfig)

            val service = HttpService.httpService(targetHostname, targetPort, usesHttps)
            requests.forEach { rawRequest ->
                val fixedContent = rawRequest.replace("\r", "").replace("\n", "\r\n")
                val request = HttpRequest.httpRequest(service, fixedContent)
                audit.addRequest(request)
            }

            val taskId = ScanTaskRegistry.register(audit)
            Json.encodeToString(ScanTaskStatus(
                taskId = taskId,
                statusMessage = audit.statusMessage(),
                requestCount = audit.requestCount(),
                errorCount = audit.errorCount()
            ))
        }

        mcpTool<GetScanStatus>(
            "Gets the current status of a running scan task (crawl or audit). " +
            "Use the taskId returned by start_crawl or start_audit. " +
            "Use taskId '*' to list all tracked tasks."
        ) {
            if (taskId == "*") {
                val all = ScanTaskRegistry.listAll()
                if (all.isEmpty()) return@mcpTool "No active scan tasks"
                all.map { (id, task) ->
                    Json.encodeToString(ScanTaskStatus(
                        taskId = id,
                        statusMessage = task.statusMessage(),
                        requestCount = task.requestCount(),
                        errorCount = task.errorCount()
                    ))
                }.joinToString("\n\n")
            } else {
                val task = ScanTaskRegistry.get(taskId)
                    ?: return@mcpTool "No scan task found with ID: $taskId"

                val status = ScanTaskStatus(
                    taskId = taskId,
                    statusMessage = task.statusMessage(),
                    requestCount = task.requestCount(),
                    errorCount = task.errorCount()
                )

                val extra = if (task is Audit) {
                    val issues = task.issues()
                    "\nInsertion points: ${task.insertionPointCount()}\nIssues found: ${issues.size}"
                } else ""

                Json.encodeToString(status) + extra
            }
        }

        mcpTool<DeleteScanTask>(
            "Deletes/cancels a running scan task. Use the taskId returned by start_crawl or start_audit."
        ) {
            val task = ScanTaskRegistry.remove(taskId)
                ?: return@mcpTool "No scan task found with ID: $taskId"

            task.delete()
            api.logging().logToOutput("MCP deleted scan task: $taskId")
            "Scan task $taskId has been deleted"
        }

        mcpPaginatedTool<GetAuditIssues>(
            "Gets issues found by a specific audit task. " +
            "Use the taskId returned by start_audit or start_audit_on_requests."
        ) {
            val task = ScanTaskRegistry.get(taskId)
            if (task == null) {
                return@mcpPaginatedTool sequenceOf("No scan task found with ID: $taskId")
            }
            if (task !is Audit) {
                return@mcpPaginatedTool sequenceOf("Task $taskId is not an audit (it may be a crawl)")
            }

            task.issues().asSequence().map { Json.encodeToString(it.toSerializableForm()) }
        }

        mcpTool<GenerateScanReport>(
            "Generates an HTML or XML report for scanner issues. " +
            "Set format to 'HTML' or 'XML'. " +
            "Provide the full file path where the report should be saved. " +
            "Optionally filter by urlPrefix to only include issues for specific URLs."
        ) {
            val reportFormat = when (format.uppercase()) {
                "HTML" -> ReportFormat.HTML
                "XML" -> ReportFormat.XML
                else -> return@mcpTool "Invalid format: use 'HTML' or 'XML'"
            }

            val issues = if (urlPrefix.isNullOrBlank()) {
                api.siteMap().issues()
            } else {
                api.siteMap().issues(SiteMapFilter.prefixFilter(urlPrefix))
            }

            if (issues.isEmpty()) {
                return@mcpTool "No issues found to report"
            }

            val reportPath = Paths.get(path)
            api.scanner().generateReport(issues, reportFormat, reportPath)
            api.logging().logToOutput("MCP generated $format report with ${issues.size} issues at: $path")

            Json.encodeToString(ReportResult(
                path = path,
                format = format.uppercase(),
                issueCount = issues.size
            ))
        }

        // --- Site Map Full Request/Response Tools ---

        mcpPaginatedTool<GetSiteMapRequestResponse>(
            "Returns full HTTP request/response pairs from the site map for a given URL prefix. " +
            "Unlike get_site_map_entries, this includes complete request and response bodies. " +
            "Use urlPrefix to filter (e.g. 'https://example.com/api/'). " +
            "Results are truncated individually to prevent excessive output."
        ) {
            val allowed = runBlocking {
                checkHistoryPermissionOrDeny(HistoryAccessType.SITE_MAP, config, api, "site map request/response")
            }
            if (!allowed) {
                return@mcpPaginatedTool sequenceOf("Site map access denied by Burp Suite")
            }

            val items = if (urlPrefix.isNullOrBlank()) {
                api.siteMap().requestResponses()
            } else {
                api.siteMap().requestResponses(SiteMapFilter.prefixFilter(urlPrefix))
            }

            val seen = mutableSetOf<String>()
            items.asSequence()
                .filter { entry ->
                    val key = "${entry.request()?.method() ?: "?"} ${entry.request()?.url() ?: ""}"
                    seen.add(key)
                }
                .map { truncateIfNeeded(Json.encodeToString(it.toSerializableForm())) }
        }

        mcpTool<GetProxyHistoryItem>(
            "Returns the full HTTP request/response for a specific proxy history item by index (0-based). " +
            "Use get_proxy_http_history first to find the item index, then retrieve full details with this tool."
        ) {
            val allowed = runBlocking {
                checkHistoryPermissionOrDeny(HistoryAccessType.HTTP_HISTORY, config, api, "HTTP history item")
            }
            if (!allowed) {
                return@mcpTool "HTTP history access denied by Burp Suite"
            }

            val history = api.proxy().history()
            if (index < 0 || index >= history.size) {
                return@mcpTool "Index $index out of range. History contains ${history.size} items (0-${history.size - 1})."
            }

            val item = history[index]
            truncateIfNeeded(Json.encodeToString(item.toSerializableForm()))
        }

        mcpTool<HighlightProxyItem>(
            "Sets a highlight color on a proxy history item by index (0-based) for visual triage. " +
            "Valid colors: RED, ORANGE, YELLOW, GREEN, CYAN, BLUE, PINK, MAGENTA, GRAY, NONE. " +
            "Optionally set a note on the item."
        ) {
            val allowed = runBlocking {
                checkHistoryPermissionOrDeny(HistoryAccessType.HTTP_HISTORY, config, api, "HTTP history highlight")
            }
            if (!allowed) {
                return@mcpTool "HTTP history access denied by Burp Suite"
            }

            val history = api.proxy().history()
            if (index < 0 || index >= history.size) {
                return@mcpTool "Index $index out of range. History contains ${history.size} items (0-${history.size - 1})."
            }

            val item = history[index]
            val highlightColor = try {
                burp.api.montoya.core.HighlightColor.valueOf(color.uppercase())
            } catch (_: Exception) {
                return@mcpTool "Invalid color: $color. Use one of: RED, ORANGE, YELLOW, GREEN, CYAN, BLUE, PINK, MAGENTA, GRAY, NONE"
            }

            item.annotations().setHighlightColor(highlightColor)
            if (!note.isNullOrBlank()) {
                item.annotations().setNotes(note)
            }

            api.logging().logToOutput("MCP highlighted proxy item $index as $color")
            "Highlighted item $index as $color" + if (!note.isNullOrBlank()) " with note: $note" else ""
        }

        mcpTool<SearchSiteMap>(
            "Searches the site map for entries matching criteria. " +
            "Filter by URL prefix, HTTP method, status code range, and/or MIME type. " +
            "Returns a compact summary of matching entries."
        ) {
            val allowed = runBlocking {
                checkHistoryPermissionOrDeny(HistoryAccessType.SITE_MAP, config, api, "site map search")
            }
            if (!allowed) {
                return@mcpTool "Site map access denied by Burp Suite"
            }

            val items = if (urlPrefix.isNullOrBlank()) {
                api.siteMap().requestResponses()
            } else {
                api.siteMap().requestResponses(SiteMapFilter.prefixFilter(urlPrefix))
            }

            val results = items.asSequence()
                .filter { entry ->
                    val req = entry.request() ?: return@filter false
                    val resp = entry.response()

                    val methodMatch = method.isNullOrBlank() || req.method().equals(method, ignoreCase = true)
                    val statusMatch = if (minStatus != null || maxStatus != null) {
                        val code = resp?.statusCode()?.toInt() ?: 0
                        (minStatus == null || code >= minStatus) && (maxStatus == null || code <= maxStatus)
                    } else true
                    val mimeMatch = mimeType.isNullOrBlank() ||
                        resp?.statedMimeType()?.name?.contains(mimeType, ignoreCase = true) == true

                    methodMatch && statusMatch && mimeMatch
                }
                .take(maxResults ?: 100)
                .map { Json.encodeToString(it.toSiteMapEntry()) }
                .toList()

            if (results.isEmpty()) {
                "No matching site map entries found"
            } else {
                "Found ${results.size} entries:\n\n" + results.joinToString("\n\n")
            }
        }

        // --- Site Map Write Tools ---

        mcpTool<AddToSiteMap>(
            "Adds an HTTP request/response pair to the Burp site map. " +
            "Provide the raw HTTP request and response strings along with target connection details."
        ) {
            val service = HttpService.httpService(targetHostname, targetPort, usesHttps)
            val fixedRequest = request.replace("\r", "").replace("\n", "\r\n")
            val fixedResponse = response.replace("\r", "").replace("\n", "\r\n")

            val httpRequest = HttpRequest.httpRequest(service, fixedRequest)
            val httpResponse = burp.api.montoya.http.message.responses.HttpResponse.httpResponse(fixedResponse)
            val reqResp = MontoyaHttpRequestResponse.httpRequestResponse(httpRequest, httpResponse)

            api.siteMap().add(reqResp)
            api.logging().logToOutput("MCP added request/response to site map: ${httpRequest.url()}")
            "Added to site map: ${httpRequest.url()}"
        }
    }

    // --- Scope Management Tools (available in all editions) ---

    mcpTool<IncludeInScope>(
        "Adds a URL to Burp's suite-wide target scope. " +
        "The URL should include the protocol (e.g. 'https://example.com')."
    ) {
        val allowed = runBlocking {
            ScannerSecurity.checkScanPermission("Add to scope: $url", config, api)
        }
        if (!allowed) {
            api.logging().logToOutput("MCP scope modification denied")
            return@mcpTool "Scope modification denied by Burp Suite"
        }

        api.scope().includeInScope(url)
        api.logging().logToOutput("MCP added to scope: $url")
        "Added to scope: $url"
    }

    mcpTool<ExcludeFromScope>(
        "Removes a URL from Burp's suite-wide target scope."
    ) {
        val allowed = runBlocking {
            ScannerSecurity.checkScanPermission("Remove from scope: $url", config, api)
        }
        if (!allowed) {
            api.logging().logToOutput("MCP scope modification denied")
            return@mcpTool "Scope modification denied by Burp Suite"
        }

        api.scope().excludeFromScope(url)
        api.logging().logToOutput("MCP removed from scope: $url")
        "Removed from scope: $url"
    }

    mcpTool<IsInScope>(
        "Checks whether a URL is within Burp's current suite-wide target scope."
    ) {
        val inScope = api.scope().isInScope(url)
        Json.encodeToString(ScopeCheckResult(url = url, inScope = inScope))
    }

    // --- Tool Integration: Comparer, Decoder, Organizer ---

    mcpTool<SendToComparer>(
        "Sends data to Burp's Comparer tool for comparison with other data."
    ) {
        val byteArray = burp.api.montoya.core.ByteArray.byteArray(data)
        api.comparer().sendToComparer(byteArray)
        api.logging().logToOutput("MCP sent ${data.length} chars to Comparer")
        "Sent ${data.length} chars to Comparer"
    }

    mcpTool<SendToDecoder>(
        "Sends data to Burp's Decoder tool for encoding/decoding analysis."
    ) {
        val byteArray = burp.api.montoya.core.ByteArray.byteArray(data)
        api.decoder().sendToDecoder(byteArray)
        api.logging().logToOutput("MCP sent ${data.length} chars to Decoder")
        "Sent ${data.length} chars to Decoder"
    }

    mcpTool<SendToOrganizer>(
        "Sends an HTTP request/response pair to Burp's Organizer tool for review. " +
        "Provide raw HTTP request and response strings with target connection details."
    ) {
        val service = HttpService.httpService(targetHostname, targetPort, usesHttps)
        val fixedRequest = request.replace("\r", "").replace("\n", "\r\n")
        val fixedResponse = response.replace("\r", "").replace("\n", "\r\n")

        val httpRequest = HttpRequest.httpRequest(service, fixedRequest)
        val httpResponse = burp.api.montoya.http.message.responses.HttpResponse.httpResponse(fixedResponse)
        val reqResp = MontoyaHttpRequestResponse.httpRequestResponse(httpRequest, httpResponse)

        api.organizer().sendToOrganizer(reqResp)
        api.logging().logToOutput("MCP sent request/response to Organizer: ${httpRequest.url()}")
        "Sent to Organizer: ${httpRequest.url()}"
    }

    // --- Utility Tools ---

    mcpTool("get_burp_version", "Returns the Burp Suite edition and version information") {
        val version = api.burpSuite().version()
        Json.encodeToString(BurpVersionInfo(
            edition = version.edition().name,
            name = version.name(),
            version = version.toString(),
            buildNumber = version.buildNumber()
        ))
    }

    mcpTool<HtmlEncode>("HTML-encodes the input string, converting special characters to HTML entities") {
        api.utilities().htmlUtils().encode(content)
    }

    mcpTool<HtmlDecode>("HTML-decodes the input string, converting HTML entities back to characters") {
        api.utilities().htmlUtils().decode(content)
    }

    // --- Authenticated Crawl Tools ---

    mcpTool<StartAuthenticatedCrawl>(
        "Starts an authenticated BFS crawl from seed URLs. Provide cookies as a header string " +
        "(e.g. 'sessionid=abc123; csrftoken=xyz'). The crawler follows links, respects scope, " +
        "and adds all responses to Burp's site map. Returns a task ID for tracking with get_crawl_status. " +
        "Optionally pass extraPathPatterns as a list of regex strings to extract additional " +
        "URLs from responses (e.g. [\"/custom-api/[^\\\"']+\"] to match app-specific endpoints)."
    ) {
        val allowed = runBlocking {
            ScannerSecurity.checkScanPermission(
                "Start authenticated crawl on: ${seedUrls.joinToString(", ")} " +
                "(maxDepth=$maxDepth, maxPages=$maxPages)",
                config, api
            )
        }
        if (!allowed) {
            api.logging().logToOutput("MCP authenticated crawl denied")
            return@mcpTool "Scanner operation denied by Burp Suite"
        }

        val headers = extraHeaders ?: emptyMap()
        val crawler = AuthenticatedCrawler(
            api = api,
            seedUrls = seedUrls,
            cookies = cookies,
            extraHeaders = headers,
            maxDepth = maxDepth ?: 10,
            maxPages = maxPages ?: 500,
            delayMs = delayMs ?: 100,
            extraPathPatterns = extraPathPatterns ?: emptyList()
        )

        val taskId = CrawlTaskRegistry.register(crawler)
        api.logging().logToOutput("MCP starting authenticated crawl [$taskId]: ${seedUrls.joinToString(", ")}")
        crawler.start()

        Json.encodeToString(CrawlStatus(
            taskId = taskId,
            status = crawler.status.get(),
            pagesVisited = 0,
            pagesQueued = crawler.pagesQueued(),
            urlsDiscovered = crawler.urlsDiscovered(),
            currentDepth = 0,
            errors = 0,
            elapsedMs = 0
        ))
    }

    mcpTool<GetCrawlStatus>(
        "Gets the status of a running authenticated crawl task. " +
        "Use the taskId returned by authenticated_crawl."
    ) {
        val crawler = CrawlTaskRegistry.get(taskId)
            ?: return@mcpTool "No crawl task found with ID: $taskId"

        Json.encodeToString(CrawlStatus(
            taskId = taskId,
            status = crawler.status.get(),
            pagesVisited = crawler.pagesVisited.get(),
            pagesQueued = crawler.pagesQueued(),
            urlsDiscovered = crawler.urlsDiscovered(),
            currentDepth = crawler.currentDepth.get(),
            errors = crawler.errors.get(),
            elapsedMs = crawler.elapsedMs()
        ))
    }

    mcpTool<StopCrawl>(
        "Stops a running authenticated crawl task."
    ) {
        val crawler = CrawlTaskRegistry.get(taskId)
            ?: return@mcpTool "No crawl task found with ID: $taskId"

        crawler.cancel()
        api.logging().logToOutput("MCP cancelled crawl task: $taskId")
        "Crawl task $taskId cancelled. Pages visited: ${crawler.pagesVisited.get()}, URLs discovered: ${crawler.urlsDiscovered()}"
    }

    // --- Cookie Jar Tools ---

    mcpTool<SetCookieJar>(
        "Injects a cookie into Burp's cookie jar. The scanner and crawler will use these cookies. " +
        "Set expirationMinutes to 0 or omit for a session cookie."
    ) {
        val expiration = if (expirationMinutes != null && expirationMinutes > 0) {
            ZonedDateTime.now().plusMinutes(expirationMinutes.toLong())
        } else {
            null
        }

        api.http().cookieJar().setCookie(name, value, path ?: "/", domain, expiration)
        api.logging().logToOutput("MCP set cookie: $name=$value for $domain${ path ?: "/" }")
        "Cookie set: $name for domain $domain"
    }

    mcpTool<GetCookieJar>(
        "Lists cookies from Burp's cookie jar. Optionally filter by domain."
    ) {
        val allCookies = api.http().cookieJar().cookies()
        val filtered = if (domain.isNullOrBlank()) {
            allCookies
        } else {
            allCookies.filter { it.domain().contains(domain) }
        }

        if (filtered.isEmpty()) {
            "No cookies found" + if (!domain.isNullOrBlank()) " for domain: $domain" else ""
        } else {
            filtered.joinToString("\n") { cookie ->
                "${cookie.name()}=${cookie.value()} | domain=${cookie.domain()} | path=${cookie.path()} | " +
                "expiration=${cookie.expiration()?.toString() ?: "session"}"
            }
        }
    }

    mcpPaginatedTool<GetProxyHttpHistory>("Displays items within the proxy HTTP history") {
        val allowed = runBlocking {
            checkHistoryPermissionOrDeny(HistoryAccessType.HTTP_HISTORY, config, api, "HTTP history")
        }
        if (!allowed) {
            return@mcpPaginatedTool sequenceOf("HTTP history access denied by Burp Suite")
        }

        api.proxy().history().asSequence().map { truncateIfNeeded(Json.encodeToString(it.toSerializableForm())) }
    }

    mcpPaginatedTool<GetProxyHttpHistoryRegex>("Displays items matching a specified regex within the proxy HTTP history") {
        val allowed = runBlocking {
            checkHistoryPermissionOrDeny(HistoryAccessType.HTTP_HISTORY, config, api, "HTTP history")
        }
        if (!allowed) {
            return@mcpPaginatedTool sequenceOf("HTTP history access denied by Burp Suite")
        }

        val compiledRegex = try {
            Pattern.compile(regex)
        } catch (e: java.util.regex.PatternSyntaxException) {
            return@mcpPaginatedTool sequenceOf("Invalid regex: ${e.message}")
        }
        api.proxy().history { it.contains(compiledRegex) }.asSequence()
            .map { truncateIfNeeded(Json.encodeToString(it.toSerializableForm())) }
    }

    mcpPaginatedTool<GetProxyWebsocketHistory>("Displays items within the proxy WebSocket history") {
        val allowed = runBlocking {
            checkHistoryPermissionOrDeny(HistoryAccessType.WEBSOCKET_HISTORY, config, api, "WebSocket history")
        }
        if (!allowed) {
            return@mcpPaginatedTool sequenceOf("WebSocket history access denied by Burp Suite")
        }

        api.proxy().webSocketHistory().asSequence()
            .map { truncateIfNeeded(Json.encodeToString(it.toSerializableForm())) }
    }

    mcpPaginatedTool<GetProxyWebsocketHistoryRegex>("Displays items matching a specified regex within the proxy WebSocket history") {
        val allowed = runBlocking {
            checkHistoryPermissionOrDeny(HistoryAccessType.WEBSOCKET_HISTORY, config, api, "WebSocket history")
        }
        if (!allowed) {
            return@mcpPaginatedTool sequenceOf("WebSocket history access denied by Burp Suite")
        }

        val compiledRegex = try {
            Pattern.compile(regex)
        } catch (e: java.util.regex.PatternSyntaxException) {
            return@mcpPaginatedTool sequenceOf("Invalid regex: ${e.message}")
        }
        api.proxy().webSocketHistory { it.contains(compiledRegex) }.asSequence()
            .map { truncateIfNeeded(Json.encodeToString(it.toSerializableForm())) }
    }

    mcpTool<SetTaskExecutionEngineState>("Sets the state of Burp's task execution engine (paused or unpaused)") {
        api.burpSuite().taskExecutionEngine().state = if (running) RUNNING else PAUSED

        "Task execution engine is now ${if (running) "running" else "paused"}"
    }

    mcpTool<SetProxyInterceptState>("Enables or disables Burp Proxy Intercept") {
        if (intercepting) {
            api.proxy().enableIntercept()
        } else {
            api.proxy().disableIntercept()
        }

        "Intercept has been ${if (intercepting) "enabled" else "disabled"}"
    }

    mcpTool("get_active_editor_contents", "Outputs the contents of the user's active message editor") {
        getActiveEditor(api)?.text ?: "<No active editor>"
    }

    mcpTool<SetActiveEditorContents>("Sets the content of the user's active message editor") {
        val editor = getActiveEditor(api) ?: return@mcpTool "<No active editor>"

        if (!editor.isEditable) {
            return@mcpTool "<Current editor is not editable>"
        }

        editor.text = text

        "Editor text has been set"
    }
}

fun getActiveEditor(api: MontoyaApi): JTextArea? {
    val frame = api.userInterface().swingUtils().suiteFrame()

    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    val permanentFocusOwner = focusManager.permanentFocusOwner

    val isInBurpWindow = generateSequence(permanentFocusOwner) { it.parent }.any { it == frame }

    return if (isInBurpWindow && permanentFocusOwner is JTextArea) {
        permanentFocusOwner
    } else {
        null
    }
}

interface HttpServiceParams {
    val targetHostname: String
    val targetPort: Int
    val usesHttps: Boolean

    fun toMontoyaService(): HttpService = HttpService.httpService(targetHostname, targetPort, usesHttps)
}

@Serializable
data class SendHttp1Request(
    val content: String,
    override val targetHostname: String,
    override val targetPort: Int,
    override val usesHttps: Boolean
) : HttpServiceParams

@Serializable
data class SendHttp2Request(
    val pseudoHeaders: Map<String, String>,
    val headers: Map<String, String>,
    val requestBody: String,
    override val targetHostname: String,
    override val targetPort: Int,
    override val usesHttps: Boolean
) : HttpServiceParams

@Serializable
data class CreateRepeaterTab(
    val tabName: String?,
    val content: String,
    override val targetHostname: String,
    override val targetPort: Int,
    override val usesHttps: Boolean
) : HttpServiceParams

@Serializable
data class SendToIntruder(
    val tabName: String?,
    val content: String,
    override val targetHostname: String,
    override val targetPort: Int,
    override val usesHttps: Boolean
) : HttpServiceParams

@Serializable
data class UrlEncode(val content: String)

@Serializable
data class UrlDecode(val content: String)

@Serializable
data class Base64Encode(val content: String)

@Serializable
data class Base64Decode(val content: String)

@Serializable
data class GenerateRandomString(val length: Int, val characterSet: String)

@Serializable
data class SetProjectOptions(val json: String)

@Serializable
data class SetUserOptions(val json: String)

@Serializable
data class SetTaskExecutionEngineState(val running: Boolean)

@Serializable
data class SetProxyInterceptState(val intercepting: Boolean)

@Serializable
data class SetActiveEditorContents(val text: String)

@Serializable
data class GetScannerIssues(override val count: Int, override val offset: Int) : Paginated

@Serializable
data class GetProxyHttpHistory(override val count: Int, override val offset: Int) : Paginated

@Serializable
data class GetProxyHttpHistoryRegex(val regex: String, override val count: Int, override val offset: Int) : Paginated

@Serializable
data class GetProxyWebsocketHistory(override val count: Int, override val offset: Int) : Paginated

@Serializable
data class GetProxyWebsocketHistoryRegex(val regex: String, override val count: Int, override val offset: Int) :
    Paginated

@Serializable
data class GenerateCollaboratorPayload(
    val customData: String? = null
)

@Serializable
data class GetCollaboratorInteractions(
    val payloadId: String? = null
)

@Serializable
data class GetSiteMapUrls(
    val urlPrefix: String? = null,
    val includeQueryString: Boolean? = false,
    override val count: Int,
    override val offset: Int
) : Paginated

@Serializable
data class GetSiteMapEntries(
    val urlPrefix: String? = null,
    override val count: Int,
    override val offset: Int
) : Paginated

@Serializable
data class GetSiteMapIssueSummary(
    val urlPrefix: String? = null,
    override val count: Int,
    override val offset: Int
) : Paginated

// --- Scanner Control Data Classes ---

@Serializable
data class StartCrawl(val seedUrls: List<String>)

@Serializable
data class StartAudit(val configurationType: String)

@Serializable
data class StartAuditOnRequests(
    val configurationType: String,
    val requests: List<String>,
    val targetHostname: String,
    val targetPort: Int,
    val usesHttps: Boolean
)

@Serializable
data class GetScanStatus(val taskId: String)

@Serializable
data class DeleteScanTask(val taskId: String)

@Serializable
data class GetAuditIssues(
    val taskId: String,
    override val count: Int,
    override val offset: Int
) : Paginated

@Serializable
data class GenerateScanReport(
    val format: String,
    val path: String,
    val urlPrefix: String? = null
)

// --- Scope Data Classes ---

@Serializable
data class IncludeInScope(val url: String)

@Serializable
data class ExcludeFromScope(val url: String)

@Serializable
data class IsInScope(val url: String)

// --- Site Map Write Data Classes ---

@Serializable
data class AddToSiteMap(
    val request: String,
    val response: String,
    val targetHostname: String,
    val targetPort: Int,
    val usesHttps: Boolean
)

// --- Tool Integration Data Classes ---

@Serializable
data class SendToComparer(val data: String)

@Serializable
data class SendToDecoder(val data: String)

@Serializable
data class SendToOrganizer(
    val request: String,
    val response: String,
    val targetHostname: String,
    val targetPort: Int,
    val usesHttps: Boolean
)

// --- Authenticated Crawl Data Classes ---

@Serializable
data class StartAuthenticatedCrawl(
    val seedUrls: List<String>,
    val cookies: String,
    val extraHeaders: Map<String, String>? = null,
    val maxDepth: Int? = 10,
    val maxPages: Int? = 500,
    val delayMs: Long? = 100,
    val extraPathPatterns: List<String>? = null
)

@Serializable
data class GetCrawlStatus(val taskId: String)

@Serializable
data class StopCrawl(val taskId: String)

// --- Cookie Jar Data Classes ---

@Serializable
data class SetCookieJar(
    val name: String,
    val value: String,
    val domain: String,
    val path: String? = "/",
    val expirationMinutes: Int? = null
)

@Serializable
data class GetCookieJar(
    val domain: String? = null
)

// --- Site Map Request/Response Data Classes ---

@Serializable
data class GetSiteMapRequestResponse(
    val urlPrefix: String? = null,
    override val count: Int,
    override val offset: Int
) : Paginated

@Serializable
data class GetProxyHistoryItem(
    val index: Int
)

@Serializable
data class HighlightProxyItem(
    val index: Int,
    val color: String,
    val note: String? = null
)

@Serializable
data class SearchSiteMap(
    val urlPrefix: String? = null,
    val method: String? = null,
    val minStatus: Int? = null,
    val maxStatus: Int? = null,
    val mimeType: String? = null,
    val maxResults: Int? = 100
)

// --- Utility Data Classes ---

@Serializable
data class HtmlEncode(val content: String)

@Serializable
data class HtmlDecode(val content: String)